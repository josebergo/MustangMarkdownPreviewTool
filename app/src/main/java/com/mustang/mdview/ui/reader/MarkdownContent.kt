package com.mustang.mdview.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.mustang.mdview.data.MarkdownDocument
import com.mustang.mdview.data.MarkdownImageResolver
import java.io.ByteArrayInputStream

/** HTML comes from the safe renderer; the WebView has no JS bridge and cannot access file paths or arbitrary content URIs. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownContent(html: String, document: MarkdownDocument, modifier: Modifier = Modifier,
    onToggleControls: () -> Unit, onAuthorizeImages: () -> Unit) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val toggle by rememberUpdatedState(onToggleControls)
    val authorize by rememberUpdatedState(onAuthorizeImages)
    var scrollY by rememberSaveable(document.uri) { mutableIntStateOf(0) }
    val resolver = remember(document) { MarkdownImageResolver(context.applicationContext, document) }
    val page = remember(html) { MarkdownRenderer().page(html) }
    DisposableEffect(resolver) { onDispose { resolver.close() } }
    AndroidView(modifier = modifier.testTag("markdownPreview"), factory = { viewContext ->
        WebView(viewContext).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.textZoom = (fontScale * 100).toInt()
            settings.setSupportMultipleWindows(false)
            isVerticalScrollBarEnabled = false
            setOnScrollChangeListener { _, _, y, _, _ -> scrollY = y }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { view.post { view.scrollTo(0, scrollY) } }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "mustang") {
                        when (uri.host) { "toggle" -> toggle(); "authorize-images" -> authorize() }
                    } else if (uri.host == "reader.mustang.local" && !uri.fragment.isNullOrBlank()) return false
                    else if (request.hasGesture() && uri.scheme in listOf("https", "http", "mailto")) {
                        try { viewContext.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        catch (_: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(viewContext, "No app can open this link.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val uri = request.url
                    if (uri.scheme != "https" || uri.host != "reader.mustang.local" || uri.port != -1) return failure()
                    return try {
                        when (uri.path) {
                            "/index.html" -> WebResourceResponse("text/html", "UTF-8",
                                ByteArrayInputStream(page.toByteArray(Charsets.UTF_8)))
                            "/assets/reader.css", "/assets/reader.js", "/assets/highlight.min.js" -> {
                                val name = requireNotNull(uri.lastPathSegment)
                                WebResourceResponse(if (name.endsWith("css")) "text/css" else "application/javascript", "UTF-8",
                                    context.assets.open("reader/$name"))
                            }
                            "/image" -> WebResourceResponse("image/png", null, ByteArrayInputStream(
                                resolver.load(requireNotNull(uri.getQueryParameter("destination")))))
                            else -> failure()
                        }
                    } catch (_: Exception) { failure() }
                }

                private fun failure() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    emptyMap(), ByteArrayInputStream(byteArrayOf()))
            }
            loadUrl("${MarkdownRenderer.ORIGIN}/index.html")
        }
    }, update = { it.settings.textZoom = (fontScale * 100).toInt() }, onRelease = {
        resolver.close(); it.stopLoading(); it.destroy()
    })
}
