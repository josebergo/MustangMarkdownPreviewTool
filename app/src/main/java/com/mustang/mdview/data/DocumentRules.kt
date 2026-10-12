package com.mustang.mdview.data

/** Android-independent filename, path, and encoding rules that can be verified directly on the JVM. */
object DocumentRules {
    fun isMarkdown(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("md", "markdown")
    fun title(name: String): String = name.substringBeforeLast('.').ifBlank { name }

    /** Preserve literal plus signs when decoding URLs; resolve .. segment by segment without escaping the authorized root. */
    fun relativeSegments(parent: List<String>, path: String): List<String> {
        require(!path.startsWith('/') && !path.contains('\\')) { "Image path is outside the authorized folder" }
        val decoded = java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
        require(!decoded.startsWith('/') && !decoded.contains('\\') && !decoded.contains('\u0000'))
        val result = parent.toMutableList()
        decoded.split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> { require(result.isNotEmpty()) { "Image path escapes the authorized folder" }; result.removeAt(result.lastIndex) }
                else -> result.add(it)
            }
        }
        return result
    }

    fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
}
