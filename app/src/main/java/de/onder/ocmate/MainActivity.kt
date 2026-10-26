package de.onder.ocmate

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * OCMate als TV-App: lädt die Oberfläche aus den App-Dateien und leitet alle
 * Netzwerk-Anfragen (Playlist, EPG, Streams) selbst weiter. Dadurch gelten die
 * Browser-Sperren für http-Streams und fremde Server (CORS) hier nicht.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var chrome: WebChromeClient
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null

    // Vom Nutzer gewählter User-Agent für Anfragen an IPTV-Server (leer = WebView-Standard)
    @Volatile private var customUa: String = ""
    private var webDead = false

    inner class Bridge {
        @JavascriptInterface
        fun setUserAgent(ua: String?) {
            customUa = ua?.trim() ?: ""
        }
    }

    private val assetHost = "appassets.androidplatform.net"
    // http statt https: IPTV-Server liefern meist über http, so gibt es keine Mixed-Content-Sperre.
    private val startUrl = "http://$assetHost/assets/ocmate.html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        val assets = WebViewAssetLoader.Builder()
            .setDomain(assetHost)
            .setHttpAllowed(true)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "$userAgentString OCMate/1.0"
        }
        web.addJavascriptInterface(Bridge(), "OCNative")
        web.isFocusable = true
        web.isFocusableInTouchMode = true

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (uri.host == assetHost) return assets.shouldInterceptRequest(uri)
                if (request.method != "GET" || request.isForMainFrame) return null
                val scheme = uri.scheme ?: return null
                if (scheme != "http" && scheme != "https") return null
                return try {
                    forward(uri.toString(), request.requestHeaders ?: emptyMap())
                } catch (e: Exception) {
                    errorResponse("${e.javaClass.simpleName}: ${e.message ?: "Netzwerkfehler"}")
                }
            }

            // Stürzt die Oberfläche ab (z. B. Speicher voll bei riesigem EPG), App neu aufbauen statt schwarzem Bild
            @SuppressLint("NewApi")
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                webDead = true
                root.removeView(view)
                recreate()
                return true
            }
        }

        chrome = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullView = view
                fullCallback = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                web.visibility = View.GONE
            }

            override fun onHideCustomView() {
                fullView?.let { root.removeView(it) }
                fullView = null
                web.visibility = View.VISIBLE
                fullCallback?.onCustomViewHidden()
                fullCallback = null
                web.requestFocus()
            }
        }
        web.webChromeClient = chrome

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(startUrl)
        web.requestFocus()
    }

    /** Holt eine Datei nativ und gibt sie mit offener CORS-Freigabe an die Oberfläche zurück. */
    private fun forward(start: String, headers: Map<String, String>): WebResourceResponse {
        var target = start
        var hops = 0
        var conn: HttpURLConnection
        while (true) {
            conn = URL(target).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            for ((k, v) in headers) {
                if (k.equals("Origin", true) || k.equals("Referer", true) || k.equals("Accept-Encoding", true)) continue
                conn.setRequestProperty(k, v)
            }
            val ua = customUa
            if (ua.isNotEmpty()) conn.setRequestProperty("User-Agent", ua)
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && location != null && hops < 6) {
                target = URL(URL(target), location).toString()
                conn.disconnect()
                hops++
                continue
            }
            break
        }

        var code = conn.responseCode
        if (code in 300..399 || code < 100 || code > 599) code = 502
        val body = (if (code >= 400) conn.errorStream else conn.inputStream) ?: ByteArrayInputStream(ByteArray(0))
        val type = conn.contentType ?: "application/octet-stream"
        val mime = type.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
        val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(type)?.groupValues?.get(1)?.trim()

        val out = HashMap<String, String>()
        for ((k, v) in conn.headerFields) {
            if (k == null || v.isNullOrEmpty()) continue
            if (k.startsWith("Access-Control-", true) || k.equals("Content-Encoding", true) ||
                k.equals("Content-Length", true) || k.equals("Transfer-Encoding", true) ||
                k.equals("Content-Type", true) || k.equals("Set-Cookie", true)) continue
            out[k] = v.joinToString(", ")
        }
        out["Access-Control-Allow-Origin"] = "*"
        out["Access-Control-Expose-Headers"] = "*"
        val reason = conn.responseMessage?.takeIf { it.isNotBlank() } ?: if (code < 400) "OK" else "Error"
        return WebResourceResponse(mime, charset, code, reason, out, body)
    }

    private fun errorResponse(msg: String): WebResourceResponse =
        WebResourceResponse(
            "text/plain", "utf-8", 502, "Bad Gateway",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(msg.toByteArray())
        )

    private fun js(code: String) = web.evaluateJavascript(code, null)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { js("zap(-1)"); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { js("zap(1)"); return true }
                KeyEvent.KEYCODE_GUIDE -> { js("go('guide')"); return true }
                KeyEvent.KEYCODE_LAST_CHANNEL -> { js("S.prev&&play(S.prev,{keepScroll:true})"); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (fullView != null) {
            chrome.onHideCustomView()
            return
        }
        web.evaluateJavascript("(window.ocBack&&window.ocBack())?1:0") { result ->
            if (result != "1") finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!webDead) web.saveState(outState)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
