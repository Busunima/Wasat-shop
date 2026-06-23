package com.wasat.shop.feature.orders

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Передача CSV-экспорта (FR-A05) в системный share sheet: файл пишется в
 * cache/exports (см. res/xml/file_paths.xml), URI выдаётся через FileProvider
 * с временным правом чтения для принимающего приложения.
 */
object CsvShare {
    fun share(context: Context, csv: String, fileName: String, mime: String = "text/csv") {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, fileName))
    }
}
