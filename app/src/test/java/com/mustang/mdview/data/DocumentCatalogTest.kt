package com.mustang.mdview.data

import org.junit.Assert.*
import org.junit.Test

class DocumentCatalogTest {
    @Test fun pinsSelectedDocumentsInStableOrder() {
        val documents = listOf("a", "b", "c", "d").map { MarkdownDocument(it, "$it.md", it) }
        val pinned = DocumentCatalog.pin(documents, setOf("d", "b"))
        assertEquals(listOf("b", "d", "a", "c"), pinned.map { it.uri })
        assertEquals(listOf(true, true, false, false), pinned.map { it.pinned })
        assertEquals(pinned, DocumentCatalog.pin(pinned, setOf("b", "d")))
    }
    @Test fun removesOnlySelectedUrisIncludingSameTitles() {
        val a = MarkdownDocument("a", "书.md", "书")
        val b = a.copy(uri = "b")
        val c = a.copy(uri = "c")
        assertEquals(listOf(b), DocumentCatalog.remove(listOf(a, b, c), setOf("a", "c")))
        assertEquals(listOf(a), DocumentCatalog.remove(listOf(a), emptySet()))
    }
    @Test fun reimportPreservesPinnedStateAndOrder() {
        val a = MarkdownDocument("a", "a.md", "a", pinned = true)
        val b = MarkdownDocument("b", "b.md", "b")
        val refreshed = a.copy(sourceTreeUri = "tree", pinned = false)
        val merged = DocumentCatalog.merge(listOf(a, b), listOf(refreshed))
        assertEquals(listOf("a", "b"), merged.map { it.uri })
        assertTrue(merged.first().pinned)
        assertEquals("tree", merged.first().sourceTreeUri)
    }
    @Test fun deduplicatesUrisButKeepsSameNamedDocuments() {
        val a = MarkdownDocument("content://docs/a", "书.md", "书")
        val b = a.copy(uri = "content://docs/b")
        assertEquals(listOf(a, b), DocumentCatalog.merge(listOf(a), listOf(a, b)))
    }
    @Test fun enrichesExistingDocumentWithImageDirectory() {
        val a = MarkdownDocument("content://docs/a", "书.md", "书")
        val authorized = a.copy(sourceTreeUri = "content://tree/root", parentSegments = listOf("docs"))
        assertEquals(listOf(authorized), DocumentCatalog.merge(listOf(a), listOf(authorized)))
        assertEquals(listOf(authorized), DocumentCatalog.merge(listOf(authorized), listOf(a)))
    }
}
