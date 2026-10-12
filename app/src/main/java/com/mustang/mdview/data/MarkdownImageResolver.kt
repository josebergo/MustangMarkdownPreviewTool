package com.mustang.mdview.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Read images only from HTTPS or authorized directories; download and decode on the WebView request thread. */
class MarkdownImageResolver(private val context: Context, private val document: MarkdownDocument,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    private val closed = AtomicBoolean(false)
    @Volatile private var activeConnection: HttpURLConnection? = null
    private val cache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    fun close() { closed.set(true); activeConnection?.disconnect(); cache.evictAll() }

    /** Return the image URI without concatenating content URI paths; .. must stay within the authorized root. */
    fun resolveImage(destination: String): Uri {
        check(!closed.get())
        val uri = Uri.parse(destination)
        if (uri.scheme != null) {
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "Only HTTPS images are supported" }
            return uri
        }
        val tree = requireNotNull(document.sourceTreeUri) { "Select the folder containing the document to grant access to relative images." }
        val segments = DocumentRules.relativeSegments(document.parentSegments, destination.substringBefore('#').substringBefore('?'))
        var file = requireNotNull(DocumentFile.fromTreeUri(context, Uri.parse(tree)))
        segments.forEach { segment ->
            check(!closed.get())
            file = requireNotNull(file.findFile(segment)) { "Image not found: $segment" }
        }
        require(file.isFile && file.canRead()) { "Cannot read the image" }
        return file.uri
    }

    /** Read dimensions before sampled decoding; limit image bytes and pixels so untrusted images cannot exhaust memory. */
    @Synchronized
    fun load(destination: String): ByteArray {
        check(!closed.get())
        cache.get(destination)?.let { return it }
        val uri = resolveImage(destination)
        val raw = if (uri.scheme == "https") download(URL(uri.toString()))
        else requireNotNull(context.contentResolver.openInputStream(uri)).use(::readLimited)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image format is unsupported or corrupted" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 200_000_000) { "Image dimensions are too large" }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        check(!closed.get())
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(raw, 0, raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample }))
        val output = ByteArrayOutputStream()
        try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) } finally { bitmap.recycle() }
        check(!closed.get())
        return output.toByteArray().also { cache.put(destination, it) }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            check(!closed.get())
            val read = input.read(buffer)
            if (read == -1) break
            require(output.size() + read <= 12 * 1024 * 1024) { "Image exceeds 12 MB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /** Redirects also require HTTPS; each request has a timeout, and results are rejected after leaving the reader. */
    private fun download(start: URL): ByteArray {
        var url = start
        repeat(6) {
            check(!closed.get())
            require(url.protocol == "https" && url.userInfo == null)
            val connection = openConnection(url)
            activeConnection = connection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    url = URL(url, requireNotNull(connection.getHeaderField("Location")))
                } else {
                    require(status in 200..299) { "Image server returned $status" }
                    require(connection.contentLengthLong <= 12 * 1024 * 1024) { "Image exceeds 12 MB" }
                    return connection.inputStream.use(::readLimited)
                }
            } finally { connection.disconnect(); activeConnection = null }
        }
        error("Too many image redirects")
    }
}
