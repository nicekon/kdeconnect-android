# Open Received Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a forked KDE Connect Android APK that can open received links without a notification tap.
**Architecture:** SharePlugin reads an app-wide opt-in and passes it to IntentHelper. IntentHelper only attempts background launch with overlay permission; other callers keep the default notification policy. Sharing settings expose the option and system permission page.
**Tech Stack:** Kotlin, Android, Gradle, Robolectric, JUnit.
**Spec:** docs/superpowers/specs/2026-10-29-open-received-links-design.md

## Global Constraints
- Preserve URL contents, protocol, minSdk and targetSdk.
- Preference key `share_open_links_directly`, default false.
- Debug application remains separate from the official app, label KDE Connect Kon.
- Never grant permissions or bypass the lockscreen automatically.

## Review Focus
- Permission revoked after opt-in: fall back to a notification.
- Other plugins: continue to notify even when overlay permission exists.
- Direct launch rejected by Android: preserve notification fallback.
- Notion deep link: preserve scheme, path, query and fragment.
- Repeated send: each explicit request starts the link again.

### Task 1: Scoped link launch and settings
**Files:** SharePlugin.kt, ShareSettingsFragment.kt, helpers/IntentHelper.kt, res/xml/shareplugin_preferences.xml, English/Korean strings, plugins/share/SharePluginTest.kt.
**Interfaces:** existing share URL packet; local preference to helper optional Boolean argument default false.
- [x] Write Robolectric tests against real SharePlugin URL reception and Android shadows.
- [x] Run tests and confirm enabled + granted permission fails under current notification behavior.
- [x] Implement scoped helper opt-in, permission check and SecurityException fallback.
- [x] Add settings toggle and overlay permission shortcut, with clear English/Korean text.
- [x] Run focused tests and full unit suite; record results.
- [x] Commit implementation and tests.

### Task 2: APK and handoff
**Files:** debug app labels, docs/received-links.md, build outputs (not committed).
**Interfaces:** debug variant org.kde.kdeconnect_tp.debug, signed installable APK.
- [x] Rename debug label KDE Connect Kon and write setup/build instructions.
- [x] Build assembleDebug, verify APK signature, application ID and permissions.
- [x] Review whole branch; fix important findings with regression tests.
- [x] Commit and push branch to user's fork; deliver APK and source links.
- [x] Verify Codex project registration or report the one manual step still needed.
