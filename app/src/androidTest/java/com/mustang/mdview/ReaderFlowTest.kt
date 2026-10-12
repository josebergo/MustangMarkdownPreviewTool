package com.mustang.mdview

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.webkit.WebView
import android.view.View
import android.view.ViewGroup
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/** Use independent test documents to verify navigation from the bookshelf to preview and back. */
@RunWith(AndroidJUnit4::class)
class ReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun bookshelfHasSingleNavigationAndImportActions() {
        compose.onNodeWithTag("bookshelfNavigation").assertExists()
        compose.onNodeWithText("Add files").assertExists()
        compose.onNodeWithText("Scan folder").assertExists()
    }

    @Test fun managementEntryShowsSelectionAndBatchActions() {
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("0 selected").assertExists()
        compose.onNodeWithText("Pin").assertIsNotEnabled()
        compose.onNodeWithText("Remove").assertIsNotEnabled()
        compose.onNodeWithText("Bookshelf").performClick()
        compose.onNodeWithText("Add files").assertExists()
    }

    @Test fun multiSelectionPinsPersistsAndRemovesWithoutDeletingFiles() {
        val context = compose.activity
        val repository = context.repository
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add files").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        compose.waitForIdle()
        val root = java.io.File(context.filesDir, "saf-fixtures").apply { mkdirs() }
        val names = listOf("管理甲", "管理乙", "管理丙")
        names.forEach { java.io.File(root, "$it.md").writeText("# $it") }
        val authority = "com.mustang.mdview.testdocs"
        val tree = android.provider.DocumentsContract.buildTreeDocumentUri(authority, "root")
        context.grantUriPermission(context.packageName, tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uris = names.map { android.provider.DocumentsContract.buildDocumentUri(authority, "root/$it.md") }
        runBlocking {
            repository.removeDocuments(repository.documents.value.map { it.uri }.toSet())
            repository.addDocuments(uris)
        }
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("管理乙").performClick()
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("管理丙").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithTag("markdownPreview").assertDoesNotExist()
        val output = java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        capture(java.io.File(output, "management-selected.png"))
        compose.onNodeWithText("Pin").performClick()
        compose.waitUntil(5000) { repository.documents.value.firstOrNull()?.pinned == true }
        org.junit.Assert.assertEquals(listOf("管理乙", "管理丙", "管理甲"), repository.documents.value.map { it.title })
        val restored = com.mustang.mdview.data.DocumentRepository(context)
        runBlocking { restored.restore(); repository.addDocuments(listOf(uris[1])) }
        org.junit.Assert.assertEquals(repository.documents.value, restored.documents.value)
        compose.onNodeWithText("0 selected").assertExists()
        compose.onNodeWithText("管理乙").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("book:${uris[1]}").assertIsOn()
        compose.onNodeWithText("管理丙").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(5000) { repository.documents.value.size == 1 }
        org.junit.Assert.assertEquals("管理甲", repository.documents.value.single().title)
        runBlocking { restored.restore() }
        org.junit.Assert.assertEquals(repository.documents.value, restored.documents.value)
        names.forEach { org.junit.Assert.assertTrue(java.io.File(root, "$it.md").exists()) }
        runBlocking { org.junit.Assert.assertEquals("# 管理乙", repository.readMarkdown(uris[1])) }
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Deselect all").performClick()
        compose.onNodeWithText("0 selected").assertExists()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(5000) { repository.documents.value.isEmpty() }
        compose.onNodeWithText("No documents yet").assertExists()
        compose.onNodeWithText("Pin").assertIsNotEnabled()
        compose.onNodeWithText("Bookshelf").performClick()
        compose.onNodeWithText("Add files").assertExists()
        names.forEach { java.io.File(root, "$it.md").delete() }
    }

    @Test fun repositorySurvivesActivityRecreation() {
        val original = compose.activity.repository
        compose.activityRule.scenario.recreate()
        org.junit.Assert.assertSame(original, compose.activity.repository)
    }

    @Test fun opensPreviewThenReturnsToBookshelf() {
        val context = compose.activity
        val sample = java.io.File(context.filesDir, "preview-test.md")
        val testContext = InstrumentationRegistry.getInstrumentation().context
        sample.writeText(testContext.assets.open("markdown/preview.md").bufferedReader().use { it.readText() })
        compose.runOnUiThread { compose.activity.openTestDocument(sample) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("markdownPreview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bookshelfNavigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to bookshelf").performClick()
        compose.onNodeWithTag("bookshelfNavigation").assertExists()
    }

    @Test fun previewRendersSafImageHighlightedCodeAndScrollableTable() {
        val context = compose.activity
        val root = java.io.File(context.filesDir, "saf-fixtures").apply { mkdirs() }
        java.io.File(root, "images").mkdirs()
        val image = android.graphics.Bitmap.createBitmap(800, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(image)
        canvas.drawColor(android.graphics.Color.rgb(60, 160, 220))
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 185, 80) }
        canvas.drawRect(400f, 0f, 800f, 400f, paint)
        java.io.File(root, "images/sample.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val markdown = InstrumentationRegistry.getInstrumentation().context.assets.open("markdown/preview.md")
            .bufferedReader().use { it.readText() }.replace("![网络测试图片](https://www.w3.org/Icons/w3c_home.png)", "")
        java.io.File(root, "阅读验收.md").writeText(markdown)
        val tree = android.provider.DocumentsContract.buildTreeDocumentUri("com.mustang.mdview.testdocs", "root")
        context.grantUriPermission(context.packageName, tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runBlocking { context.repository.scanDirectory(tree) }
        compose.onNodeWithText("阅读验收").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("markdownPreview").fetchSemanticsNodes().isNotEmpty() }
        waitForJs("document.querySelector('main h1') && document.querySelector('main img') && document.querySelector('main img').naturalWidth > 0")
        org.junit.Assert.assertEquals("true", evaluate("document.querySelector('h1').textContent.includes('野马')"))
        org.junit.Assert.assertEquals("true", evaluate("document.querySelector('strong').textContent === 'Preview'"))
        org.junit.Assert.assertEquals("true", evaluate("document.querySelector('pre code .hljs-keyword') !== null"))
        org.junit.Assert.assertEquals("true", evaluate("getComputedStyle(document.querySelector('.table-scroll')).overflowX === 'auto'"))
        org.junit.Assert.assertEquals("true", evaluate("document.querySelector('img').width <= document.querySelector('main').clientWidth"))
        org.junit.Assert.assertEquals("true", evaluate("document.querySelector('img').naturalWidth / document.querySelector('img').naturalHeight === 2"))
        val output = java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        capture(java.io.File(output, "preview-text.png"))
        evaluate("document.querySelector('pre').scrollIntoView(); true")
        Thread.sleep(300)
        capture(java.io.File(output, "preview-code.png"))
        evaluate("document.querySelector('img').scrollIntoView(); true")
        Thread.sleep(300)
        capture(java.io.File(output, "preview-image.png"))
        compose.onNodeWithContentDescription("Back to bookshelf").performClick()
    }

    @Test fun oneMegabyteDocumentCanLoadAndReturn() {
        val file = java.io.File(compose.activity.filesDir, "large-preview.md")
        file.writeText("# 大文档\n\n" + ("正常阅读中文 English。\n\n".repeat(34000)))
        org.junit.Assert.assertTrue(file.length() >= 1_000_000)
        compose.runOnUiThread { compose.activity.openTestDocument(file) }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("markdownPreview").fetchSemanticsNodes().isNotEmpty() }
        waitForJs("document.querySelector('main h1') && document.querySelector('main h1').textContent === '大文档'")
        compose.onNodeWithContentDescription("Back to bookshelf").performClick()
        compose.onNodeWithTag("bookshelfNavigation").assertExists()
    }

    @Test fun currentDocumentSurvivesRecreation() {
        val file = java.io.File(compose.activity.filesDir, "rotate-preview.md")
        file.writeText("# 旋转后保持文档\n\n阅读内容")
        compose.runOnUiThread { compose.activity.openTestDocument(file) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("markdownPreview").fetchSemanticsNodes().isNotEmpty() }
        waitForJs("document.querySelector('main h1') !== null")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("markdownPreview").fetchSemanticsNodes().isNotEmpty() }
        waitForJs("document.querySelector('main h1') && document.querySelector('main h1').textContent === '旋转后保持文档'")
        compose.onNodeWithContentDescription("Back to bookshelf").performClick()
        compose.onNodeWithTag("bookshelfNavigation").assertExists()
    }

    private fun webView(): WebView {
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return requireNotNull(find(compose.activity.window.decorView))
    }
    private fun evaluate(expression: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        compose.runOnUiThread { webView().evaluateJavascript(expression) { result = it; latch.countDown() } }
        check(latch.await(5, TimeUnit.SECONDS)) { "JS callback timed out" }
        return result
    }
    private fun waitForJs(expression: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (evaluate(expression) == "true") return
            Thread.sleep(100)
        }
        org.junit.Assert.fail("Preview DOM condition timed out: $expression; page=" + evaluate("location.href + '\\n' + document.body.innerText"))
    }
    private fun capture(file: java.io.File) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val destination = "/data/local/tmp/mustang-${file.name}"
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} $destination")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}
