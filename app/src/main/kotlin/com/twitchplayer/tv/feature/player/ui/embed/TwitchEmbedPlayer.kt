package com.twitchplayer.tv.feature.player.ui.embed

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.twitchplayer.tv.BuildConfig
import com.twitchplayer.tv.feature.player.playback.VideoPlayer
import com.twitchplayer.tv.feature.player.playback.VideoQualities
import android.graphics.Color as AndroidColor

/** App-to-page commands for the hosted player page. No-ops if the page predates the controls. */
class WebViewVideoPlayer : VideoPlayer {
    internal var webView: WebView? = null
    private fun run(js: String) {
        webView?.evaluateJavascript("(function(){try{window.tp&&window.tp.$js}catch(e){}})()", null)
    }
    override fun play() = run("play()")
    override fun pause() = run("pause()")
    override fun toggleMute() = run("toggleMute()")

    /**
     * Video qualities offered by the embed for the current stream, with the one in use. Empty when the stream is
     * not ready yet or the hosted page predates the quality controls (it then has no `getQualities`).
     */
    override fun loadQualities(onResult: (VideoQualities) -> Unit) {
        val view = webView ?: return onResult(VideoQualities.None)
        val js = "(function(){try{return window.tp&&window.tp.getQualities?window.tp.getQualities():null}catch(e){return null}})()"
        view.evaluateJavascript(js) { onResult(VideoQualities.parse(it)) }
    }

    /** Persisted by the page: it is re-applied on every stream and every session. [id] is a quality `group` id. */
    override fun setQuality(id: String) {
        val quoted = org.json.JSONObject.quote(id)
        webView?.evaluateJavascript("(function(){try{window.tp&&window.tp.setQuality&&window.tp.setQuality($quoted)}catch(e){}})()", null)
    }

    /** Lets D-pad focus enter the page (for Twitch's own prompts, e.g. mature-content gate); Back leaves it. */
    override fun setInteractive(enabled: Boolean) {
        val view = webView ?: return
        view.descendantFocusability = if (enabled) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        view.isFocusable = enabled
        view.isFocusableInTouchMode = enabled
        if (enabled) view.requestFocus() else view.clearFocus()
    }
}

/**
 * Displays the app-controlled HTTPS page that contains Twitch's official embedded player.
 * This view intentionally exposes no JavaScript bridge and draws no controls over the embed.
 */
@Composable
fun TwitchEmbedPlayer(
    channelLogin: String,
    config: TwitchPlayerConfig,
    modifier: Modifier = Modifier,
    controller: WebViewVideoPlayer? = null,
) {
    val pageUrl = remember(channelLogin, config) { runCatching { config.pageUrl(channelLogin, autoplay = true) }.getOrNull() } ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val webView = remember(context, config.host) {
        createPlayerWebView(context, config.host)
    }

    DisposableEffect(controller, webView) {
        controller?.webView = webView
        onDispose { if (controller?.webView === webView) controller.webView = null }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { view ->
            if (view.tag != pageUrl) {
                view.tag = pageUrl
                view.loadUrl(pageUrl)
            }
        },
    )

    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPlayerWebView(context: android.content.Context, allowedHost: String): WebView = WebView(context).apply {
    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    setBackgroundColor(AndroidColor.BLACK)
    // The player is controlled by the app's own buttons; never trap D-pad focus inside the page.
    isFocusable = false
    isFocusableInTouchMode = false
    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    // No hardware layer: with one, Chromium cannot hand the video to its own SurfaceFlinger layer and every video frame
    // redraws the whole app window through the RenderThread (measured on a TCL MT5896 TV, 1080p60: ~31 app frames/s,
    // RenderThread p50 17 ms, 90 % missed deadlines). Without it the video sits in "Webview Overlay SurfaceControl" at
    // 60 presents/s and the app window only redraws when its own UI changes. See docs/PERFORMANCE.md.
    settings.javaScriptEnabled = true // Required by Twitch's official embedded player.
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false // Autoplay of the official embed; the viewer opened the channel.
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (BuildConfig.DEBUG) android.util.Log.d("TwitchEmbed", "Started $url")
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (BuildConfig.DEBUG) android.util.Log.d("TwitchEmbed", "Finished $url at ${view.width}x${view.height}")
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            if (BuildConfig.DEBUG && request.isForMainFrame) {
                android.util.Log.e("TwitchEmbed", "Load failed ${request.url}: ${error.description} (${error.errorCode})")
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
            if (BuildConfig.DEBUG && request.isForMainFrame) {
                android.util.Log.e("TwitchEmbed", "HTTP ${response.statusCode} ${request.url}")
            }
        }

        // Block main-frame navigation away from the app-controlled page (API 24+).
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame && !isAllowedNavigation(request.url.scheme, request.url.host, allowedHost)

        // API 23 fallback (minSdk 23) where the WebResourceRequest overload is not invoked.
        @Deprecated("Deprecated in Android API")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val uri = android.net.Uri.parse(url)
            return !isAllowedNavigation(uri.scheme, uri.host, allowedHost)
        }
    }
}

internal fun isAllowedNavigation(scheme: String?, host: String?, allowedHost: String): Boolean = scheme.equals("https", ignoreCase = true) && host.equals(allowedHost, ignoreCase = true)
