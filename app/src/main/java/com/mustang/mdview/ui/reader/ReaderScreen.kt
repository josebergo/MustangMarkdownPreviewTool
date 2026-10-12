package com.mustang.mdview.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mustang.mdview.R
import com.mustang.mdview.data.MarkdownDocument

/** Fullscreen content with controls that can be shown or hidden; the reader has no bookshelf bottom bar. */
@Composable
fun ReaderScreen(document: MarkdownDocument, state: ReaderState, onBack: () -> Unit,
    onRetry: () -> Unit, onAuthorizeImages: () -> Unit, snackbar: SnackbarHostState) {
    var controls by rememberSaveable(document.uri) { mutableStateOf(true) }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (state) {
                ReaderState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ReaderState.Error -> Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp)); Button(onClick = onRetry) { Text("Retry") }
                }
                is ReaderState.Content -> if (state.empty) Text("This document is empty.", Modifier.align(Alignment.Center))
                else key(state.document.uri, state.document.sourceTreeUri, state.html) {
                    MarkdownContent(state.html, state.document, Modifier.fillMaxSize(),
                        onToggleControls = { controls = !controls }, onAuthorizeImages = onAuthorizeImages)
                }
            }
            if (controls || state !is ReaderState.Content || state.empty) Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = .96f), modifier = Modifier.align(Alignment.TopCenter)) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_left), "Back to bookshelf") }
                    Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onAuthorizeImages) { Icon(painterResource(R.drawable.ic_folder_open), "Grant image folder access") }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}
