# Device verification runbook

CI proves that both shells build and that the bridge shim keeps its
contract. Everything below needs real hardware or an emulator and is what
turns the PoC from "compiles" into "works" — the open A2/A3 tasks in
[NEXT_STEPS.md](NEXT_STEPS.md).

None of it needs an SAP, Google or Apple account. What it does need is a
reachable abap2UI5 endpoint and the sample app installed on that system.
Record the results in [TEST_PROTOCOL.md](TEST_PROTOCOL.md).

## 0. Preparation

**Backend**

1. abap2UI5 from current `main` (it carries `z2ui5.cc.NativeBridgeScan`;
   older installations render nothing where the control should be). Pull it
   with abapGit as usual.
2. This repository as a second abapGit repository (online, or offline as
   ZIP): `.abapgit.xml` points at `abap/src/`, so only the sample
   `zcl_test_mobile_poc` is installed. The push client in `abap/push/` is
   ABAP Cloud only and not needed before Block C.
3. Check it in a desktop browser first:
   `https://<host>/sap/bc/z2ui5?sap-client=<client>&app_start=zcl_test_mobile_poc`
   — the page opens, the scan button is visible (`showInBrowser`) and a press
   shows "Scan failed: native shell not available". That proves backend and
   control before any device is involved.
4. The device must reach that host over **HTTPS** with a certificate the
   device trusts — both shells refuse `http://` (section 5). A company CA has
   to be installed on the device; a debug build of the Android shell accepts
   a user-installed CA, a release build does not.

**Android shell**

* Without a local build: Actions → `build_android` → latest run on `main` →
  artifact `abap2ui5-shell-debug-apk`; unzip and `adb install -r app-debug.apk`
  (or copy the APK to the device and allow installs from unknown sources).
* With Android Studio: open `android/`, Run. There is no Gradle wrapper in
  the repo; Android Studio brings its own, on the command line it is
  `gradle assembleDebug` with Gradle 8.9.
* Emulator is fine for everything but a real scan — the emulator camera can
  show a barcode image from the host (Extended controls → Camera → virtual
  scene), which is enough for a first run.

**iOS shell**

* Simulator without building: Actions → `build_ios` → latest run on `main` →
  artifact `abap2ui5-shell-ios-simulator`; unzip,
  `xcrun simctl install booted Abap2UI5Shell.app`,
  `xcrun simctl launch booted org.abap2ui5.Abap2UI5Shell`. Scanning does not
  run in the simulator (DataScanner needs a camera).
* Real device: `brew install xcodegen && cd ios && xcodegen generate`, open
  `Abap2UI5Shell.xcodeproj`, Signing & Capabilities → pick a team (a free
  personal team is enough; change the bundle id if Xcode says it is taken),
  run on the connected device and trust the developer profile under
  Settings → General → VPN & Device Management.

**Console for the bridge methods without a control**

Debug builds of both shells are inspectable: Chrome → `chrome://inspect` for
Android (USB debugging on), Safari → Develop → *device* → the page for iOS
(Web Inspector on under Settings → Safari → Advanced). Then, in the console:

```js
abap2ui5Native                                  // defined = bridge injected
await abap2ui5Native.getDeviceInfo()
await abap2ui5Native.showToast("hello")
await abap2ui5Native.scanBarcode()
await abap2ui5Native.biometricConfirm("test")
await abap2ui5Native.getPushToken()             // rejects "unavailable" before Block C
```

## 1. Bridge smoke test (Phase 0 exit criterion)

Start the shell, enter the endpoint and run `zcl_test_mobile_poc`. Its scan
button is the framework control `z2ui5.cc.NativeBridgeScan` (abap2UI5
custom controls): the scanned value lands in the input field and a toast,
a cancel or failure arrives as a "Scan failed: ..." toast.

The other methods have no framework control yet. Call them from the WebView
console instead - debug builds of both shells are inspectable:
`chrome://inspect` on the desktop for Android, Safari > Develop > *device*
for iOS. For example `await abap2ui5Native.getDeviceInfo()`.

| Check | Android | iOS |
|-------|---------|-----|
| `getDeviceInfo` | manufacturer/model/OS/app version appear | model/OS/app version appear |
| `showToast` | toast at the bottom | overlay label fades in and out |
| `scanBarcode` | ZXing scanner opens, value returns | VisionKit scanner opens, value returns — **physical device only**, DataScanner does not run in the simulator |
| `scanBarcode`, cancelled | promise rejects with `cancelled`, app stays usable | same |
| `biometricConfirm` | system prompt, `true` on success, `false` on cancel | same |
| `getPushToken` | rejects with `unavailable` without `google-services.json` | rejects with `unavailable` on the simulator |

