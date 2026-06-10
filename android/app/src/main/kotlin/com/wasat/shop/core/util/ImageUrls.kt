package com.wasat.shop.core.util

/**
 * Соглашение имён миниатюр расширения Storage Resize Images (docs/decisions.md):
 * рядом с оригиналом создаётся <имя>_<WxH>.webp. В download-URL Firebase Storage
 * путь URL-кодирован (%2F), имя файла — последний сегмент перед query.
 * Pure JVM — тестируется юнитами.
 */
object ImageUrls {
    const val THUMB = "200x200"
    const val MEDIUM = "800x800"

    /**
     * URL миниатюры по соглашению; вызывающий обязан откатываться на [url],
     * если миниатюра ещё не сгенерирована (расширение не развёрнуто/в процессе).
     */
    fun thumbnailUrl(url: String, size: String = THUMB): String {
        val queryStart = url.indexOf('?')
        val base = if (queryStart >= 0) url.substring(0, queryStart) else url
        // token из query привязан к оригиналу и для миниатюры невалиден; медиа витрины
        // публичны по Storage Rules, поэтому достаточно alt=media.
        val query = if (queryStart >= 0) "?alt=media" else ""

        // Последний path-сегмент: учитываем и '/', и URL-кодированный '%2F'
        val lastSlash = maxOf(base.lastIndexOf('/'), base.lastIndexOf("%2F").let {
            if (it >= 0) it + 2 else -1
        })
        if (lastSlash < 0) return url
        val dir = base.substring(0, lastSlash + 1)
        val fileName = base.substring(lastSlash + 1)
        if (fileName.isEmpty()) return url

        // Расширение оригинала отбрасывается: resize пишет <имя>_<size>.webp
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        return "$dir${stem}_$size.webp$query"
    }
}
