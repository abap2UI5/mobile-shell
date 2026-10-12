/*
 * abap2UI5 native bridge shim — contract v1.
 *
 * Source of truth for the JS side of the shell bridge. Both shells inject
 * this file into the WebView after page load:
 *   - Android: copied to android/app/src/main/assets/native-bridge.js,
 *     injected via evaluateJavascript in onPageFinished
 *   - iOS: bundled as ios/Resources/native-bridge.js, injected as WKUserScript
 *
 * Keep the three copies identical — edit here first, then sync.
 *
 * abap2UI5 apps feature-detect with `if (window.abap2ui5Native) ...` and must
 * degrade gracefully in a plain browser, where this object does not exist.
 */
(function () {
  "use strict";
  if (window.abap2ui5Native) return;

  var pending = new Map();
  var seq = 0;

  // Called by the native side to settle a pending promise.
  // `result` is a JS value (the native side builds the literal), `error` a
  // message string or null/undefined.
  function settle(id, result, error) {
    var p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    if (error) p.reject(new Error(error));
    else p.resolve(result);
  }
  window.__a2u5BridgeResolve = settle;

  function call(invoke) {
    var id = String(++seq);
    return new Promise(function (resolve, reject) {
      pending.set(id, { resolve: resolve, reject: reject });
      try {
        invoke(id);
      } catch (e) {
        pending.delete(id);
        reject(e);
      }
    });
  }

  // Android has two transports. The message channel
  // (androidx.webkit WebViewCompat.addWebMessageListener) exists only in
  // frames of the endpoint's origin; the @JavascriptInterface object is the
  // fallback for a WebView without that feature. The shell installs one of
  // them, never both.
  var channel = window.__a2u5AndroidChannel;
  var android = channel ? null : window.__a2u5Android;
  var ios =
    !channel &&
    !android &&
    window.webkit &&
    window.webkit.messageHandlers &&
    window.webkit.messageHandlers.a2u5Bridge;
  if (!channel && !android && !ios) return; // plain browser — no bridge

  // The channel answers every call with a JSON string
  // {id, result, error} through the reply proxy of the calling frame.
  if (channel) {
    channel.onmessage = function (event) {
      var answer;
      try {
        answer = JSON.parse(event.data);
      } catch (e) {
        return;
      }
      if (answer && typeof answer.id === "string") {
        settle(answer.id, answer.result, answer.error);
      }
    };
  }

  // Message transports: the Android channel takes a JSON string, the iOS
  // message handler a structured object.
  function messageCall(method, extra) {
    return call(function (id) {
      var msg = { id: id, method: method };
      if (extra) for (var k in extra) msg[k] = extra[k];
      if (channel) channel.postMessage(JSON.stringify(msg));
      else ios.postMessage(msg);
    });
  }

  window.abap2ui5Native = {
    available: true,
    version: 1,
    platform: ios ? "ios" : "android",

    // Promise<{platform, model, osVersion, appVersion}>
    // The two synchronous calls of the Android fallback run inside the
    // promise as well: an interface that throws (torn down, or an answer that is no JSON) is a
    // rejection, never an exception out of the call itself.
    getDeviceInfo: function () {
      if (android)
        return new Promise(function (resolve) {
          resolve(JSON.parse(android.platformInfo()));
        });
      return messageCall("getDeviceInfo");
    },

    // Promise<void>
    showToast: function (text) {
      if (android)
        return new Promise(function (resolve) {
          android.showToast(String(text));
          resolve();
        });
      return messageCall("showToast", { text: String(text) });
    },

    // Promise<string> — resolves with the scanned barcode value,
    // rejects with Error("cancelled") or Error("unsupported").
    scanBarcode: function () {
      if (android)
        return call(function (id) {
          android.scanBarcode(id);
        });
      return messageCall("scanBarcode");
    },

    // Promise<string> — the device push token (FCM registration token on
    // Android, APNs token hex on iOS). Rejects with Error("unavailable")
    // when push is not configured on the device/shell.
    getPushToken: function () {
      if (android)
        return call(function (id) {
          android.getPushToken(id);
        });
      return messageCall("getPushToken");
    },

    // Promise<boolean> — true when the user passed the biometric/device
    // credential check, false when they cancelled or failed it.
    // Rejects with Error("unavailable") when no authenticator is enrolled.
    biometricConfirm: function (reason) {
      if (android)
        return call(function (id) {
          android.biometricConfirm(id, String(reason));
        });
      return messageCall("biometricConfirm", { reason: String(reason) });
    },
  };

  // Announce the bridge. Android injects this shim once the page has loaded,
  // which can be after the abap2UI5 frontend rendered a control that asked
  // for it (z2ui5.cc.NativeBridgeScan): such a control listens for this
  // event and renders again. Fired once per document, on first injection.
  if (
    typeof window.dispatchEvent === "function" &&
    typeof Event === "function"
  ) {
    window.dispatchEvent(new Event("abap2ui5native:ready"));
  }
})();
