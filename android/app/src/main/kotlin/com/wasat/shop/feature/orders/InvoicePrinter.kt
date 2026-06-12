package com.wasat.shop.feature.orders

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Печать HTML-инвойса (FR-A04) в PDF через системный фреймворк печати Android.
 * WebView рендерит документ; по завершении загрузки PrintManager открывает диалог,
 * где пользователь сохраняет PDF либо отправляет на принтер. Ссылку на WebView держим
 * до старта печати, чтобы адаптер не был уничтожен сборщиком мусора.
 */
object InvoicePrinter {
    private var pending: WebView? = null

    fun print(context: Context, html: String, jobName: String) {
        val view = WebView(context)
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(page: WebView, url: String) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(
                    jobName,
                    page.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().build(),
                )
                pending = null
            }
        }
        view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        pending = view
    }
}
