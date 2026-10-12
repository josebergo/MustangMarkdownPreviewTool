package com.mustang.mdview

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustang.mdview.data.MarkdownDocument
import com.mustang.mdview.data.MarkdownImageResolver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Controlled responses verify loader errors, caching, and retries; real HTTPS access requires separate device validation. */
@RunWith(AndroidJUnit4::class)
class ImageLoadingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val document = MarkdownDocument("content://test/markdown", "a.md", "a")
    private fun bitmapBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(60, 160, 220))
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle()
        }.toByteArray()
    }
    private class Response(url: URL, private val status: Int, private val bytes: ByteArray,
        private val redirect: String? = null) : HttpURLConnection(url) {
        override fun connect() { }
        override fun disconnect() { }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getHeaderField(name: String?) = if (name == "Location") redirect else null
    }
    @Test fun failureIsNotCachedRetryWorksAndLargeImagePreservesColorAndRatio() {
        var calls = 0
        val bytes = bitmapBytes()
        val loader = MarkdownImageResolver(context, document) { url ->
            calls++; Response(url, if (calls == 1) 404 else 200, bytes)
        }
        try { loader.load("https://test.example/image.png"); fail("HTTP 404 must fail") }
        catch (_: IllegalArgumentException) { }
        val result = loader.load("https://test.example/image.png")
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(result, 0, result.size)
        assertTrue(bitmap.width <= 1600)
        assertEquals(2f, bitmap.width.toFloat() / bitmap.height, .01f)
        assertEquals(android.graphics.Color.rgb(60, 160, 220), bitmap.getPixel(10, 10))
        bitmap.recycle()
        assertArrayEquals(result, loader.load("https://test.example/image.png"))
        assertEquals(2, calls)
        loader.close()
    }
    @Test fun rejectsInsecureRedirectMalformedImageAndMissingAuthorization() {
        val redirect = MarkdownImageResolver(context, document) { url -> Response(url, 302, byteArrayOf(), "http://test.example/image") }
        try { redirect.load("https://test.example/image"); fail("HTTPS must not be downgraded") }
        catch (_: IllegalArgumentException) { }
        redirect.close()
        val malformed = MarkdownImageResolver(context, document) { url -> Response(url, 200, "broken".toByteArray()) }
        try { malformed.load("https://test.example/image"); fail("Corrupted images must fail") }
        catch (_: IllegalArgumentException) { }
        try { malformed.resolveImage("images/photo.png"); fail("Local images require directory access") }
        catch (_: IllegalArgumentException) { }
        malformed.close()
    }
    @Test fun concurrentRequestsForSameLargeImageAreSerializedAndCached() {
        val bytes = bitmapBytes()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val loader = MarkdownImageResolver(context, document) { url ->
            calls.incrementAndGet(); Thread.sleep(80); Response(url, 200, bytes)
        }
        val workers = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val requests = (1..4).map { workers.submit<ByteArray> { loader.load("https://test.example/shared.png") } }
            val first = requests.first().get(5, TimeUnit.SECONDS)
            requests.forEach { assertArrayEquals(first, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
        } finally { loader.close(); workers.shutdownNow() }
    }
    @Test fun loadsRealHttpsImageOnDevice() {
        val loader = MarkdownImageResolver(context, document)
        try { assertTrue(loader.load("https://www.w3.org/Icons/w3c_home.png").isNotEmpty()) }
        finally { loader.close() }
    }
}
