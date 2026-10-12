package com.mustang.mdview

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mustang.mdview.data.DocumentRepository
import com.mustang.mdview.data.MarkdownDocument
import com.mustang.mdview.ui.bookshelf.BookshelfScreen
import com.mustang.mdview.ui.bookshelf.BookshelfViewModel
import com.mustang.mdview.ui.reader.ReaderScreen
import com.mustang.mdview.ui.reader.ReaderViewModel
import kotlinx.coroutines.launch

/** The two-screen app navigates using URI state; save the active URI to restore reading after rotation. */
@Composable
fun MustangApp(activity: MainActivity, repository: DocumentRepository) {
    val bookshelf: BookshelfViewModel = viewModel(factory = viewModelFactory { initializer { BookshelfViewModel(repository) } })
    val reader: ReaderViewModel = viewModel(factory = viewModelFactory { initializer { ReaderViewModel(repository) } })
    val documents by bookshelf.documents.collectAsStateWithLifecycle()
    val busy by bookshelf.busy.collectAsStateWithLifecycle()
    val message by bookshelf.message.collectAsStateWithLifecycle()
    val warning by bookshelf.warning.collectAsStateWithLifecycle()
    val managing by bookshelf.managing.collectAsStateWithLifecycle()
    val selectedBooks by bookshelf.selected.collectAsStateWithLifecycle()
    val readerState by reader.state.collectAsStateWithLifecycle()
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requested = activity.requestedDocument.value
    val selected = selectedUri?.let { uri -> documents.find { it.uri == uri } ?: requested?.takeIf { it.uri == uri }
        ?: MarkdownDocument(uri, Uri.parse(uri).lastPathSegment ?: "Markdown", "Markdown") }

    fun persist(uri: Uri) {
        try { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Some providers only grant temporary access; the repository reports this in the import result. */ }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(::persist); bookshelf.add(uris)
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) persist(uri)
        bookshelf.scan(uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        // selectedUri is saved so the authorization target survives Activity recreation while the picker is open.
        val target = selected
        if (uri != null && target != null) {
            persist(uri)
            scope.launch {
                try {
                    val found = repository.authorizeImages(target, uri)
                    if (found) repository.documents.value.find { it.uri == target.uri }?.let { reader.load(it) }
                    snackbar.showSnackbar(if (found) "Image folder access granted" else "Document not found in this folder. Select the folder containing it.")
                } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (_: Exception) { snackbar.showSnackbar("Could not grant folder access. Select it again.") }
            }
        }
    }

    LaunchedEffect(repository) { repository.restore(); restored = true }
    LaunchedEffect(requested) { if (requested != null) selectedUri = requested.uri }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); bookshelf.clearMessage() }
    }
    LaunchedEffect(selected, restored) { if (restored && selected != null) reader.load(selected) }
    DisposableEffect(selectedUri) {
        activity.setReadingFullscreen(selectedUri != null)
        onDispose { reader.cancel(); activity.setReadingFullscreen(false) }
    }
    fun back() { selectedUri = null; activity.requestedDocument.value = null }
    BackHandler(enabled = selectedUri != null) { back() }
    BackHandler(enabled = selectedUri == null && managing) { bookshelf.setManaging(false) }
    if (selected != null) {
        ReaderScreen(selected, readerState, onBack = { back() }, onRetry = { reader.load(selected) },
            onAuthorizeImages = { imagePicker.launch(null) }, snackbar = snackbar)
    } else BookshelfScreen(documents, busy || !restored, warning,
        onAdd = { filePicker.launch(arrayOf("*/*")) }, onScan = { treePicker.launch(null) },
        onOpen = { selectedUri = it.uri }, snackbar = snackbar,
        managing = managing, selected = selectedBooks, onManage = bookshelf::setManaging,
        onToggle = bookshelf::toggle, onToggleAll = bookshelf::toggleAll,
        onRemove = bookshelf::removeSelected, onPin = bookshelf::pinSelected)
}
