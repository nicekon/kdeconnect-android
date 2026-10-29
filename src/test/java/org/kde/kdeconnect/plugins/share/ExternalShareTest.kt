package org.kde.kdeconnect.plugins.share

import android.app.Application
import android.content.ClipData
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.FilesHelper
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExternalShareTest {
    private lateinit var application: Application
    private lateinit var plugin: SharePlugin
    private val grants = mutableSetOf<Uri>()
    private val attemptedReads = CopyOnWriteArrayList<Uri>()
    private val sentPackets = CopyOnWriteArrayList<NetworkPacket>()
    private val shared = Uri.parse("content://external.provider/document/shared")
    private val privateDocument = Uri.parse("content://external.provider/document/private")

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        val packageManager = spyk(application.packageManager)
        every { packageManager.resolveContentProvider(any<String>(), any<Int>()) } answers {
            ProviderInfo().apply {
                authority = firstArg()
                packageName = if (authority.contains("own.alias")) application.packageName else "external.provider"
                applicationInfo = ApplicationInfo().apply {
                    uid = if (authority.contains("own.alias")) Process.myUid() else Process.myUid() + 1
                }
            }
        }
        val context = object : ContextWrapper(application) {
            override fun getPackageManager() = packageManager
            override fun checkUriPermission(uri: Uri, pid: Int, uid: Int, modeFlags: Int): Int =
                if (pid == Process.myPid() && uid == Process.myUid() &&
                    modeFlags == Intent.FLAG_GRANT_READ_URI_PERMISSION && uri in grants)
                    PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        val device = mockk<Device>(relaxed = true)
        every { device.sendPacket(any()) } answers { sentPackets += firstArg<NetworkPacket>() }
        plugin = SharePlugin().apply { setContext(context, device) }
        // Replace only the external file-reading boundary; exercise the real share dispatch.
        mockkObject(FilesHelper)
        every { FilesHelper.uriToNetworkPacket(any(), any(), any()) } answers {
            attemptedReads.add(secondArg())
            null
        }
    }

    @After
    fun cleanup() {
        plugin.onDestroy()
        unmockkAll()
    }

    private fun share(uri: Uri, clipUri: Uri = uri) {
        val done = CountDownLatch(1)
        plugin.share(Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("file", clipUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }) { done.countDown() }
        assertTrue("Share dispatch must complete", done.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun externalShareCannotReadAppPrivateFiles() {
        share(Uri.fromFile(java.io.File(application.filesDir, "private.txt")))
        assertTrue("Do not delegate app-private file reads", attemptedReads.isEmpty())
    }

    @Test
    fun externalShareRequiresExplicitReadGrant() {
        share(privateDocument)
        assertTrue("General app provider permissions must not authorize external sharing", attemptedReads.isEmpty())
    }

    @Test
    fun externalShareCannotReadAnOwnProviderAliasEvenWithAGrant() {
        val own = Uri.parse("content://own.alias/private")
        grants.add(own)
        share(own)
        assertTrue(attemptedReads.isEmpty())
    }

    @Test
    fun externalShareCannotReadAnOwnProviderWithUserPrefix() {
        val own = Uri.parse("content://0@own.alias/private")
        grants.add(own)
        share(own)
        assertTrue(attemptedReads.isEmpty())
    }

    @Test
    fun grantedStreamCannotBeSubstitutedForTheClipDataSelection() {
        grants.addAll(listOf(shared, privateDocument))
        share(privateDocument, clipUri = shared)
        assertTrue(attemptedReads.isEmpty())
    }

    @Test
    fun explicitlyGrantedExternalDocumentIsShared() {
        grants.add(shared)
        share(shared)
        assertEquals(listOf(shared), attemptedReads)
    }

    @Test
    fun internalFilePickerRetainsItsFileAccess() {
        val file = Uri.fromFile(java.io.File(application.filesDir, "selected.txt"))
        plugin.sendFiles(listOf(file))
        assertEquals(listOf(file), attemptedReads)
    }

    @Test
    fun invalidFileShareIsNotReinterpretedAsTheAttachedUrl() {
        plugin.share(Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, null as Uri?)
            putExtra(Intent.EXTRA_TEXT, "https://www.notion.so/otherwise-valid")
        })
        assertTrue(attemptedReads.isEmpty())
        assertTrue(sentPackets.isEmpty())
    }

    @Test
    fun oneUngrantedItemRejectsTheWholeBatchBeforeReadingAnyFile() {
        grants.add(shared)
        val done = CountDownLatch(1)
        plugin.share(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(shared, privateDocument))
            clipData = ClipData.newRawUri("file", shared).apply { addItem(ClipData.Item(privateDocument)) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(attemptedReads.isEmpty())
    }

    @Test
    fun ordinaryTextAndWebSharesAreUnchanged() {
        plugin.share(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, "Some plain text") })
        plugin.share(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, "https://www.notion.so/a?b=1#c") })
        assertEquals("Some plain text", sentPackets[0].getString("text"))
        assertEquals("https://www.notion.so/a?b=1#c", sentPackets[1].getString("url"))
    }
}
