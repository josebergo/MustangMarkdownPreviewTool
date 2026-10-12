package com.mustang.mdview.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustang.mdview.data.DocumentSource
import com.mustang.mdview.data.ImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Bookshelf import state and feedback; cancelling the file picker does not call the data layer. */
class BookshelfViewModel(private val source: DocumentSource) : ViewModel() {
    val documents = source.documents
    val warning = source.warning
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _managing = MutableStateFlow(false)
    val managing = _managing.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()

    fun setManaging(value: Boolean) {
        if (_busy.value || _managing.value == value) return
        _managing.value = value
        _selected.value = emptySet()
    }
    fun toggle(uri: String) {
        if (!_managing.value || _busy.value || documents.value.none { it.uri == uri }) return
        _selected.value = if (uri in _selected.value) _selected.value - uri else _selected.value + uri
    }
    fun toggleAll() {
        if (!_managing.value || _busy.value) return
        val all = documents.value.map { it.uri }.toSet()
        _selected.value = if (_selected.value == all) emptySet() else all
    }
    fun removeSelected() = manageSelection("Removed from bookshelf:") { source.removeDocuments(it) }
    fun pinSelected() = manageSelection("Pinned:") { source.pinDocuments(it) }

    private fun manageSelection(message: String, operation: suspend (Set<String>) -> Unit) {
        val uris = _selected.value.intersect(documents.value.map { it.uri }.toSet())
        if (!_managing.value || _busy.value || uris.isEmpty()) return
        _busy.value = true
        viewModelScope.launch {
            try {
                operation(uris)
                _selected.value = emptySet()
                _message.value = "$message ${uris.size} document(s)"
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = error.message ?: "Bookshelf operation failed. Please try again." }
            finally { _busy.value = false }
        }
    }

    fun clearMessage() { _message.value = null }
    fun add(uris: List<Uri>) { if (uris.isNotEmpty()) runImport { source.addDocuments(uris) } }
    fun scan(uri: Uri?) { if (uri != null) runImport { source.scanDirectory(uri) } }

    private fun runImport(operation: suspend () -> ImportResult) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val result = operation()
                val temporary = documents.value.any { !it.persistentAccess }
                _message.value = "Added ${result.addedCount} · Duplicates ${result.duplicateCount} · Failed ${result.failedCount}" +
                    if (temporary) "\nSome documents have temporary access and may need to be added again after restarting." else ""
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = error.message ?: "Import failed. Please select again." }
            finally { _busy.value = false }
        }
    }
}
