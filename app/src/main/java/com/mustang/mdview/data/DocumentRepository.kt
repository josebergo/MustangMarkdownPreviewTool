package com.mustang.mdview.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

/** Screens depend on this interface; device file access stays in the implementation to simplify state and cancellation verification. */
interface DocumentSource {
    val documents: StateFlow<List<MarkdownDocument>>
    val warning: StateFlow<String?>
    suspend fun addDocuments(uris: List<Uri>): ImportResult
    suspend fun scanDirectory(treeUri: Uri): ImportResult
    suspend fun readMarkdown(uri: Uri): String
    suspend fun removeDocuments(uris: Set<String>)
    suspend fun pinDocuments(uris: Set<String>)
}

/** SAF document access and atomic index persistence without copying content or requesting full storage access. */
class DocumentRepository(private val context: Context) : DocumentSource {
    private val resolver = context.contentResolver
    private val index = AtomicFile(File(context.filesDir, "bookshelf.json"))
    private val mutex = Mutex()
    private val _documents = MutableStateFlow<List<MarkdownDocument>>(emptyList())
    override val documents: StateFlow<List<MarkdownDocument>> = _documents
    private val _warning = MutableStateFlow<String?>(null)
    override val warning: StateFlow<String?> = _warning

    /** Restore the index in the background; preserve a diagnostic copy if corrupted so the next write does not erase the only evidence. */
    suspend fun restore() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!index.baseFile.exists()) return@withLock
            try {
                val array = JSONArray(index.openRead().bufferedReader().use { it.readText() })
                _documents.value = (0 until array.length()).map { position ->
                    val json = array.getJSONObject(position)
                    val parents = json.optJSONArray("parents") ?: JSONArray()
                    MarkdownDocument(json.getString("uri"), json.getString("name"), json.getString("title"),
                        json.optString("tree").takeIf { it.isNotEmpty() },
                        (0 until parents.length()).map { parents.getString(it) }, json.optBoolean("persistent", true),
                        json.optBoolean("pinned", false))
                }.sortedByDescending { it.pinned }
            } catch (error: Exception) {
                index.baseFile.copyTo(File(context.filesDir, "bookshelf-corrupt-${System.currentTimeMillis()}.json"))
                _warning.value = "The bookshelf index is corrupted. A backup was saved. Please add your documents again."
            }
        }
    }

    override suspend fun addDocuments(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val incoming = mutableListOf<MarkdownDocument>()
        var failures = 0
        uris.forEach { uri ->
            coroutineContext.ensureActive()
            try {
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: throw IllegalArgumentException("Cannot read the filename")
                if (DocumentRules.isMarkdown(name)) {
                    val persistent = resolver.persistedUriPermissions.any { it.isReadPermission && it.uri == uri }
                    incoming.add(MarkdownDocument(canonicalDocumentUri(uri), name, DocumentRules.title(name), persistentAccess = persistent))
                } else failures++
            } catch (error: Exception) { coroutineContext.ensureActive(); failures++ }
        }
        mergeAndSave(incoming, failures)
    }

    override suspend fun scanDirectory(treeUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        require(root != null && root.isDirectory && root.canRead()) { "Cannot access the folder. Please grant access again." }
        val incoming = mutableListOf<MarkdownDocument>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<DocumentFile, List<String>>>()
        queue.add(root to emptyList())
        var failures = 0
        val persistent = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (directory, parents) = queue.removeFirst()
            if (!visited.add(directory.uri.toString())) continue
            val children = try { directory.listFiles() } catch (error: Exception) {
                coroutineContext.ensureActive(); failures++; continue
            }
            children.forEach { child ->
                coroutineContext.ensureActive()
                try {
                    val name = child.name ?: return@forEach
                    if (!child.canRead()) { failures++; return@forEach }
                    if (child.isDirectory) queue.add(child to (parents + name))
                    else if (DocumentRules.isMarkdown(name)) incoming.add(MarkdownDocument(
                        canonicalDocumentUri(child.uri), name, DocumentRules.title(name), treeUri.toString(), parents, persistent))
                } catch (error: Exception) { coroutineContext.ensureActive(); failures++ }
            }
        }
        mergeAndSave(incoming, failures)
    }

    /** Normalize tree/document and single-file document URIs to the same identity for a SAF document. */
    private fun canonicalDocumentUri(uri: Uri): String = try {
        android.provider.DocumentsContract.buildDocumentUri(uri.authority,
            android.provider.DocumentsContract.getDocumentId(uri)).toString()
    } catch (_: IllegalArgumentException) { uri.normalizeScheme().toString() }

    private suspend fun mergeAndSave(incoming: List<MarkdownDocument>, failures: Int): ImportResult = mutex.withLock {
        val old = _documents.value
        val imported = incoming.map { it.uri }.toSet()
        // When reimporting a single file after directory access is revoked, discard the invalid directory context so stale URIs do not block reading.
        val refreshed = old.map { document ->
            if (document.uri in imported && document.sourceTreeUri != null &&
                DocumentFile.fromTreeUri(context, Uri.parse(document.sourceTreeUri))?.canRead() != true)
                document.copy(sourceTreeUri = null, parentSegments = emptyList()) else document
        }
        val merged = DocumentCatalog.merge(refreshed, incoming)
        val added = merged.size - old.size
        save(merged)
        _documents.value = merged
        ImportResult(added, incoming.size - added, failures)
    }

    private fun save(documents: List<MarkdownDocument>) {
        val array = JSONArray()
        documents.forEach { document -> array.put(JSONObject().apply {
            put("uri", document.uri); put("name", document.displayName); put("title", document.title)
            put("tree", document.sourceTreeUri ?: ""); put("parents", JSONArray(document.parentSegments))
            put("persistent", document.persistentAccess)
            put("pinned", document.pinned)
        }) }
        val stream = index.startWrite()
        try { stream.write(array.toString().toByteArray(Charsets.UTF_8)); index.finishWrite(stream) }
        catch (error: Exception) { index.failWrite(stream); throw error }
    }

    /** Only update the local index; keep source files and directory permissions that other documents may share. */
    override suspend fun removeDocuments(uris: Set<String>) = updateShelf { DocumentCatalog.remove(it, uris) }
    override suspend fun pinDocuments(uris: Set<String>) = updateShelf { DocumentCatalog.pin(it, uris) }

    private suspend fun updateShelf(transform: (List<MarkdownDocument>) -> List<MarkdownDocument>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = transform(_documents.value)
            save(updated)
            _documents.value = updated
        }
    }

    /** Limit content size to avoid exhausting memory; check cancellation between chunks so reads from a closed screen cannot overwrite the new screen. */
    override suspend fun readMarkdown(uri: Uri): String = withContext(Dispatchers.IO) {
        val document = documents.value.find { it.uri == uri.toString() }
        val readableUri = if (document?.sourceTreeUri != null) android.provider.DocumentsContract.buildDocumentUriUsingTree(
            Uri.parse(document.sourceTreeUri), android.provider.DocumentsContract.getDocumentId(uri)) else uri
        val stream = if (uri.scheme == "file" && uri.path?.startsWith(context.filesDir.path + "/") == true)
            File(requireNotNull(uri.path)).inputStream() else resolver.openInputStream(readableUri)
        requireNotNull(stream) { "Cannot read the document. Please add it again." }
        stream.use { input ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                require(result.size() + read <= 8 * 1024 * 1024) { "Documents larger than 8 MB cannot be opened yet." }
                result.write(buffer, 0, read)
            }
            DocumentRules.decodeUtf8(result.toByteArray())
        }
    }

    /** Additional image directory access requires finding the current document in that directory to avoid mapping relative paths incorrectly. */
    suspend fun authorizeImages(document: MarkdownDocument, tree: Uri): Boolean {
        scanDirectory(tree)
        return documents.value.any { it.uri == document.uri && it.sourceTreeUri == tree.toString() }
    }
}
