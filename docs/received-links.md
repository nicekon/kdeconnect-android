# KDE Connect Kon: open received links

This fork adds an opt-in for immediately opening URLs received through KDE Connect sharing, including links sent by Soduto's **Open Clipboard Link** menu. Android chooses the installed application that handles each URL. A web link only opens Notion directly when Android associates that link with Notion.

## Install and enable

1. Install the private release APK. It uses `org.kde.kdeconnect_tp.kon`, so the official KDE Connect installation can remain installed. The personal fork is named **KDE Connect Kon**. It is separate from the earlier debug package and requires its own pairing.
2. Open KDE Connect Kon and pair it with the Mac. It has separate app data and needs its own pairing and permissions.
3. In the sharing plugin settings, enable **Open received links immediately**.
4. On Android 10 and newer, select **Allow opening links in the background** and explicitly allow **Display over other apps** in Android settings.
5. Unlock the phone, put KDE Connect Kon in the background, copy a Notion URL on the Mac, then choose the phone's **Open Clipboard Link** menu in Soduto.

After requesting an immediate launch, a silent notification retains the URL for recovery because Android may reject a background launch without returning an error. A successfully opened app does not require tapping this notification. Without the opt-in or required permission, the existing tap-to-open notification remains. Ordinary links retain the previous foreground and Android 9 behavior. Received `kdeconnect:` links always require a notification tap, so a link cannot silently trigger this app’s remote-command handler. Android and manufacturer policies may still restrict launches; this does not bypass the lockscreen.

## Build and verify

Use JDK 21 and Android SDK platform 37 (matching upstream). Configure `local.properties` with your SDK location, then run:

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease \
  -PkonSigningPropertiesFile="/absolute/private/release-signing.properties"
```

The APK is written to `build/outputs/apk/release/kdeconnect-android-release.apk`. The caller-selected private properties file must define `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Keep it and the keystore outside Git; release signing fails if no file is supplied. Back up both securely and keep using the same key for future updates. Debug builds still use the public upstream debug key and must not be distributed as release builds. Do not commit local SDK paths, signing secrets or build outputs.

Retained permissions and the bounded security review are documented in [private-release-audit.md](private-release-audit.md). A private signature identifies this build; it does not certify that a phone security service will accept it.

Source: https://github.com/nicekon/kdeconnect-android/tree/codex/release-audit

## Previous debug verification (2026-10-29)

- Full JVM unit suite: 141 tests, 0 failures/errors, including 10 received-link behavior tests.
- Debug APK build: successful; APK Signature Scheme v1 and v2 verified.
- Package: `org.kde.kdeconnect_tp.debug`; version `1.37.0-debug`; minSdk 23; targetSdk 37.
- Independent review findings addressed: preserve a quiet recovery notification when Android silently ignores a launch; preserve links when no activity handles a deep link.
- Actual Notion/OEM behavior on the intended phone remains unverified. The USB-connected Android 9 LG V30 did not have KDE Connect or Notion installed and was not used as evidence for the intended phone.
