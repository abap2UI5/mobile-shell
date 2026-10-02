# Next steps — detailed work plan

Status assessment as of 2026-08-20. Complements [PLAN.md](../PLAN.md): the
plan describes the phases, this file lists **what is actually still open**,
in execution order, with dependencies and acceptance criteria.

## Where the PoC stands

* Phases 0–4 are implemented in PoC scope (see status table in PLAN.md).
* No open GitHub issues or pull requests.
* CI builds both shells and tests the bridge contract. The iOS build is
  green **for the first time** — before task A1 it never reached the
  compiler at all.
* Everything achievable without an SAP/Google/Apple account has been done —
  see *Recently completed*. What is left needs real devices, external
  accounts, or a maintainer decision.
* `NativeBridgeScan` is in the framework as `z2ui5.cc.NativeBridgeScan`
  (abap2UI5, D1); the view-builder snippet was dropped (D2) and the
  `frontend-integration/` staging folder is gone.
* One open code TODO: `android/.../MobileServicesPush.kt` — the push
  registration call needs the Phase-1 Mobile Services session (returns 401
  unauthenticated until then).

### Recently completed

| Task | Outcome |
|------|---------|
| A1 | `build_ios` green. Two causes, not one: XcodeGen writes Xcode project format 77, which the Xcode 15.4 of the `macos-14` image cannot read (→ `macos-15`), and once the compiler was reachable, `BarcodeScanner.swift` failed on the main-actor isolation of `DataScannerViewController`. |
| E1 (partly) | TLS policy enforced on both platforms: Android `network_security_config` (no cleartext, system trust anchors, user CAs in debug builds only) with a commented pinning template, iOS `NSAllowsArbitraryLoads: false`. An `http://` endpoint is refused with an explanation instead of a blank WebView. The **pinning decision itself is still open** (E1 below). |
| E3 | `app_lock_enabled` manageable by EMM on both platforms; the user toggle disappears and an enforced lock fails closed where no credential is enrolled. |
| E4 | The Android shell logs the WebView provider/version at start and warns below a documented floor (`WebViewVersion.MINIMUM_MAJOR`). |
| E5 | `screenshot_protection` manageable by EMM: Android `FLAG_SECURE`, iOS content cover while not in the foreground (documented limitation — iOS cannot block screenshots). |
| A2/A3 (prep) | [`TESTING.md`](TESTING.md) — device runbook for bridge smoke test, session expiry, QR onboarding, managed config (TestDPC / `simctl` recipes, no EMM needed), TLS policy, WebView floor. Executing it still needs hardware. |
| Test layer | Bridge shim contract tests (both transports, out-of-order and duplicate settling, teardown mid-call, double injection) on Node with the shells mocked, plus JVM unit tests for QR payload and version parsing. Both gated in CI. |

The remaining work falls into five blocks. Block A needs no external
accounts, only a device and a reachable backend; B–E have external
prerequisites or a decision gate.

---

## Block A — immediate, no external accounts (unblocks everything else)

### A1. Fix the red iOS CI build — **done**

The red build had two independent causes, the second only visible once the
first was out of the way:

1. Unpinned `brew install xcodegen` writes Xcode project format 77, which
   the Xcode 15.4 of the `macos-14` image cannot open
   (*"in a future Xcode project file format (77)"*). Fixed by moving to
   `macos-15`; the workflow now echoes the `xcodebuild`/`xcodegen` versions
   so the same drift is diagnosable from the log alone if the runner image
   ever falls behind XcodeGen again.
2. With the project readable, `xcodebuild` reached the compiler for the
   first time and rejected `BarcodeScanner.swift`:
   `DataScannerViewController` is `@MainActor`, so reading
   `isSupported`/`isAvailable` from the nonisolated enum is an error. Fixed
   by isolating the enum to the main actor — every caller is a UI path
   already delivered on the main thread.

Only ever fixing the first would have replaced a red build with a red
build; that the second existed at all is what the "never compiled outside
CI" note in PLAN.md was warning about.

