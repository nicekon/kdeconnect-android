# Private Signed Release Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task by task.

**Goal:** Produce an audited personal release APK signed with a private key.
**Architecture:** Preserve KDE Connect features, harden external request boundaries, and add opt-in local release signing configuration. APK artifact checks validate the actual merged manifest and signer.
**Tech Stack:** Kotlin, Android, Robolectric/JUnit, Gradle, keytool/apksigner, Python.
**Spec:** docs/superpowers/specs/2026-10-29-private-release-design.md

## Global Constraints
- Preserve existing features and required permissions.
- Keys/passwords remain outside Git; private directory 0700, private files 0600.
- No fallback from release to the public debug key.
- Keep API targets and networking trust gates intact; do not bypass the phone's protection.

## Review Focus
- File shares must not use app-private file URIs or ungranted provider access.
- Normal externally granted content shares and internal file picking must keep working.
- Widgets must work using app-created PendingIntents while rejecting arbitrary external broadcasts.
- KDE Connect command URLs need explicit interaction; normal Notion/web URLs keep direct launch.
- Personal release identity must not break FileProvider authorities or expose signing secrets.

### Task 1: Security audit and corrections
- [x] Record code/dependency/permission review and limitations.
- [x] Add failing authorization tests, implement URI and action-scheme guards, and verify.
- [x] Restrict command widget receiver and repair fork FileProvider authority.

### Task 2: Private release build
- [x] Add private signing properties support and personal release identity/label.
- [x] Generate a dedicated key outside Git without exposing secrets.
- [x] Run full unit suite, lint and assembleRelease.

### Task 3: Artifact validation and handoff
- [x] Validate the actual APK manifest, signer and signature; copy APK/checksum/public certificate.
- [x] Record exact retained permissions, completed fixes and remaining review limits.
- [x] Commit and push source branch, excluding private material; deliver APK and key backup guidance.

Validation: full debug JVM suite 156/156; signed release assembled; APK manifest/signature/permissions/alignment checked. Lint completed with documented residual diagnostics, not a clean result. See `docs/private-release-audit.md`.
