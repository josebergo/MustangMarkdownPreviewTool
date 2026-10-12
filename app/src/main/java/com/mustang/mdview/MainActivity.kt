package com.mustang.mdview

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.mutableStateOf
import com.mustang.mdview.data.DocumentRepository
import com.mustang.mdview.data.MarkdownDocument
import com.mustang.mdview.ui.theme.MustangTheme
import java.io.File

/** The Activity manages the window and app entry point; the ViewModel owns content state. */
class MainActivity : ComponentActivity() {
    val repository: DocumentRepository get() = (application as MustangApplication).repository
    internal val requestedDocument = mutableStateOf<MarkdownDocument?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MustangTheme { MustangApp(this, repository) } }
    }

    /** Immersive reading allows gestures to reveal system bars; returning to the bookshelf restores the window. */
    fun setReadingFullscreen(reading: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !reading)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (reading) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        if (reading) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** For debug instrumentation tests only; restricted to internal app files without adding an external file entry point. */
    fun openTestDocument(file: File) {
        check(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
        require(file.canonicalPath.startsWith(filesDir.canonicalPath + File.separator))
        requestedDocument.value = MarkdownDocument(android.net.Uri.fromFile(file).toString(), file.name,
            file.nameWithoutExtension)
    }
}
