package org.abap2ui5.mobileshell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The message channel's wire format: what the shim posts must parse, and
 * what the shell answers must be the JSON the shim settles promises from
 * (bridge/test/native-bridge.test.js, "android-channel").
 */
class BridgeMessageTest {

    @Test
    fun `a call of the shim parses`() {
        assertEquals(
            BridgeMessage.Call("3", "biometricConfirm", "", "Approve the posting"),
            BridgeMessage.parse("""{"id":"3","method":"biometricConfirm","reason":"Approve the posting"}"""))
        assertEquals(
            BridgeMessage.Call("4", "showToast", "saved", ""),
            BridgeMessage.parse("""{"id":"4","method":"showToast","text":"saved"}"""))
    }

    @Test
    fun `anything else is no call`() {
        assertNull(BridgeMessage.parse(null))
        assertNull(BridgeMessage.parse("not json"))
        assertNull(BridgeMessage.parse("[]"))
        assertNull(BridgeMessage.parse("""{"method":"scanBarcode"}"""))
        assertNull(BridgeMessage.parse("""{"id":1,"method":"scanBarcode"}"""))
        assertNull(BridgeMessage.parse("""{"id":"1"}"""))
    }

    @Test
    fun `a reply carries id, result and error`() {
        val ok = JSONObject(BridgeMessage.reply("1", "4006381333931", null))
        assertEquals("1", ok.getString("id"))
        assertEquals("4006381333931", ok.getString("result"))
        assertEquals(true, ok.isNull("error"))

        val refused = JSONObject(BridgeMessage.reply("2", null, "forbidden"))
        assertEquals(true, refused.isNull("result"))
        assertEquals("forbidden", refused.getString("error"))

        val info = JSONObject(BridgeMessage.reply("3", JSONObject().put("platform", "android"), null))
        assertEquals("android", info.getJSONObject("result").getString("platform"))
        assertEquals(false, JSONObject(BridgeMessage.reply("4", false, null)).getBoolean("result"))
    }

    @Test
    fun `fallback literals are valid JS`() {
        assertEquals("null", BridgeMessage.literal(null))
        assertEquals("\"a\\\"b<\\/script>\"", BridgeMessage.literal("a\"b</script>"))
        assertEquals("true", BridgeMessage.literal(true))
        assertEquals("""{"platform":"android"}""", BridgeMessage.literal(JSONObject().put("platform", "android")))
    }
}
