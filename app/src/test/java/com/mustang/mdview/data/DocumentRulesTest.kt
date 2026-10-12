package com.mustang.mdview.data

import org.junit.Assert.*
import org.junit.Test

class DocumentRulesTest {
    @Test fun acceptsMarkdownNamesAndPreservesInnerDots() {
        assertTrue(DocumentRules.isMarkdown("a.MD"))
        assertTrue(DocumentRules.isMarkdown("书.markdown"))
        assertFalse(DocumentRules.isMarkdown("a.txt"))
        assertEquals("a.b", DocumentRules.title("a.b.md"))
    }
    @Test fun resolvesChineseSpaceAndParentWithinRoot() {
        assertEquals(listOf("图片", "a b.png"),
            DocumentRules.relativeSegments(listOf("文档"), "../图片/a%20b.png"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalOutsideAuthorizedRoot() {
        DocumentRules.relativeSegments(emptyList(), "../secret.png")
    }
    @Test fun decodesUtf8BomAndEmptyFile() {
        assertEquals("中文", DocumentRules.decodeUtf8("\uFEFF中文".toByteArray()))
        assertEquals("", DocumentRules.decodeUtf8(byteArrayOf()))
    }
    @Test(expected = java.nio.charset.CharacterCodingException::class)
    fun rejectsMalformedUtf8() { DocumentRules.decodeUtf8(byteArrayOf(0xC3.toByte(), 0x28)) }
}
