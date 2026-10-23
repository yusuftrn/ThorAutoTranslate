package com.thorautotranslate

import android.app.DownloadManager
import android.content.Context
import java.io.File

/** Remove files/requests left by unreleased experimental builds without touching language models or keys. */
@Synchronized
internal fun cleanupRetiredModels(context: Context) {
    val downloads = context.getSharedPreferences("model_downloads", Context.MODE_PRIVATE)
    val requests = downloads.all.values.filterIsInstance<Long>().filter { it > 0 }.toLongArray()
    if (requests.isNotEmpty()) context.getSystemService(DownloadManager::class.java).remove(*requests)
    listOfNotNull(context.getExternalFilesDir(null), context.noBackupFilesDir).forEach {
        check(File(it, "models").let { folder -> !folder.exists() || folder.deleteRecursively() })
    }
    context.deleteSharedPreferences("model_downloads")
    val settings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val edit = settings.edit().remove("local_model_id")
    if (settings.getString("translation_mode", "OFFLINE") == "LOCAL") edit.putString("translation_mode", "OFFLINE")
    edit.apply()
}
