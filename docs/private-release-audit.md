# KDE Connect Kon private release audit — 2026-10-29

## Scope and result

Personal fork based on upstream `1c608ee4753011acbde8aed8a12d97e5f254ebdd`, including the received-link feature at `b86c68b6119f3daf77944d68017e984adf00f86c`. This is a bounded source, permission, dependency-metadata and build-artifact review, not a malware clearance or comprehensive penetration test. The Samsung screenshot does not establish which rule or reputation signal caused the block. No phone protection was disabled and no APK was submitted to an external scanning service.

The release preserves KDE Connect plugins and all 38 declared Android permissions. The merged APK also requests the AndroidX app-defined signature permission for non-exported dynamic receivers (39 uses-permission entries total). Changing the build type or signer does not inherently remove permissions. Runtime and special-access permissions still require Android/user grants.

## Corrections

- External file-share requests must contain explicitly delegated `content://` read access before any file is opened. Private `file://` paths, this app’s providers (including aliases/user prefixes), ungranted streams and streams outside a supplied ClipData selection are rejected. Invalid file requests cannot fall through to attached text/URL extras, including for offline destinations. Normal delegated external documents and internal file picking remain supported.
- The run-command widget receiver is non-exported. App-created PendingIntents and system widget updates retain their intended delivery path; arbitrary third-party explicit broadcasts cannot use that receiver.
- Notification posting catches permission-related `SecurityException` without opening a link as a fallback.
- Received `kdeconnect:` links require a notification tap on all Android versions and in the foreground, with no full-screen intent. This prevents immediate-link opening from automatically entering this app’s remote-command handler. Ordinary web/Notion link behavior is retained.
- Received-file opening derives FileProvider authority from the build application ID. Own-notification filtering includes the current build ID as well as the official/debug app IDs.
- Release signing uses a dedicated private RSA-4096 key. Signing material lives outside Git; the release is not debuggable, keeps backups disabled, and has personal application ID `org.kde.kdeconnect_tp.kon` with label **KDE Connect Kon**. Official KDE Connect may coexist and requires separate pairing/data. Identity/signature changes are not a guarantee of acceptance by security services.

## Retained Android permissions

All names below have the `android.permission.` prefix. Bluetooth legacy permissions retain maxSdkVersion 30.

| Purpose | Permissions retained | Code area / use |
| --- | --- | --- |
| Network, discovery, Bluetooth (11) | INTERNET, ACCESS_LOCAL_NETWORK, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE, BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, BLUETOOTH_SCAN | LAN/Bluetooth backends and discovery |
| Background connection (4) | RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, WAKE_LOCK | Boot/background connection service and transfers |
| SMS, phone, contacts (7) | READ_PHONE_STATE, READ_CALL_LOG, READ_SMS, RECEIVE_SMS, SEND_SMS, RECEIVE_MMS, READ_CONTACTS | SMS/telephony/contact plugins |
| Files (5) | READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE, DOWNLOAD_WITHOUT_NOTIFICATION, REQUEST_INSTALL_PACKAGES | Sharing/storage/SFTP, completed downloads, opening received APK files |
| Audio/camera (2) | RECORD_AUDIO, CAMERA | Mousepad/Bigscreen microphone and Find My Phone flashlight |
| Location (3) | ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION | Trusted Wi-Fi SSID checks and inherited location declarations across Android versions |
| Notifications (3) | POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT, VIBRATE | Incoming events/find-phone/notification behavior; command links specifically omit full-screen intents |
| App visibility/clipboard/background opening (3) | QUERY_ALL_PACKAGES, READ_LOGS, SYSTEM_ALERT_WINDOW | App/plugin queries, optional clipboard logcat workaround, explicitly enabled background link opening |

Notification-listener and accessibility services keep their system binding permissions. FileProvider remains non-exported. The link-opening feature added no new manifest permission. Declaration does not imply that ordinary installations can obtain restricted permissions such as READ_LOGS.

## Source and dependency review limits

