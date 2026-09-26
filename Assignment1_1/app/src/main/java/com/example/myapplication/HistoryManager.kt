package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryManager(private val context: Context) {
    private val historyFile: File get() = File(context.filesDir, "calculator_history.txt")
    private val inMemoryHistory = mutableListOf<String>()

    init {
        loadFromInternalStorage()
    }

    private fun loadFromInternalStorage() {
        if (historyFile.exists()) {
            val content = historyFile.readText()
            if (content.isNotBlank()) {
                inMemoryHistory.clear()
                inMemoryHistory.addAll(content.split("\n").filter { it.isNotBlank() })
            }
        }
    }

    fun saveToHistory(expression: String, result: String) {
        val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "$timestamp: $expression = $result"
        inMemoryHistory.add(entry)
        // Note: We don't write to historyFile here, as per your request to save only on button click.
    }

    fun loadHistory(): String {
        return inMemoryHistory.joinToString("\n")
    }

    fun getLastEntries(count: Int = 5): String {
        if (inMemoryHistory.isEmpty()) return ""
        val lastEntries = inMemoryHistory.takeLast(count)
        return lastEntries.joinToString("\n") { line ->
            line.substringAfter(": ")
        }
    }

    fun clearHistory() {
        inMemoryHistory.clear()
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }

    fun exportToDownloads(): Boolean {
        return try {
            // First, write the in-memory history to the app's internal file
            historyFile.writeText(inMemoryHistory.joinToString("\n"))

            val fileName = "calculator_history.txt"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                
                // Check if file already exists
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                
                val cursor = resolver.query(contentUri, projection, selection, selectionArgs, null)
                val uri = if (cursor != null && cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    cursor.close()
                    android.content.ContentUris.withAppendedId(contentUri, id)
                } else {
                    cursor?.close()
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    resolver.insert(contentUri, contentValues)
                }

                uri?.let {
                    resolver.openOutputStream(it, "wt")?.use { outputStream -> // "wt" for write and truncate
                        historyFile.inputStream().copyTo(outputStream)
                    }
                    true
                } ?: false
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val exportFile = File(downloadsDir, fileName)
                historyFile.copyTo(exportFile, overwrite = true)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}