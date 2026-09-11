package com.watchbot.mathsync.wear.presentation

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

class WatchWebViewHolder(val webView: WebView) {
    var isLoaded = false

    fun setContent(markdown: String) {
        val base64 = Base64.encodeToString(markdown.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val js = """
            (function() {
                try {
                    var decoded = decodeURIComponent(escape(window.atob('$base64')));
                    window.setContent(decoded);
                } catch(e) {
                    console.error("Watch content error:", e);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun scrollByDelta(deltaPx: Int) {
        webView.evaluateJavascript("window.scrollByDelta($deltaPx);", null)
    }

    fun changeFontSize(delta: Int) {
        webView.evaluateJavascript("window.changeFontSize($delta);", null)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WatchMathView(
    markdownContent: String,
    onHolderReady: (WatchWebViewHolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val holder = remember {
        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
        }
        val h = WatchWebViewHolder(wv)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                h.isLoaded = true
                h.setContent(markdownContent)
            }
        }
        wv.loadUrl("file:///android_asset/katex/template_watch.html")
        h
    }

    LaunchedEffect(holder) {
        onHolderReady(holder)
    }

    LaunchedEffect(markdownContent) {
        if (holder.isLoaded) {
            holder.setContent(markdownContent)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            holder.webView.destroy()
        }
    }

    AndroidView(
        factory = { holder.webView },
        modifier = modifier
    )
}