Paired-device certificate/TLS and plugin trust gates remain in place; this change does not alter them. Incoming filenames retain basename sanitization. Existing broad external FileProvider paths, cleartext network configuration, SMS/MMS service surface and optional sensitive plugins were reviewed at source level but were not comprehensively penetration-tested. No new external service endpoint was added by this feature.

The resolved release runtime graph contains 130 artifact coordinates. An [OSV batch query](https://google.github.io/osv.dev/post-v1-querybatch/) of their public Maven names/versions returned no matching advisory records on 2026-10-29. This is a metadata lookup, not proof that every dependency is safe or that no reachable vulnerability exists. In particular the inherited SMS/MMS dependency still resolves legacy `com.squareup.okhttp:okhttp:2.5.0` and `okhttp-urlconnection:2.5.0`; changing that stack was outside this targeted release. MINA resolves 2.2.10 and SSHD 2.20.0. The dependency inventory and OSV result accompany the local APK.

## Compatibility and device limits

External legacy `file://` sharing and own-provider URIs returned through another app are now rejected; use a properly granted document or this app’s internal picker. Command-link notifications need notification permission. Physical widget behavior, Android 7–9 received-file opening and Notion/OEM background launching require device verification. The available USB device was an Android 9 LG V30 without KDE Connect/Notion, not the intended Samsung phone.

The private release is separate from the earlier `.debug` build, so open it and pair it with the Mac again. Enable **Open received links immediately** and, on Android 10+, grant **Display over other apps**. Keep the keystore and its properties file backed up securely outside source control for future updates.

## Verification evidence

- Final command: `testDebugUnitTest lintRelease assembleRelease` with the explicitly selected private signing properties; **BUILD SUCCESSFUL**. The project does not provide a `testReleaseUnitTest` task. Release code was compiled/shrunk/packaged; physical release execution remains unverified.
- Full JVM suite (debug variant): **156 tests, zero failures/errors/skips**, 25 suites. Focused security tests were observed failing before their fixes and passing afterward.
- `lintRelease` completed under upstream’s existing `abortOnError=false` configuration; **not a clean lint result**. Remaining diagnostics: 428 Error, 21 Fatal, 194 Warning, 1 Hint. Error/Fatal categories are MissingTranslation (419, including English/Korean-only fork strings), ExtraTranslation (20), NewApi (8), MissingLeanbackLauncher (1), MissingDefaultResource (1). The touched helper’s MissingPermission diagnostic was resolved. Translation completion and broader inherited API/resource compatibility cleanup were not part of this release. Full local report: `build/reports/lint-results-release.html`.
- Missing signing-file property: `validateSigningRelease --rerun-tasks` failed with the intended explicit error. No unsigned/debug-key fallback was produced.
- Actual APK: `KDE-Connect-Kon-1.37.0-kon-release.apk`, package `org.kde.kdeconnect_tp.kon`, version `1.37.0-kon`, minSdk 23, targetSdk 37, label KDE Connect Kon. Signature schemes v1/v2 and ZIP alignment verified. Non-debuggable, backup disabled, widget/FileProvider non-exported, and sensitive bind-service permissions verified from the packaged manifest. Signing files are absent from the APK.
- All 39 merged permission requests (including constraints and the normalized personal AndroidX receiver permission) match the preceding debug artifact. No plugin permission was added or removed.
- APK SHA-256: `09e9feb94f6469ab8d1ddf136c1b5f4f6fdc7b4a83baaec4ed391808033fdea6`.
- Dedicated certificate SHA-256: `816821949313c656ea4bd7d4a4e2e28f6807d8b16948f6636f944f2a43ea954a` (distinct from the tracked public debug key).
- Private signing directory/file modes checked as 0700/0600. Dedicated key/password material was checked against tracked files without printing it; ignored key/property patterns also verified. Public certificate, checksum, artifact verification JSON, resolved runtime inventory and OSV lookup result are saved alongside the APK in `build/artifacts/`.
- Source diff whitespace check passed; independent source/configuration review findings were addressed.
