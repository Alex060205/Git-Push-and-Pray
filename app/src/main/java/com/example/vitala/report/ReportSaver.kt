package com.example.vitala.report

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves the PDF where the patient can find it: Downloads/Vitala (Android 10+). */
object ReportSaver {
    data class Saved(val uri: Uri?, val location: String)

    fun save(context: Context, fileName: String, write: (java.io.OutputStream) -> Unit): Saved {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Vitala")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Couldn't create the file in Downloads")
            try {
                resolver.openOutputStream(uri)?.use(write) ?: error("Couldn't open the file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return Saved(uri, "Downloads › Vitala › $fileName")
        }
        // Android 8-9: app's own Documents folder (no storage permission needed).
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, fileName)
        file.outputStream().use(write)
        return Saved(null, file.absolutePath)
    }
}
