package com.voicebridge.android.service

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 会议纪要 PDF 打印导出服务
 * 对应 iOS 侧 PDFExportService.swift
 * 职责：
 * 1. 采用 WebView 离屏渲染 HTML CSS 排版模版
 * 2. 调起系统 PrintManager 导出标准 A4 高保真会议纪要 PDF
 */
object PDFExportService {

    /**
     * 将会议纪要导出为 A4 格式 PDF 并调起系统分享打印面板
     * @param context 上下文
     * @param title 会议名称
     * @param segments 纪要段落
     */
    fun export(
        context: Context,
        title: String,
        segments: List<Pair<String, String>> // List of Pair(SpeakerLabel, ParagraphText)
    ) {
        val html = buildHTMLTemplate(title, segments)
        
        // 主线程实例化离屏 WebView
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val originalAdapter = webView.createPrintDocumentAdapter("VoiceBridge-$title")
                
                // 包装代理 Adapter 以拦截生命周期，确保在打印渲染完成后释放 WebView 堆外内存
                val printAdapter = object : android.print.PrintDocumentAdapter() {
                    override fun onStart() {
                        originalAdapter.onStart()
                    }
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: android.os.Bundle?
                    ) {
                        originalAdapter.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
                    }
                    override fun onWrite(
                        pages: Array<out android.print.PageRange>?,
                        destination: android.os.ParcelFileDescriptor?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        originalAdapter.onWrite(pages, destination, cancellationSignal, callback)
                    }
                    override fun onFinish() {
                        originalAdapter.onFinish()
                        webView.post {
                            try {
                                webView.destroy()
                            } catch (e: Exception) {
                                // 优雅降级忽略
                            }
                        }
                    }
                }
                
                // 配置标准 A4 页面与 300DPI 分辨率
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf_print", "PDF", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                
                printManager.print("VoiceBridge-$title", printAdapter, attributes)
            }
        }
        
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun buildHTMLTemplate(title: String, segments: List<Pair<String, String>>): String {
        val bodyBuilder = StringBuilder()
        for (seg in segments) {
            val label = seg.first
            val text = seg.second
            bodyBuilder.append("""
                <div class="segment">
                    ${if (label.isNotEmpty()) "<div class=\"speaker\">$label</div>" else ""}
                    <div class="text">$text</div>
                </div>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Helvetica Neue', Arial, sans-serif;
                        color: #333333;
                        line-height: 1.6;
                        padding: 40px;
                        background: #ffffff;
                    }
                    h1 {
                        font-size: 24px;
                        color: #111111;
                        border-bottom: 2px solid #eeeeee;
                        padding-bottom: 12px;
                        margin-bottom: 30px;
                    }
                    .segment {
                        margin-bottom: 20px;
                        page-break-inside: avoid;
                    }
                    .speaker {
                        font-weight: bold;
                        color: #007AFF;
                        font-size: 14px;
                        margin-bottom: 4px;
                    }
                    .text {
                        font-size: 15px;
                        color: #222222;
                    }
                    @media print {
                        body {
                            padding: 0;
                        }
                    }
                </style>
            </head>
            <body>
                <h1>$title — 会议纪要</h1>
                <div class="content">
                    $bodyBuilder
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
