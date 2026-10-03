# AGENTS.md — mobile-shell

Single source of truth for agents working on the **abap2UI5 mobile shell**:
a proof of concept that runs abap2UI5 apps on iOS and Android in a generic
native shell. The regular abap2UI5 frontend renders in a WebView, and native
device features reach it through a small JavaScript bridge. `CLAUDE.md` next
to this file is a pointer at it, nothing more.

**Language:** English for all code, comments, docs, commit messages, PRs.

## What this repository is

A source repository: humans and agents edit here. It holds two native shells,
the bridge contract they share, and a sample abap2UI5 app that uses the
bridge. Start with [PLAN.md](PLAN.md), which has the goals, the architecture,
the phase status table and the risks.
[docs/NEXT_STEPS.md](docs/NEXT_STEPS.md) has the ordered list of open work.

- **Online-only by design.** abap2UI5 is a server-roundtrip architecture,
  so offline sync (Offline OData) is out of scope. This is a product decision
  (PLAN.md, "Scope decision"), not a gap. Do not add offline features.
- **One generic client for all apps.** The shell knows one URL and all UI
  logic stays in ABAP. Nothing app-specific goes into the shells.
- **The default build needs no account.** Both shells build with stock
  tooling and without SAP, Google or Apple credentials. The SAP BTP SDK is
  not a dependency yet (its artefacts need an SAP account), and Firebase push
  is applied only when `android/app/google-services.json` exists (that file
  is git-ignored). Keep it that way. A change that needs an account goes
  behind the same kind of switch, with the missing step documented.

## Layout

| Path | |
|---|---|
| `bridge/native-bridge.js` | The JS shim both shells inject. **The source of truth** for the contract |
| `bridge/README.md` | Contract v1, its rules, the ready event and the callback mechanism |
| `bridge/test/native-bridge.test.js` | Contract tests: the shim against both transports, mocked, in Node |
| `android/` | Android shell (Kotlin, Gradle): WebView, ZXing scan, QR onboarding, app lock, FCM push, managed config |
| `android/app/src/main/assets/native-bridge.js` | Byte-identical copy of `bridge/native-bridge.js` |
| `android/app/src/test/` | JVM unit tests (`OnboardingTest`, `WebViewVersionTest`) |
| `ios/` | iOS shell (SwiftUI, WKWebView, VisionKit scan, app lock, APNs push, managed config) |
| `ios/project.yml` | XcodeGen spec. The Xcode project and `Info.plist` are generated from it and git-ignored |
| `ios/Resources/native-bridge.js` | Byte-identical copy of `bridge/native-bridge.js` |
| `abap/src/` | The sample app `zcl_test_mobile_poc`, which abapGit installs |
| `abap/push/` | `zcl_test_mobile_push`, the Mobile Services push client. ABAP Cloud only, installed by hand |
| `docs/` | `DISTRIBUTION.md` (managed config, rollout, hardening), `TESTING.md` (device runbook), `TEST_PROTOCOL.md` (its fill-in sheet), `NEXT_STEPS.md` (open work) |
| `.github/workflows/` | `abap.yml`, `bridge.yml`, `build-android.yml`, `build-ios.yml` (see "Validation") |
| `.github/dependabot.yml` | GitHub Actions updates, monthly and grouped. Every action is pinned to a commit SHA with the version in a trailing comment |

## Rules for the bridge

- **Edit `bridge/native-bridge.js` first, then copy it** to
  `android/app/src/main/assets/` and `ios/Resources/`. The three files are
  byte-identical, and the `bridge`, `build_android` and `build_ios`
  workflows `diff` them.
- **The contract follows the rules in `bridge/README.md`.** Apps
  feature-detect `window.abap2ui5Native` and individual methods. Every
  method returns a promise. Changes within a major version are additive
  only, and a rename or removal bumps the major version and keeps the old
  names working for one release. The transport (Android
  `@JavascriptInterface`, iOS `webkit.messageHandlers`) stays hidden behind
  the shim, and `__a2u5BridgeResolve` / `__a2u5Android` stay private.
- **`abap2ui5native:ready` fires once per document.** Android injects the
  shim in `onPageFinished`, which can be after the first view has rendered,
  and `z2ui5.cc.NativeBridgeScan` listens for the event to render again.
- **A change to the contract updates `bridge/README.md`** and adds a case to
  `bridge/test/native-bridge.test.js`.

## Rules for the shells

- **Both platforms behave the same.** A bridge method, a managed
  configuration key or a security setting lands on Android and iOS
  together, or the gap is written down in PLAN.md / docs/NEXT_STEPS.md. The
  managed configuration keys (`endpoint_url`, `app_lock_enabled`,
  `screenshot_protection`) carry the same names on both platforms, with the
  precedence managed configuration > stored preference > manual entry/QR
  (docs/DISTRIBUTION.md). An unset key stays under user control.
- **HTTPS only.** Android's `network_security_config.xml` allows no
  cleartext, and iOS sets `NSAllowsArbitraryLoads: false` in `project.yml`.
  Adding an exception has to be a deliberate, documented edit.
  Certificate pinning is an open deployment decision (NEXT_STEPS.md, E1),
  not a default.
