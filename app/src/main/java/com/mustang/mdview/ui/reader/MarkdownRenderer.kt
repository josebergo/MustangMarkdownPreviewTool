package com.mustang.mdview.ui.reader

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Image
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.net.URLEncoder

/** The single entry point from Markdown to controlled HTML; document HTML cannot execute. */
class MarkdownRenderer {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(),
        TaskListItemsExtension.create(), AutolinkExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true)
        .sanitizeUrls(true).attributeProviderFactory {
            org.commonmark.renderer.html.AttributeProvider { node, tag, attributes ->
                if (node is Image && tag == "img") {
                    attributes["src"] = "$ORIGIN/image?destination=${URLEncoder.encode(node.destination, "UTF-8")}"
                    attributes["loading"] = "lazy"
                }
                if (tag == "a") attributes["rel"] = "noreferrer noopener"
            }
        }.build()

    fun render(markdown: String): String = renderer.render(parser.parse(markdown))

    /** Only run bundled app scripts; content HTML is escaped, and all images go through the controlled native loader. */
    fun page(fragment: String): String = """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'self'; script-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'">
        <link rel="stylesheet" href="$ORIGIN/assets/reader.css">
        <script defer src="$ORIGIN/assets/highlight.min.js"></script>
        <script defer src="$ORIGIN/assets/reader.js"></script>
        </head><body><main>$fragment</main></body></html>
    """.trimIndent()

    companion object { const val ORIGIN = "https://reader.mustang.local" }
}
