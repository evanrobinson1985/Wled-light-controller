package com.wledcontroller.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Picks a file with any app that can open one: the phone's own Files, My Files, another file
 * manager, Drive, a gallery. Input is the file types wanted (any type when empty).
 */
class PickFile : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val types = input.ifEmpty { arrayOf("*/*") }
        val get = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (types.size == 1) types[0] else "*/*")
        if (types.size > 1) get.putExtra(Intent.EXTRA_MIME_TYPES, types)
        return Intent.createChooser(get, "Choose a file with")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.takeIf { resultCode == Activity.RESULT_OK }?.let { it.data ?: it.clipData?.takeIf { c -> c.itemCount > 0 }?.getItemAt(0)?.uri }
}

/** The picked file's name as the app that gave it says, or null. */
fun Uri.displayName(context: Context): String? = runCatching {
    context.contentResolver.query(this, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
