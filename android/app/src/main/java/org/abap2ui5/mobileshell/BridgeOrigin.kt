package org.abap2ui5.mobileshell

import java.net.MalformedURLException
import java.net.URL

/**
 * Which pages get the native bridge: only those served from the origin of
 * the configured endpoint (scheme, host and port, the web's same-origin
 * rule). Navigation stays inside the shell, so the WebView also shows IdP
 * login pages and anything an app links to; none of them may scan, read the
 * push token or ask for a biometric confirmation.
 *
 * Kept free of Android APIs so it can be unit tested on the JVM
 * (see src/test/.../BridgeOriginTest.kt).
 */
object BridgeOrigin {

    /** `scheme://host:port` of an http(s) URL, or null for anything else. */
    fun origin(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val parsed = URL(url.trim())
            val scheme = parsed.protocol.lowercase()
            if (scheme != "https" && scheme != "http") return null
            val host = parsed.host.lowercase()
            if (host.isEmpty()) return null
            val port = if (parsed.port == -1) parsed.defaultPort else parsed.port
            "$scheme://$host:$port"
        } catch (e: MalformedURLException) {
            null
        }
    }

    /** True when [pageUrl] has the origin of [endpointUrl]; false when either is unusable. */
    fun isAllowed(pageUrl: String?, endpointUrl: String?): Boolean {
        val endpoint = origin(endpointUrl) ?: return false
        return origin(pageUrl) == endpoint
    }
}
