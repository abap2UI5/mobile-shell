# mobile-shell

PoC: running abap2UI5 apps on iOS and Android in a **generic native shell**
built for the SAP mobile stack (SAP Mobile Services + SAP BTP SDK), with the
UI rendered by the regular abap2UI5 frontend in a WebView and native device
features exposed through a small JS bridge. This is a **source** repository
in the abap2UI5 ecosystem, for developers who want to try abap2UI5 apps on
a phone. The code is edited here and checked by CI. It is a proof of
concept with no releases, not a product.

**Start here: [PLAN.md](PLAN.md)** — goals, architecture, phases, risks.

| Directory | Content |
|-----------|---------|
| [`bridge/`](bridge/) | Bridge contract v1 + shared JS shim (source of truth) + contract tests |
| [`android/`](android/) | Android shell — WebView, ZXing scan, QR onboarding, app lock, FCM push, managed config |
| [`ios/`](ios/) | iOS shell — WKWebView, VisionKit scan, app lock, APNs push, managed config (XcodeGen) |
| [`abap/`](abap/) | `src/`: sample app (abapGit-installable, scans through `z2ui5.cc.NativeBridgeScan`); `push/`: Mobile Services push client, installed by hand (ABAP Cloud only) |
| [`docs/`](docs/) | [Distribution & hardening](docs/DISTRIBUTION.md), [device test runbook](docs/TESTING.md), [detailed open-work plan](docs/NEXT_STEPS.md) |

Status: **Phases 0–4 implemented in PoC scope** (see the status table in
PLAN.md). What still needs external accounts: the BTP SDK onboarding flow
(SAP repositories), and push activation (Firebase project, APNs key, Mobile
Services credentials). Online-only by design: abap2UI5 is a server-roundtrip
architecture, offline sync is explicitly out of scope.
