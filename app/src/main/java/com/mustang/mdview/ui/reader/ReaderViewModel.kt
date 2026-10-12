package com.mustang.mdview.ui.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustang.mdview.data.DocumentSource
import com.mustang.mdview.data.MarkdownDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loading state distinguishes empty documents, read errors, and normal previews. */
sealed interface ReaderState {
    data object Loading : ReaderState
    data class Content(val document: MarkdownDocument, val html: String, val empty: Boolean) : ReaderState
    data class Error(val message: String) : ReaderState
}

/** Cancel the previous task before switching documents; cancellation exceptions must not become screen errors. */
class ReaderViewModel(private val source: DocumentSource) : ViewModel() {
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state = _state.asStateFlow()
    private var loading: Job? = null

    fun load(document: MarkdownDocument) {
        loading?.cancel()
        _state.value = ReaderState.Loading
        loading = viewModelScope.launch {
            try {
                val markdown = source.readMarkdown(Uri.parse(document.uri))
                val html = withContext(Dispatchers.Default) { MarkdownRenderer().render(markdown) }
                _state.value = ReaderState.Content(document, html, markdown.isBlank())
            } catch (error: CancellationException) { throw error }
            catch (error: java.nio.charset.CharacterCodingException) { _state.value = ReaderState.Error("Unrecognized document encoding. Please use UTF-8.") }
            catch (error: Exception) {
                _state.value = ReaderState.Error(if (error is IllegalArgumentException) error.message ?: "Cannot read the document"
                    else "Cannot read the document. Check the file and permissions, then add it again.")
            }
        }
    }

    fun cancel() { loading?.cancel() }
}