Also confirm the negative case: open the same abap2UI5 app in a desktop
browser. `window.abap2ui5Native` must be undefined; the sample keeps its scan
button visible there (`showInBrowser`), and a press reports "native shell not
available" instead of erroring.

Android injects the shim after the page has loaded. If the scan button is
missing on the first screen in the Android shell, the control did not render
again on the shim's `abap2ui5native:ready` event - report it.

**Phase 0 is done when a barcode scanned in the Android shell arrives in the
ABAP app against a real backend.**

## 2. Session handling (PLAN.md risk 3)

`BACKGROUND_RELOAD_MS` / `backgroundReloadSeconds` is 30 minutes in both
shells. Lower it temporarily (e.g. 30 seconds) to test in one sitting:

1. Open an app, background the shell longer than the interval, return.
2. The SPA must reload rather than resume a dead draft session.
3. Repeat after the server session really expired — the reload must run the
   backend's auth redirect and land on a usable app, not on a login page
   inside a broken frame.

Restore the constant afterwards.

## 3. QR onboarding

Generate a code from either payload shape (any QR generator, e.g.
`qrencode -o onboard.png '<payload>'`):

```
https://host/sap/bc/z2ui5?sap-client=100
```

```json
{"url":"https://host/sap/bc/z2ui5?sap-client=100",
 "msHost":"example.hana.ondemand.com","msAppId":"com.example.app"}
```

Scan it via the *Scan QR* menu entry. The endpoint must be stored (survives
a restart), and with the JSON payload the Mobile Services coordinates must
land in the preferences for the later push registration.

Negative cases — the shell must reject these with the "not a valid
onboarding payload" toast and keep running: a Wi-Fi QR code, a truncated
JSON object, JSON without `url`. (These shapes are also covered by the JVM
unit tests in `android/app/src/test/`.)

## 4. Managed configuration (no EMM needed)

**Android** — with [TestDPC](https://play.google.com/store/apps/details?id=com.afwsamples.testdpc):

1. Install TestDPC and set it up as device owner (fresh device/emulator) or
   in a work profile.
2. TestDPC → *Manage app restrictions* → pick `org.abap2ui5.mobileshell`.
3. Set `endpoint_url`, `app_lock_enabled`, `screenshot_protection`.
4. Restart the shell and verify:
   * managed `endpoint_url` → the *Set endpoint* and *Scan QR* menu entries
     are gone and the pushed URL is loaded;
   * managed `app_lock_enabled=true` → the app-lock toggle is gone, the lock
     prompt appears at start, and on a device **without** an enrolled
     credential the shell refuses to start with the administrator message;
   * managed `screenshot_protection=true` → screenshots fail and the recents
     thumbnail is blank.
5. Clear the restrictions again: every setting must return to user control —
   an unset key is not the same as a managed `false`.

**iOS** — the shell reads the standard managed-app dictionary, so a
simulator can be fed the same input MDM would push:

```sh
xcrun simctl spawn booted defaults write org.abap2ui5.Abap2UI5Shell \
  com.apple.configuration.managed -dict \
  endpoint_url -string "https://host/sap/bc/z2ui5?sap-client=100" \
  app_lock_enabled -bool YES \
  screenshot_protection -bool YES
```

Relaunch the app and check the same three effects. `screenshot_protection`
on iOS only covers the content when the app leaves the foreground (check the
app switcher) — it cannot block a screenshot; see
[DISTRIBUTION.md](DISTRIBUTION.md).

Remove it again with:

```sh
xcrun simctl spawn booted defaults delete org.abap2ui5.Abap2UI5Shell \
  com.apple.configuration.managed
```

## 5. TLS policy

* An `http://` endpoint must be refused with the cleartext message instead
  of a blank WebView (Android `network_security_config`, iOS ATS).
* A debug build must still work behind an inspecting proxy with a
  user-installed CA (Android `debug-overrides`); a release build must not.

## 6. WebView floor (PLAN.md risk 2)

`adb logcat -s WebViewVersion` prints provider and version at every start.
On a device whose WebView is below `WebViewVersion.MINIMUM_MAJOR` the
warning toast must appear — an old System WebView renders the UI5 SPA
half-broken rather than failing loudly, which is exactly the support case
this check exists to short-circuit.

## 7. CSP verification (still open)

Both shells inject the shim through native evaluate APIs
(`evaluateJavascript` / `WKUserScript`), which are not subject to the page's
CSP. Confirm this against a backend with the hardened UI5 CSP settings
switched on, on both platforms, before relying on it in production. This is
the one Phase-4 security item that cannot be settled without a real system.
