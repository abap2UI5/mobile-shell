package org.abap2ui5.mobileshell

import org.json.JSONException
import org.json.JSONObject

/**
 * The wire format of the Android message channel (bridge/README.md,
 * "Transports"): the shim posts a call as a JSON string
 * `{id, method, text?, reason?}`, the shell answers through the frame's
 * reply proxy with `{id, result, error}`. The `@JavascriptInterface`
 * fallback settles calls with `__a2u5BridgeResolve` and a JS literal
 * instead ([literal]).
 *
 * Kept free of Android APIs so it can be unit tested on the JVM
 * (see src/test/.../BridgeMessageTest.kt).
 */
object BridgeMessage {

    /** A call from the shim. [text] and [reason] are empty when the method takes none. */
    data class Call(val id: String, val method: String, val text: String, val reason: String)

    /** The call in [data], or null when it is no JSON object with a string id and method. */
    fun parse(data: String?): Call? {
        if (data == null) return null
        return try {
            val json = JSONObject(data)
            val id = json.opt("id") as? String ?: return null
            val method = json.opt("method") as? String ?: return null
            Call(id, method, json.optString("text", ""), json.optString("reason", ""))
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * The answer to call [id]. [result] is null, a String, a Boolean or a
     * JSONObject; [error] null or the error message the promise rejects with.
     */
    fun reply(id: String, result: Any?, error: String?): String =
        JSONObject()
            .put("id", id)
            .put("result", result ?: JSONObject.NULL)
            .put("error", error ?: JSONObject.NULL)
            .toString()

    /** [result] as a JS literal for `__a2u5BridgeResolve` (the fallback transport). */
    fun literal(result: Any?): String = when (result) {
        null -> "null"
        is String -> JSONObject.quote(result)
        is Boolean, is JSONObject -> result.toString()
        else -> throw IllegalArgumentException("no bridge result type: ${result::class.java}")
    }
}
