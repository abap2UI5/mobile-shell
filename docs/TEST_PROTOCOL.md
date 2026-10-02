# Device test protocol

Fill-in sheet for the runbook in [TESTING.md](TESTING.md) — one copy per
test session. Mark each row ✅ / ❌ / — (not tested) and note what happened
on a ❌; a failing row becomes an issue or a fix, not a workaround.

## Setup

| | Value |
|---|---|
| Date / tester | |
| Backend (host, client, release) | |
| abap2UI5 commit / version | |
| mobile-shell commit | |
| Android device, OS, WebView version (`adb logcat -s WebViewVersion`) | |
| iOS device or simulator, iOS version | |
| Shell build (CI artifact / local) | |

## 0. Preparation

| Check | Result | Notes |
|---|---|---|
| Desktop browser: sample opens, scan button visible, press shows "native shell not available" | | |
| Device reaches the host over HTTPS without a certificate warning | | |

## 1. Bridge smoke test

| Check | Android | iOS | Notes |
|---|---|---|---|
| Sample opens inside the shell after entering the endpoint | | | |
| Scan button visible on the **first** screen (ready event, no reload needed) | | | |
| Scan → value in the input field + "Scanned: …" toast | | device only | |
| Scan cancelled → "Scan failed: cancelled", app stays usable | | device only | |
| Console: `getDeviceInfo()` | | | |
| Console: `showToast("hello")` | | | |
| Console: `biometricConfirm("test")` — true on success, false on cancel | | | |
| Console: `getPushToken()` rejects `unavailable` | | | |

**Phase 0 exit criterion — barcode scanned in the Android shell arrives in
the ABAP app against a real backend:** ☐

## 2. Session handling

| Check | Android | iOS | Notes |
|---|---|---|---|
| Background > interval → SPA reloads, no dead draft | | | |
| After real server session expiry → auth redirect, usable app | | | |

## 3. QR onboarding

| Check | Android | iOS | Notes |
|---|---|---|---|
| Plain URL QR → endpoint stored, survives restart | | | |
| JSON QR → endpoint + MS coordinates stored | | | |
| Wi-Fi QR / truncated JSON / JSON without `url` → rejected, shell keeps running | | | |

## 4. Managed configuration

| Check | Android | iOS | Notes |
|---|---|---|---|
| Managed `endpoint_url` → menu entries gone, URL loaded | | | |
| Managed `app_lock_enabled` → toggle gone, prompt at start | | | |
| … without enrolled credential → start refused with admin message | | | |
| Managed `screenshot_protection` → Android: screenshot fails / iOS: switcher covered | | | |
| Restrictions cleared → user control back | | | |

## 5. TLS policy

| Check | Android | iOS | Notes |
|---|---|---|---|
| `http://` endpoint refused with the cleartext message | | | |
| Debug build works behind an inspecting proxy with user CA | | — | |

## 6. WebView floor

| Check | Result | Notes |
|---|---|---|
| Provider/version logged at start | | |
| Warning below `MINIMUM_MAJOR` (only if such a device is at hand) | | |

## 7. CSP

| Check | Android | iOS | Notes |
|---|---|---|---|
| Bridge works with the hardened UI5 CSP settings on | | | |

## Findings

| # | Section | What happened | Expected | Follow-up |
|---|---|---|---|---|
| 1 | | | | |
