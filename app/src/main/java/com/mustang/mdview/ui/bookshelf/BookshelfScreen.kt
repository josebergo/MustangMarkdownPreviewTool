package com.mustang.mdview.ui.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.mustang.mdview.R
import com.mustang.mdview.data.MarkdownDocument

/** Rows open documents when reading and toggle selection when managing; long titles wrap naturally. */
@Composable
fun BookshelfScreen(documents: List<MarkdownDocument>, busy: Boolean, warning: String?,
    onAdd: () -> Unit, onScan: () -> Unit, onOpen: (MarkdownDocument) -> Unit,
    snackbar: SnackbarHostState, managing: Boolean, selected: Set<String>,
    onManage: (Boolean) -> Unit, onToggle: (String) -> Unit, onToggleAll: () -> Unit,
    onRemove: () -> Unit, onPin: () -> Unit) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar(modifier = Modifier.testTag("bookshelfNavigation")) {
            NavigationBarItem(selected = !managing, enabled = !busy, onClick = { onManage(false) }, icon = {
                Icon(painterResource(R.drawable.ic_books), contentDescription = null)
            }, label = { Text("Bookshelf") })
            NavigationBarItem(selected = managing, enabled = !busy, onClick = { onManage(true) }, icon = {
                Icon(painterResource(R.drawable.ic_file_text), contentDescription = null)
            }, label = { Text("Manage") })
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(30.dp))
            Text("Mustang", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("MUSTANG MD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (managing) "${selected.size} selected" else "My bookshelf",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${documents.size} document(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            if (managing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleAll, enabled = !busy && documents.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text(if (selected.size == documents.size && documents.isNotEmpty()) "Deselect all" else "Select all")
                    }
                    Button(onClick = onPin, enabled = !busy && selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Pin") }
                    OutlinedButton(onClick = onRemove, enabled = !busy && selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Remove") }
                }
                Text("Removing documents keeps the original files.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAdd, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Add files")
                }
                OutlinedButton(onClick = onScan, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_folder_open), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Scan folder")
                }
            }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)); Text("Organizing documents…", Modifier.padding(top = 8.dp)) }
            if (warning != null) Text(warning, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            if (documents.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_books), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text("No documents yet", style = MaterialTheme.typography.titleMedium)
                    Text("Add Markdown files to start reading.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            } else LazyColumn(contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(documents, key = { it.uri }) { document ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().testTag("book:${document.uri}").then(
                            if (managing) Modifier.toggleable(value = document.uri in selected, enabled = !busy,
                                role = Role.Checkbox, onValueChange = { onToggle(document.uri) })
                            else Modifier.clickable { onOpen(document) })) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (managing) {
                                Checkbox(checked = document.uri in selected, onCheckedChange = null, enabled = !busy)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(painterResource(R.drawable.ic_file_text), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(document.title, style = MaterialTheme.typography.titleMedium)
                                if (documents.count { it.title == document.title } > 1) Text(
                                    android.net.Uri.decode(document.uri.substringAfterLast('/')), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (document.pinned) "Pinned · Markdown" else "Markdown", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
