package com.example.poolaim

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class ApkFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun apkFile(): File = File(context?.cacheDir, "update.apk")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = apkFile()
        if (!file.exists()) throw FileNotFoundException("update.apk")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    // شاشة تثبيت النظام تستعلم عن اسم الملف وحجمه قبل قراءته
    override fun query(uri: Uri, p1: Array<String>?, p2: String?, p3: Array<String>?, p4: String?): Cursor? {
        val file = apkFile()
        val cols = p1 ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = arrayOfNulls<Any>(cols.size)
        for ((i, col) in cols.withIndex()) {
            row[i] = when (col) {
                OpenableColumns.DISPLAY_NAME -> "update.apk"
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
