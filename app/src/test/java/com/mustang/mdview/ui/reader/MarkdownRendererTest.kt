package com.mustang.mdview.ui.reader

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MarkdownRendererTest {
    private val renderer = MarkdownRenderer()
    @Test fun rendersPreviewAndKeepsCodeLiteral() {
        val doc = Jsoup.parse(renderer.render("# 中文标题\n\n**重点**\n\n```kotlin\nval a = \"**源码**\"\n```"))
        assertEquals("中文标题", doc.selectFirst("h1")?.text())
        assertEquals("重点", doc.selectFirst("strong")?.text())
        assertTrue(doc.selectFirst("pre code")!!.text().contains("**源码**"))
    }
    @Test fun supportsGfmAndReferenceImages() {
        val doc = Jsoup.parse(renderer.render("| A | B |\n|---|---|\n| 1 | 2 |\n\n- [x] 完成\n\n~~删除~~\n\nhttps://example.com\n\n![图][pic]\n\n[pic]: 图片/a.png"))
        assertEquals(1, doc.select("table").size)
        assertEquals("checkbox", doc.selectFirst("input")?.attr("type"))
        assertEquals("删除", doc.selectFirst("del")?.text())
        assertEquals("https://example.com", doc.selectFirst("a")?.attr("href"))
        assertTrue(doc.selectFirst("img")!!.attr("src").startsWith("https://reader.mustang.local/image?"))
    }
    @Test fun escapesDocumentHtmlAndBlocksDangerousLinks() {
        val doc = Jsoup.parse(renderer.render("<script>alert(1)</script>\n\n[x](javascript:alert%281%29)"))
        assertTrue(doc.select("script").isEmpty())
        assertFalse(doc.select("a").any { it.attr("href").startsWith("javascript:") })
        assertTrue(doc.text().contains("<script>"))
    }
    @Test fun supportsHeadingLevelsNestedListsQuotesAndEscaping() {
        val markdown = (1..6).joinToString("\n\n") { "${"#".repeat(it)} 标题 $it" } +
            "\n\n> 引用\n\n1. 外层\n   - 内层\n\n\\*普通星号\\*\n\n[引用链接][ref]\n\n[ref]: https://example.com\n"
        val doc = Jsoup.parse(renderer.render(markdown))
        (1..6).forEach { assertEquals("标题 $it", doc.selectFirst("h$it")?.text()) }
        assertEquals("引用", doc.selectFirst("blockquote")?.text())
        assertEquals("内层", doc.selectFirst("ol li ul li")?.text())
        assertTrue(doc.text().contains("*普通星号*"))
        assertEquals("https://example.com", doc.selectFirst("a")?.attr("href"))
    }
    @Test fun unclosedFenceAndUnknownLanguageRemainReadable() {
        val doc = Jsoup.parse(renderer.render("```unknown-language\n<源码>\n**保留**"))
        assertEquals("language-unknown-language", doc.selectFirst("pre code")?.className())
        assertTrue(doc.selectFirst("pre code")!!.text().contains("<源码>"))
        assertTrue(doc.selectFirst("pre code")!!.text().contains("**保留**"))
        assertTrue(doc.select("strong").isEmpty())
    }
}