- **iOS: edit `ios/project.yml`, never a generated project.**
  `Abap2UI5Shell.xcodeproj` and `Info.plist` come from
  `xcodegen generate`. `build_ios` runs on `macos-15`, because current
  XcodeGen writes a project format the Xcode of `macos-14` cannot open (the
  workflow echoes both versions for that reason).
- **Android: there is no Gradle wrapper.** CI uses Gradle 8.9 and JDK 17.
  The app targets SDK 35 with `minSdk` 26, and `WebViewVersion.MINIMUM_MAJOR`
  is the WebView floor the shell warns below.

## Rules for the ABAP side

- **abapGit installs `abap/src/` only** (`.abapgit.xml`: `STARTING_FOLDER`
  `/abap/src/`). `abap/push/` carries no `.clas.xml`, is not in the
  abaplint scope and is copied into an ABAP Cloud system by hand.
- **The scan control belongs to the framework.** `z2ui5.cc.NativeBridgeScan`
  lives in abap2UI5 (`app/webapp/cc/NativeBridgeScan.js`), not here. A
  control for another bridge method (`getDeviceInfo`, `showToast`,
  `getPushToken`, `biometricConfirm`) is a pull request to abap2UI5. The
  Phase 0 `html:script` + `follow_up_action` workaround no longer runs,
  because the framework's CSP has no `'unsafe-inline'`. Do not bring it back.
- **Views use `z2ui5_cl_ui5_view_builder`.** `z2ui5_cl_xml_view_cc` is
  frozen, so a custom control is written with the builder (`ns = z2ui5`,
  `xmlns:z2ui5="z2ui5.cc"`), as `zcl_test_mobile_poc` does.
- **The sample needs abap2UI5 `main`.** `abaplint.json` resolves abap2UI5
  with `"branch": "main"`, declared rather than left to the default branch
  (CONVENTIONS §9). That is what the sample needs today, because
  `z2ui5.cc.NativeBridgeScan` is still under "unreleased" in abap2UI5's
  `changelog.txt` and docs/TESTING.md tells testers to install abap2UI5
  from `main`.
- abaplint checks `abap/src/` at syntax `v750`, with
  `errorNamespace` `^(Z|Y|LCL_|TY_|LIF_)`.

## Validation

There is no `package.json`. The checks are run directly:

```bash
# bridge (what the bridge workflow runs)
diff bridge/native-bridge.js android/app/src/main/assets/native-bridge.js
diff bridge/native-bridge.js ios/Resources/native-bridge.js
node --test bridge/test/native-bridge.test.js

# ABAP sample (what the abap workflow runs, against abap2UI5 main)
npx -y @abaplint/cli@latest abaplint.json

# Android (needs JDK 17, Gradle 8.9 and the Android SDK)
cd android && gradle testDebugUnitTest && gradle assembleDebug

# iOS (needs macOS, Xcode and XcodeGen)
cd ios && xcodegen generate   # then build the Abap2UI5Shell scheme
```

| Workflow (`name:`) | Runs on | Does |
|---|---|---|
| `abap.yml` (`abap`) | changes to `abap/**`, `abaplint.json`, `.abapgit.xml`; weekly; by hand | abaplint against abap2UI5 `main` |
| `bridge.yml` (`bridge`) | changes to `bridge/**`; by hand | shim copies in sync, contract tests |
| `build-android.yml` (`build_android`) | changes to `android/**`, `bridge/**`; by hand | shim copy in sync, unit tests, debug APK (artefact `abap2ui5-shell-debug-apk`) |
| `build-ios.yml` (`build_ios`) | changes to `ios/**`, `bridge/**`; by hand | shim copy in sync, XcodeGen, unsigned simulator build (artefact `abap2ui5-shell-ios-simulator`) |

CI proves that the shells compile and that the bridge keeps its contract.
It does not prove that they work against a backend: that takes a device and
the runbook in docs/TESTING.md, with results recorded in
docs/TEST_PROTOCOL.md. When a status changes, update the status table in
PLAN.md and docs/NEXT_STEPS.md in the same pull request.

## Conventions

- All text files are LF-only, enforced by `.gitattributes`.
- `.nvmrc` says `22`, the Node version the `abap` and `bridge` workflows
  set up.
- Third-party actions are pinned to a commit SHA, with the version in a
  trailing comment (`uses: actions/checkout@<sha> # v4.4.0`). Dependabot
  moves the pins. The workflows ask for `contents: read` only.
- The ecosystem-wide rules (workflow and npm-script naming, toolchain
  versions, which documentation files exist, commit style) live in
  [CONVENTIONS.md](https://github.com/abap2UI5/abap2UI5/blob/main/.github/shared/CONVENTIONS.md)
  and bind this repository too. Known gaps, each waiting on a decision:
  - There is no `package.json`, so there is no `npm run check` / `npm test`
    (§3) and no `engines.node` (§4).
  - abaplint runs as `@abaplint/cli@latest` rather than the ecosystem's pin
    (§4).
  - The workflow file names have no verb prefix (`abap.yml`, `bridge.yml`),
    and the `name:` keys use snake case (`build_android`) (§2).
