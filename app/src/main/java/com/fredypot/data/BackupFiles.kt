package com.fredypot.data

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException

/** Reads and writes backup files the user picked through the system file dialog. */
class BackupFiles(private val contentResolver: ContentResolver) {

    fun write(uri: Uri, content: String) {
        val stream = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        stream.bufferedWriter().use { it.write(content) }
    }

    fun read(uri: Uri): String {
        val stream = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        return stream.bufferedReader().use { it.readText() }
    }
}