### A2. First real build + on-device smoke test (Phase 0 exit criterion)

CI now proves that both shells compile. Nobody has yet run either against a
real backend — follow [`TESTING.md`](TESTING.md), which lists the checks
per platform.

* Run the Android shell on a device/emulator against a real abap2UI5
  endpoint; execute `zcl_test_mobile_poc` (ZXing scan through
  `z2ui5.cc.NativeBridgeScan`), the other methods from the WebView console.
* Same on iOS (simulator for info/toast; **scan needs a real device** —
  VisionKit DataScanner does not run in the simulator).
* Verify the reload-after-background/session-expiry behavior against a real
  session draft timeout (PLAN.md risk 3).
* File whatever version alignment the first build needs (AGP/Kotlin/Xcode)
  as fixes, not workarounds.

**Acceptance:** barcode scanned from an abap2UI5 app inside the Android
shell against a real ABAP backend (the declared PoC exit criterion), and
the same flow demonstrated on an iOS device.

### A3. QR onboarding + managed-config paths verified

Both were built account-free but never exercised end to end. The steps are
now written down — sections 3 and 4 of [`TESTING.md`](TESTING.md), including
how to feed managed config without an EMM (TestDPC on Android, `simctl
defaults` on iOS) — so this task is running them, not designing them.

The QR payload shapes are covered by JVM unit tests, the managed-config
precedence and the enforced app lock are not: those need a device.

**Acceptance:** the runbook executed on both platforms, with corrections
filed where behavior deviates from it.

---

## Block B — SAP account required: finish Phase 1 (BTP SDK onboarding)

Prerequisite: SAP BTP account with Mobile Services; SDK artifacts come via
SAP's repositories / SDK assistant. This block carries **risk #1** (auth
handover) and gates push activation (C) — start it before C.

### B1. Mobile Services application + destination

Create the *Mobile Application* in the MS cockpit with a destination to the
ABAP system (`/sap/bc/z2ui5...` or the cloud HTTP service). Record the
config (host, app id) in the repo docs so QR payloads can be generated.

### B2. SDK onboarding flows replacing the plain URL entry

* Android: `com.sap.cloud.android:onboarding` + foundation — QR onboarding,
  OAuth against MS, passcode/biometric integration (replaces/absorbs the
  hand-rolled `Onboarding.kt` + `AppLock.kt` paths where the SDK covers them).
* iOS: SAPFoundation `OnboardingFlow` equivalent.

### B3. Session handover SDK → WebView (the critical spike)

Evaluate the SDKs' authenticated-WebView helpers **first**; only if they
don't fit, hand-roll: Android `CookieManager` + `shouldInterceptRequest`
(attach `Authorization`), iOS `WKWebsiteDataStore` cookie injection or
`WKURLSchemeHandler`.

**Acceptance:** SPA JSON roundtrips are authenticated straight after
onboarding, with no interactive login inside the WebView.

### B4. Token refresh + timeout

Expired token / MS session → re-run flow silently where possible, then
reload the SPA. Combine with the existing background-expiry reload.

### B5. Close the code TODO in `MobileServicesPush.kt`

Attach the MS session to the push runtime registration call (the SDK push
helper covers exactly this). Unblocks C.

---

## Block C — Firebase/Apple credentials required: activate Phase 2 (push)

Code is shipped; this block is configuration + end-to-end verification.
Depends on B (registration call needs the authenticated session).

* C1. Firebase project → `google-services.json` into `android/app/`
  (FCM activates automatically), server key into MS push settings.
* C2. APNs key/cert in MS, `aps-environment` entitlement + signed build
  (needs an Apple Developer team; extend `ios/project.yml` signing).
* C3. MS push API service key for the backend; configure
  `zcl_test_mobile_push`.
* C4. End-to-end test: ABAP → MS → device on both platforms, including
  deep link on notification tap (data key `url`) into the correct app.

**Acceptance:** a push sent from ABAP opens the target abap2UI5 app on both
platforms via deep link.

