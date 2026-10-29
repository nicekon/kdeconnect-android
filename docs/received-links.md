# KDE Connect Kon: open received links

This fork adds an opt-in for immediately opening URLs received through KDE Connect sharing, including links sent by Soduto's **Open Clipboard Link** menu. Android chooses the installed application that handles each URL. A web link only opens Notion directly when Android associates that link with Notion.

## Install and enable

1. Install the debug APK. It uses `org.kde.kdeconnect_tp.debug`, so the official KDE Connect installation can remain installed. The test app is named **KDE Connect Kon**.
2. Open KDE Connect Kon and pair it with the Mac. It has separate app data and needs its own pairing and permissions.
3. In the sharing plugin settings, enable **Open received links immediately**.
4. On Android 10 and newer, select **Allow opening links in the background** and explicitly allow **Display over other apps** in Android settings.
5. Unlock the phone, put KDE Connect Kon in the background, copy a Notion URL on the Mac, then choose the phone's **Open Clipboard Link** menu in Soduto.

Without the opt-in or required permission, the existing tap-to-open notification remains. Foreground behavior and unrelated plugins are unchanged. Android and manufacturer policies may still restrict launches; this does not bypass the lockscreen. Android 9 and older retain the existing direct-launch behavior.

## Build and verify

Use JDK 21 and Android SDK platform 37 (matching upstream). Configure `local.properties` with your SDK location, then run:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The APK is written to `build/outputs/apk/debug/kdeconnect-android-debug.apk`. It uses the upstream debug keystore and is for local testing, not store publication. Do not commit local SDK paths, private release signing keys or build outputs.

Source: https://github.com/nicekon/kdeconnect-android/tree/codex/open-received-links
