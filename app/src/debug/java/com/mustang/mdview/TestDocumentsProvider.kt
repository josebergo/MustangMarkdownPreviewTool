package com.mustang.mdview

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File

/** Debug-only SAF test provider protected by a system signature permission to verify real ContentResolver access. */
class TestDocumentsProvider : DocumentsProvider() {
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE)
    private val root get() = File(requireNotNull(context).filesDir, "saf-fixtures")
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: emptyArray())
    private fun file(id: String): File {
        val result = if (id == "root") root else File(root, id.removePrefix("root/"))
        require(result.canonicalPath == root.canonicalPath || result.canonicalPath.startsWith(root.canonicalPath + "/"))
        return result
    }
    private fun add(cursor: MatrixCursor, id: String, file: File) {
        val values = mapOf<String, Any>(Document.COLUMN_DOCUMENT_ID to id, Document.COLUMN_DISPLAY_NAME to file.name,
            Document.COLUMN_MIME_TYPE to if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream",
            Document.COLUMN_FLAGS to 0, Document.COLUMN_SIZE to file.length())
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: columns)
        val target = file(documentId)
        if (target.exists()) add(cursor, documentId, target)
        return cursor
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(projection ?: columns)
        file(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { add(cursor, "$parentDocumentId/${it.name}", it) }
        return cursor
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.MODE_READ_ONLY)
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith("$parentDocumentId/")
}
