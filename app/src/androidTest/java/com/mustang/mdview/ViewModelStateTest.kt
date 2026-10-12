package com.mustang.mdview

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustang.mdview.data.*
import com.mustang.mdview.ui.bookshelf.BookshelfViewModel
import com.mustang.mdview.ui.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewModelStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private class Source : DocumentSource {
        override val documents = MutableStateFlow<List<MarkdownDocument>>(emptyList())
        override val warning = MutableStateFlow<String?>(null)
        var fail = false
        var calls = 0
        override suspend fun addDocuments(uris: List<Uri>): ImportResult {
            calls++; delay(40)
            if (fail) error("Import failed")
            return ImportResult(1, 0, 0)
        }
        override suspend fun scanDirectory(treeUri: Uri) = addDocuments(listOf(treeUri))
        override suspend fun removeDocuments(uris: Set<String>) { documents.value = DocumentCatalog.remove(documents.value, uris) }
        override suspend fun pinDocuments(uris: Set<String>) { documents.value = DocumentCatalog.pin(documents.value, uris) }
        override suspend fun readMarkdown(uri: Uri): String {
            if (uri.lastPathSegment == "slow") withContext(NonCancellable) { delay(250) }
            if (fail) throw java.nio.charset.MalformedInputException(1)
            return if (uri.lastPathSegment == "empty") "" else "# ${uri.lastPathSegment}"
        }
    }
    @Test fun cancelledPickerDoesNothingAndFailureRestoresActions() = runBlocking {
        val source = Source(); val vm = BookshelfViewModel(source)
        instrumentation.runOnMainSync { vm.add(emptyList()); vm.scan(null) }
        assertEquals(0, source.calls)
        source.fail = true
        instrumentation.runOnMainSync { vm.add(listOf(Uri.parse("content://test/file"))) }
        withTimeout(5000) { vm.message.first { it != null } }
        assertFalse(vm.busy.value)
        assertEquals("Import failed", vm.message.value)
    }
    @Test fun newDocumentCancelsOldResultAndEmptyIsExplicit() = runBlocking {
        val source = Source(); val vm = ReaderViewModel(source)
        fun doc(name: String) = MarkdownDocument("content://test/$name", "$name.md", name)
        instrumentation.runOnMainSync { vm.load(doc("slow")) }
        delay(30)
        instrumentation.runOnMainSync { vm.load(doc("current")) }
        val result = withTimeout(5000) { vm.state.filterIsInstance<ReaderState.Content>().first() }
        assertEquals("current", result.document.title)
        delay(350)
        assertEquals("current", (vm.state.value as ReaderState.Content).document.title)
        instrumentation.runOnMainSync { vm.load(doc("empty")) }
        assertTrue(withTimeout(5000) { vm.state.filterIsInstance<ReaderState.Content>().first() }.empty)
        source.fail = true
        instrumentation.runOnMainSync { vm.load(doc("invalid")) }
        assertTrue(withTimeout(5000) { vm.state.filterIsInstance<ReaderState.Error>().first() }.message.contains("UTF-8"))
        instrumentation.runOnMainSync { vm.cancel() }
    }
}
