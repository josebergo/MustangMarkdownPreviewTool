package com.mustang.mdview.data

/** The URI identifies a document; directory access and relative location resolve its image references. */
data class MarkdownDocument(
    val uri: String,
    val displayName: String,
    val title: String,
    val sourceTreeUri: String? = null,
    val parentSegments: List<String> = emptyList(),
    val persistentAccess: Boolean = true,
    val pinned: Boolean = false,
)

/** The result of one import, with separate counts for duplicates and failures. */
data class ImportResult(val addedCount: Int, val duplicateCount: Int, val failedCount: Int)

/** Deduplicate by URI; directory imports can add image access to previously imported single files. */
object DocumentCatalog {
    fun remove(existing: List<MarkdownDocument>, selected: Set<String>): List<MarkdownDocument> =
        existing.filterNot { it.uri in selected }

    /** Group pinned items first while preserving order within each group; repeated pinning does not reverse the selected items. */
    fun pin(existing: List<MarkdownDocument>, selected: Set<String>): List<MarkdownDocument> =
        existing.map { if (it.uri in selected) it.copy(pinned = true) else it }.sortedByDescending { it.pinned }

    fun merge(existing: List<MarkdownDocument>, incoming: List<MarkdownDocument>): List<MarkdownDocument> {
        val result = existing.associateByTo(linkedMapOf()) { it.uri }
        incoming.forEach { item ->
            val previous = result[item.uri]
            result[item.uri] = if (previous == null) item
            else if (item.sourceTreeUri != null) item.copy(pinned = previous.pinned)
            else previous.copy(persistentAccess = previous.persistentAccess || item.persistentAccess)
        }
        return result.values.sortedByDescending { it.pinned }
    }
}
