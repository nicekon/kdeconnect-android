package org.kde.kdeconnect.plugins.share

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.res.Resources
import androidx.preference.PreferenceManager
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.IntentHelper
import org.kde.kdeconnect.helpers.LifecycleHelper
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect_tp.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SharePluginTest {
    private lateinit var application: Application
    private lateinit var context: Context
    private lateinit var plugin: SharePlugin
    private lateinit var notifications: NotificationManager
    private val url = "https://www.notion.so/example/Page-0123456789abcdef?view=mobile#section"

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        // This project's JVM tests do not package Android app resources.
        // Supply only the notification label; keep Android launch/notification behavior real.
        val testResources = spyk(application.resources)
        every { testResources.getString(R.string.tap_to_open) } returns "Tap to open"
        context = object : ContextWrapper(application) {
            override fun getResources(): Resources = testResources
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(NotificationHelper.Channels.HIGHPRIORITY, "Links", NotificationManager.IMPORTANCE_HIGH))
        notifications.createNotificationChannel(NotificationChannel(NotificationHelper.Channels.DEFAULT, "Received links", NotificationManager.IMPORTANCE_DEFAULT))
        mockkObject(LifecycleHelper)
        every { LifecycleHelper.isInForeground } returns false
        ShadowSettings.setCanDrawOverlays(false)
        plugin = SharePlugin().apply { setContext(context, mockk<Device>(relaxed = true)) }
    }

    @After
    fun cleanup() {
        plugin.onDestroy()
        unmockkAll()
    }

    private fun enableDirectLinks() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("share_open_links_directly", true).commit()
    }

    private fun receive(link: String = url) {
        assertTrue(plugin.onPacketReceived(NetworkPacket("kdeconnect.share.request").apply { set("url", link) }))
    }

    @Test
    fun enabledWithOverlayPermissionOpensExactUrlAndKeepsQuietRecoveryNotification() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        receive()
        val started = shadowOf(application).nextStartedActivity
        assertNotNull("An opted-in URL share should launch an activity", started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(url, started.dataString)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertQuietRecoveryNotification(url)
    }

    @Test
    fun defaultPreferenceStillNotifiesEvenWithOverlayPermission() {
        ShadowSettings.setCanDrawOverlays(true)
        receive()
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun missingOverlayPermissionStillNotifiesAfterOptIn() {
        enableDirectLinks()
        receive()
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun foregroundShareStillOpensWithoutOverlayPermission() {
        every { LifecycleHelper.isInForeground } returns true
        receive()
        assertEquals(url, shadowOf(application).nextStartedActivity.dataString)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun otherHelperCallersStillNotifyAfterShareOptIn() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        IntentHelper.startActivityFromBackgroundOrCreateNotification(context, Intent(Intent.ACTION_VIEW), "Other action")
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun repeatedAppDeepLinkRequestsEachOpenUnchanged() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        val deepLink = "notion://www.notion.so/example/Page-0123456789abcdef?view=mobile#section"
        receive(deepLink)
        receive(deepLink)
        assertEquals(deepLink, shadowOf(application).nextStartedActivity?.dataString)
        assertEquals(deepLink, shadowOf(application).nextStartedActivity?.dataString)
        assertTrue(shadowOf(notifications).allNotifications.isNotEmpty())
        assertTrue(shadowOf(notifications).allNotifications.all { it.fullScreenIntent == null })
    }

    @Test
    fun revokedOverlayPermissionFallsBackOnTheNextRequest() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        receive()
        assertEquals(url, shadowOf(application).nextStartedActivity?.dataString)
        notifications.cancelAll()
        ShadowSettings.setCanDrawOverlays(false)
        receive()
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
        assertEquals(NotificationHelper.Channels.HIGHPRIORITY, shadowOf(notifications).allNotifications.single().channelId)
    }

    @Test
    fun rejectedDirectLaunchFallsBackToNotification() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        val blockedContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw SecurityException("Background activity launch rejected")
            }
        }
        plugin.setContext(blockedContext, mockk<Device>(relaxed = true))
        receive()
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
    }

    private fun assertQuietRecoveryNotification(link: String) {
        val received = shadowOf(notifications).allNotifications
        assertEquals("The received link must remain available in a notification", 1, received.size)
        val notification = received.single()
        assertEquals(link, notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertNotNull(notification.contentIntent)
        assertNull(notification.fullScreenIntent)
        assertEquals(NotificationHelper.Channels.DEFAULT, notification.channelId)
    }

    @Test
    fun silentlyRejectedDirectLaunchKeepsAQuietRecoveryNotification() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        val silentContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) = Unit
        }
        plugin.setContext(silentContext, mockk<Device>(relaxed = true))
        receive()
        assertNull(shadowOf(application).nextStartedActivity)
        assertQuietRecoveryNotification(url)
    }

    @Test
    fun missingDeepLinkHandlerKeepsTheReceivedLinkInANotification() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        val unhandledContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("No app handles this link")
            }
        }
        plugin.setContext(unhandledContext, mockk<Device>(relaxed = true))
        val deepLink = "notion://www.notion.so/example/Page-0123456789abcdef"
        receive(deepLink)
        assertNull(shadowOf(application).nextStartedActivity)
        val received = shadowOf(notifications).allNotifications
        assertEquals("The received link must remain available in a notification", 1, received.size)
        val notification = received.single()
        assertEquals(deepLink, notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertNotNull(notification.contentIntent)
    }

    private fun assertCommandLinkRequiresTap() {
        enableDirectLinks()
        ShadowSettings.setCanDrawOverlays(true)
        val link = "kdeconnect://runcommand/paired-desktop/command-key"
        receive(link)
        assertNull("A received command must wait for user interaction", shadowOf(application).nextStartedActivity)
        val notification = shadowOf(notifications).allNotifications.single()
        assertNull("A full-screen intent could execute the command without a tap", notification.fullScreenIntent)
        notification.contentIntent.send()
        assertEquals(link, shadowOf(application).nextStartedActivity.dataString)
    }

    @Test
    fun receivedCommandRequiresTapEvenWithDirectLinksEnabled() = assertCommandLinkRequiresTap()

    @Test
    fun receivedCommandRequiresTapEvenInForeground() {
        every { LifecycleHelper.isInForeground } returns true
        assertCommandLinkRequiresTap()
    }

    @Test
    @Config(sdk = [28])
    fun receivedCommandRequiresTapOnAndroid9() = assertCommandLinkRequiresTap()

    private fun rejectNotifications() {
        val manager = mockk<NotificationManagerCompat>()
        every { manager.notify(any<Int>(), any<android.app.Notification>()) } throws SecurityException("Notification permission denied")
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(context) } returns manager
    }

    @Test
    fun deniedNotificationPermissionDoesNotLaunchACommandOrEscapeTheHelper() {
        rejectNotifications()
        ShadowSettings.setCanDrawOverlays(true)
        IntentHelper.startActivityFromBackgroundOrCreateNotification(
            context,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("kdeconnect://runcommand/desktop/key")),
            "Command link",
            allowBackgroundLaunch = true,
            requireUserInteraction = true
        )
        assertNull("Denied notifications must not execute the command instead", shadowOf(application).nextStartedActivity)
    }

    @Test
    fun deniedNotificationPermissionDoesNotLaunchAnOptedOutLinkOrEscapeTheHelper() {
        rejectNotifications()
        IntentHelper.startActivityFromBackgroundOrCreateNotification(
            context,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)),
            url
        )
        assertNull(shadowOf(application).nextStartedActivity)
    }
}