---

## Block D — decision gate, then framework integration (Phase 3 graduation)

### D0. Decision: adopt the shell approach — **taken**

The maintainer asked to go ahead with the framework integration on
2026-10-02, ahead of the A2/A3 device results.

* D1. **done in abap2UI5/abap2UI5** ([abap2UI5/abap2UI5#2830](https://github.com/abap2UI5/abap2UI5/pull/2830), not merged yet): `z2ui5.cc.NativeBridgeScan`
  in `app/webapp/cc/` — the frontend lives in the abap2UI5 repo, and
  abap2UI5/frontend is generated from it, never edited. Node specs cover
  the scan, cancel/failure, the invisible placeholder outside the shell and
  the second render on the shim's `abap2ui5native:ready` event.
* D2. **dropped:** `z2ui5_cl_xml_view_cc` sits in the frozen `src/99`
  package and takes no new methods. Apps write the control with
  `z2ui5_cl_ui5_view_builder` (`ns = z2ui5`, `xmlns:z2ui5="z2ui5.cc"`) —
  see `abap/zcl_test_mobile_poc.clas.abap`.
* D3. Sample app in the samples repo + docs page (extend the mobile
  documentation beyond `mobile_start.html` with the Stage-2 shell) — once
  D1 is released.
* D4. Backlog, additive contract v1+ extensions only as concrete apps need
  them: controls for device info, toast, push token and
  biometric-confirm-before-save; NFC, share sheet. Additions only via PR
  review (PLAN.md risk 5). Until then those methods are reachable only from
  the WebView console — the Phase-0 `html:script` + `follow_up_action`
  workaround no longer runs under the framework's CSP.

**Acceptance for D1:** a view naming `z2ui5:NativeBridgeScan` receives the
scanned value as an ordinary event argument — no `html:script` workaround —
and renders an invisible placeholder in a plain browser. Verified in Node
specs; on a device it is part of A2.

---

## Block E — production hardening & distribution (Phase 4 leftovers)

E3–E5 are implemented (see *Recently completed*); what remains are the
decisions and the real-device/rollout work. E1–E2 before any pilot with
real data.

* E1. **Certificate pinning decision.** The groundwork is in place — TLS is
  enforced on both platforms and a `domain-config` template with pin-set
  placeholders sits in `network_security_config.xml`; iOS additionally needs
  a WKWebView challenge handler if the answer is yes. What is missing is the
  decision itself, which is a landscape question, not a coding one: pinning
  breaks TLS-inspecting proxies and turns certificate rotation into an app
  release. Decide, then either fill in the pins (with a backup pin and a
  meetable expiration) or record why not.
* E2. **CSP verification**: confirm the natively-injected shim keeps working
  under hardened UI5 CSP settings on both platforms — needs a real backend
  ([`TESTING.md`](TESTING.md) §7).
* E4a. Pin the WebView floor in the EMM's compliance rules to the same
  number the shell warns at, and re-check `WebViewVersion.MINIMUM_MAJOR`
  against the UI5 version the backend actually serves.
* E6. Observability: enable MS client log upload + usage analytics (needs B).
* E7. Distribution execution per `docs/DISTRIBUTION.md`: EMM rollout
  (managed Play private track / ABM custom app); public-store option later
  needs a demo endpoint + hosted privacy policy.

**Acceptance:** security checklist in `docs/DISTRIBUTION.md` fully checked
or consciously waived per item.

---

## Suggested sequence

```
A1 ✔ ──► A2 ──► A3 ──► D3 ──► D4 (as needed)      D0 ✔  D1 ✔  D2 ✗
                │
                └──► B1 ──► B2 ──► B3 ──► B4/B5 ──► C1–C4 ──► E6
E1, E2, E4a, E7 in parallel once A2 provides real devices
```

Rule of thumb: what could be done in this repo without accounts or hardware
is done; A2/A3 and E1/E2 now need devices, B and C accounts and credentials,
D a maintainer decision, E7 the first real rollout.
