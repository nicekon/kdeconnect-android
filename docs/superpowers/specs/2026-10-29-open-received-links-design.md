# Open received links directly

The user wants a forked Android KDE Connect APK that opens a link sent by Soduto without tapping a notification. Use the existing share protocol and Android ACTION_VIEW resolution, preserving the exact URL so Android can choose Notion or another registered app.

Add an app-wide sharing preference `share_open_links_directly`, disabled by default. Offer a settings entry to the system overlay permission screen. Only received URL shares may request immediate background launch, and only when the preference is enabled and Settings.canDrawOverlays returns true. Other activity launches retain current behavior. Missing permission, SecurityException or ActivityNotFoundException during direct launch must retain the existing tap-to-open notification. A normally returning launch request must keep a quiet ordinary notification for recovery, since Android can silently reject background starts; no notification tap is required when the app actually opens. Do not bypass lockscreen or platform restrictions.

Approaches considered: changing all IntentHelper callers would affect unrelated plugins; using ADB would require a separate connection. The scoped share preference preserves the existing paired KDE Connect connection and limits the behavior to URL shares.

Build the existing debug variant alongside the official app, named KDE Connect Kon, without changing minSdk/targetSdk. Produce a signed APK, source branch and concise setup instructions. Run tests for direct launch, opt-out, missing/revoked permission, foreground behavior, unchanged callers and exact URL preservation, then the full unit suite and APK verification. Hardware behavior remains to be tested on the user's phone.

The user explicitly authorized the fork, project setup, implementation and APK in the conversation. Codex UI project registration requires the user's action because the computer-use tool rejects controlling com.openai.codex.
