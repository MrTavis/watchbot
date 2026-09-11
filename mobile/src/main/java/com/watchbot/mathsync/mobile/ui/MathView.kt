package com.watchbot.mathsync.mobile.ui

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathView(
    markdownContent: String,
    modifier: Modifier = Modifier,
    fontSizePx: Int = 16
) {
    val context = LocalContext.current
    var isLoaded = remember { false }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoaded = true
                    updateContent(this@apply, markdownContent, fontSizePx)
                }
            }
            loadUrl("file:///android_asset/katex/template_phone.html")
        }
    }

    LaunchedEffect(markdownContent, fontSizePx) {
        if (isLoaded) {
            updateContent(webView, markdownContent, fontSizePx)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

private fun updateContent(webView: WebView, content: String, fontSizePx: Int) {
    // Encode string in Base64 so we never have quote or newline escaping issues in JS evaluate
    val base64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val js = """
        (function() {
            try {
                var decoded = decodeURIComponent(escape(window.atob('$base64')));
                window.setFontSize($fontSizePx);
                window.setContent(decoded);
            } catch(e) {
                console.error("Error setting content:", e);
            }
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}
