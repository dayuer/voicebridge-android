package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 资产包定位管理器 — 对应 iOS 侧 AssetPackLocator.swift
 * 负责在文件系统各级别目录下定位 ONNX 模型（CDN/PAD下载区 -> 缓存区 -> Assets 兜底拷贝）
 */
object AssetPackLocator {
    private const val TAG = "AssetPackLocator"

    /**
     * 定位模型文件的系统绝对路径，如果本地缺失且 Assets 存在，则自动执行 Assets 部署
     */
    fun path(context: Context, relativePath: String): String? {
        val destFile = File(context.filesDir, "Models/$relativePath")
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        // 创建多级子目录
        destFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        return try {
            context.assets.open(relativePath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "从 Assets 成功部署资源: $relativePath")
            destFile.absolutePath
        } catch (e: IOException) {
            // Assets 也缺失，代表只能依赖 CDN / Play Asset Delivery (PAD)
            Log.w(TAG, "未能在 Assets 中找到资源，需等待动态下载: $relativePath")
            null
        }
    }

    /**
     * 用于自检或国内 CDN 渠道触发下载
     */
    suspend fun ensureDownloaded(
        context: Context,
        packId: String,
        onProgress: (Double) -> Unit
    ) {
        // 模拟/对标 iOS 双轨分发的后台静默拉取
        // 未来如果添加自建 CDN，在此处执行断点续传 HTTP 请求并写入 context.filesDir/Models
        onProgress(1.0)
    }
}
