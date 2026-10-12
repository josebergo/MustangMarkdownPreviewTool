package com.mustang.mdview

import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustang.mdview.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentAccessTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.filesDir, "saf-fixtures")
    private val tree = DocumentsContract.buildTreeDocumentUri("com.mustang.mdview.testdocs", "root")
    private fun uri(id: String) = DocumentsContract.buildDocumentUri("com.mustang.mdview.testdocs", "root/$id")
    @Before fun fixtures() {
        context.grantUriPermission(context.packageName, tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        root.mkdirs(); File(root, "docs").mkdirs(); File(root, "图片").mkdirs()
        File(root, "docs/书.MD").writeText("\uFEFF# 中文标题\n\n![颜色](../图片/a%20b.png)")
        File(root, "书.MD").writeText("同名不同路径")
        File(root, "skip.txt").writeText("不是 Markdown")
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(60, 160, 220))
        File(root, "图片/a b.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun scanDeduplicatesAndRestoresWhileKeepingSameTitles() = runBlocking {
        val repository = DocumentRepository(context)
        val result = repository.scanDirectory(tree)
        assertEquals(2, result.addedCount)
        assertEquals(2, repository.documents.value.size)
        assertTrue(repository.documents.value.all { it.title == "书" })
        val duplicate = repository.addDocuments(listOf(uri("docs/书.MD")))
        assertEquals(0, duplicate.addedCount)
        assertEquals(1, duplicate.duplicateCount)
        assertEquals(2, repository.documents.value.size)
        val restored = DocumentRepository(context)
        restored.restore()
        assertEquals(repository.documents.value, restored.documents.value)
        assertTrue(restored.readMarkdown(uri("docs/书.MD")).startsWith("# 中文标题"))
    }
    @Test fun relativeImageReadsThroughSafAndRejectsEscapes() = runBlocking {
        val repository = DocumentRepository(context)
        repository.scanDirectory(tree)
        val document = repository.documents.value.first { it.parentSegments == listOf("docs") }
        val images = MarkdownImageResolver(context, document)
        assertTrue(images.load("../图片/a%20b.png").size > 0)
        try { images.resolveImage("../../outside.png"); fail("Paths outside the authorized root must be rejected") }
        catch (_: IllegalArgumentException) { }
        images.close()
        try { images.load("../图片/a%20b.png"); fail("Loading must stop after leaving the screen") }
        catch (_: IllegalStateException) { }
    }
    @Test fun singleFileCanEnrichDirectoryAndDeletedSourceFails() = runBlocking {
        val repository = DocumentRepository(context)
        repository.addDocuments(listOf(uri("docs/书.MD")))
        val document = repository.documents.value.single()
        assertNull(document.sourceTreeUri)
        assertTrue(repository.authorizeImages(document, tree))
        assertEquals(2, repository.documents.value.size)
        File(root, "docs/书.MD").delete()
        try { repository.readMarkdown(uri("docs/书.MD")); fail("Deleted documents must report a read failure") }
        catch (_: java.io.FileNotFoundException) { }
    }
    @Test fun damagedIndexIsPreservedAndDoesNotCrash() = runBlocking {
        File(context.filesDir, "bookshelf.json").writeText("{invalid")
        val repository = DocumentRepository(context)
        repository.restore()
        assertTrue(repository.documents.value.isEmpty())
        assertNotNull(repository.warning.value)
        assertTrue(context.filesDir.listFiles()!!.any { it.name.startsWith("bookshelf-corrupt-") })
    }
    @Test fun reimportAfterDirectoryRevocationRemovesInvalidImageContext() = runBlocking {
        val repository = DocumentRepository(context)
        repository.scanDirectory(tree)
        context.contentResolver.releasePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.revokeUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val single = uri("docs/书.MD")
        context.grantUriPermission(context.packageName, single, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        repository.addDocuments(listOf(single))
        val document = repository.documents.value.first { it.uri == single.toString() }
        assertNull(document.sourceTreeUri)
        assertTrue(repository.readMarkdown(single).contains("中文标题"))
    }
}
