package com.mustang.mdview

import android.app.Application
import com.mustang.mdview.data.DocumentRepository

/** The repository and ViewModel share a lifecycle so Activity recreation does not split bookshelf state. */
class MustangApplication : Application() {
    val repository by lazy { DocumentRepository(this) }
}
