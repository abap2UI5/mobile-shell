# Contributing

mobile-shell is a proof of concept. It runs abap2UI5 apps on iOS and Android
in a generic native shell: the regular abap2UI5 frontend renders in a
WebView, and native device features reach it through a small JavaScript
bridge.

Start with [PLAN.md](PLAN.md) for the scope and the architecture, and with
[docs/NEXT_STEPS.md](docs/NEXT_STEPS.md) for the open work. The rules for
the bridge, the two shells and the ABAP sample are in
[AGENTS.md](AGENTS.md). Two of them are scope decisions: the shell is
online-only, and it carries nothing app-specific.

## Before you open a pull request

Run the checks for the part you changed. They are what the workflows run.
There is no `package.json`, so they are called directly:

```bash
# bridge: the three shim copies are byte-identical, and the contract tests pass
diff bridge/native-bridge.js android/app/src/main/assets/native-bridge.js
diff bridge/native-bridge.js ios/Resources/native-bridge.js
node --test bridge/test/native-bridge.test.js

# ABAP sample, against abap2UI5 main
npx -y @abaplint/cli@latest abaplint.json

# Android (JDK 17, Gradle 8.9, Android SDK)
cd android && gradle testDebugUnitTest && gradle assembleDebug

# iOS (macOS, Xcode, XcodeGen)
cd ios && xcodegen generate
```

## What CI checks

| Workflow | Proves |
| --- | --- |
| `abap` | the sample app in `abap/src/` passes abaplint against abap2UI5 `main` |
| `bridge` | the shim copies are in sync, and the contract tests pass |
| `build_android` | the shim copy is in sync, the JVM unit tests pass, and a debug APK builds |
| `build_ios` | the shim copy is in sync, and an unsigned simulator build succeeds |

CI shows that the shells compile and that the bridge keeps its contract. It
does not show that they work against a backend. That takes a device and the
runbook in [docs/TESTING.md](docs/TESTING.md). If you test on a device, say
so in the pull request and record the result in
[docs/TEST_PROTOCOL.md](docs/TEST_PROTOCOL.md).

## Conventions

English for code, comments, commit messages and pull requests. Write commit
subjects in the imperative and describe the outcome, not the mechanics. One
topic per pull request. The rules the whole ecosystem follows are in
[abap2UI5's CONVENTIONS.md](https://github.com/abap2UI5/abap2UI5/blob/main/.github/shared/CONVENTIONS.md).
