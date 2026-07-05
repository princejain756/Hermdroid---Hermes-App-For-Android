package com.princejain.hermroid.automation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileCompressor(private val context: Context) {
    fun compress(uris: List<Uri>): Uri {
        require(uris.isNotEmpty()) { "Choose at least one file" }
        val directory = File(context.cacheDir, "hermroid").apply { mkdirs() }
        directory.listFiles()?.forEach { if (it.isFile) it.delete() }
        val output = File(directory, "hermroid-files-${System.currentTimeMillis()}.zip")
        val usedNames = mutableSetOf<String>()

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            uris.forEachIndexed { index, uri ->
                val baseName = displayName(uri).sanitizeFileName().ifBlank { "file-${index + 1}" }
                val name = uniqueName(baseName, usedNames)
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Unable to read $baseName")
                zip.putNextEntry(ZipEntry(name))
                input.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return uri.lastPathSegment.orEmpty()
    }
}

private fun String.sanitizeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180)

private fun uniqueName(name: String, used: MutableSet<String>): String {
    if (used.add(name)) return name
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val suffix = if (dot > 0) name.substring(dot) else ""
    var index = 2
    while (!used.add("$stem-$index$suffix")) index++
    return "$stem-$index$suffix"
}
