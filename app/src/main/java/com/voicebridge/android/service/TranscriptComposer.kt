package com.voicebridge.android.service

import java.util.Locale

/**
 * ASR 解码窗口（由 VAD 语音区间打包而成，边界只落在静音处）
 */
data class ASRWindow(
    val start: Double,
    val end: Double,
    val gapAfter: Double
)

/**
 * 转录窗口文本 + 时间元数据（组装输入）
 */
data class TranscriptWindow(
    val text: String,
    val start: Double,
    val end: Double,
    val gapAfter: Double
)

/**
 * 组装完成的文章段落
 */
data class ComposedParagraph(
    val text: String,
    val start: Double
)

/**
 * 文章化组装纯逻辑 — VAD 窗口打包、标点剥离/恢复对齐、切句、智能分段
 * 迁移自 iOS 侧 TranscriptComposer.swift
 */
object TranscriptComposer {

    private val sentenceEnders = setOf('。', '！', '？', '!', '?', '…', '；', ';')

    /**
     * 把 VAD 语音区间打包为解码窗口。
     * 规则：间隙 >= strongGap 立即分界（天然段落信号）；窗口跨度超过 maxSpan 时
     * 回溯到窗口内最后一个 >= minCutGap 的间隙处分界；实在没有可用间隙才就地硬切。
     */
    fun packWindows(
        regions: List<Pair<Double, Double>>,
        maxSpan: Double = 28.0,
        strongGap: Double = 2.0,
        minCutGap: Double = 0.25
    ): List<ASRWindow> {
        if (regions.isEmpty()) return emptyList()
        val windows = ArrayList<ASRWindow>()
        var current = ArrayList<Pair<Double, Double>>()
        current.add(regions[0])

        fun close(regs: List<Pair<Double, Double>>, gapAfter: Double) {
            val f = regs.firstOrNull() ?: return
            val l = regs.lastOrNull() ?: return
            windows.add(ASRWindow(f.first, l.second, gapAfter))
        }

        for (i in 1 until regions.size) {
            val region = regions[i]
            val gap = region.first - current[current.size - 1].second
            if (gap >= strongGap) {
                close(current, gap)
                current = ArrayList()
                current.add(region)
                continue
            }
            current.add(region)
            
            // 超长回溯：优先在最后一个 >= minCutGap 的真实间隙处分界
            while (current.size > 1 && (current[current.size - 1].second - current[0].first) > maxSpan) {
                var cutIdx = current.size - 2 // 兜底：在最后一个区间前硬切
                for (j in (current.size - 2) downTo 0) {
                    if (current[j + 1].first - current[j].second >= minCutGap) {
                        cutIdx = j
                        break
                    }
                }
                val cutGap = current[cutIdx + 1].first - current[cutIdx].second
                close(current.subList(0, cutIdx + 1), cutGap)
                current = ArrayList(current.subList(cutIdx + 1, current.size))
            }
        }
        close(current, 0.0)
        return windows
    }

    /**
     * 剥掉 ASR 自带的零散标点（统一重排前清理）；保留字符/数字，空白折叠为单空格
     */
    fun stripPunctuation(text: String): String {
        val out = StringBuilder()
        for (ch in text) {
            if (ch.isLetter() || ch.isDigit()) {
                out.append(ch)
            } else if (ch.isWhitespace()) {
                out.append(' ')
            }
        }
        return out.toString().split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /**
     * 把带标点文本切成句子（保留句尾标点，尾部无标点的残句单独成句）
     */
    fun splitSentences(text: String): List<String> {
        val sentences = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in text) {
            cur.append(ch)
            if (ch in sentenceEnders) {
                val t = cur.toString().trim()
                if (t.isNotEmpty()) sentences.add(t)
                cur.clear()
            }
        }
        val tail = cur.toString().trim()
        if (tail.isNotEmpty()) sentences.add(tail)
        return sentences
    }

    /**
     * 对整批窗口做全文标点恢复，再按原始字符归属拆回每个窗口。
     * 分批规则：单批 <= maxBatchChars，批边界只落在窗口边界。任一环节失败返回 null。
     */
    fun restorePunctuation(
        pieces: List<String>,
        maxBatchChars: Int = 1800,
        punctuate: (String) -> String?
    ): List<String>? {
        val result = ArrayList<String>()
        var batch = ArrayList<String>()
        var batchLen = 0

        fun flushBatch(): Boolean {
            if (batch.isEmpty()) return true
            val joined = batch.joinToString(" ")
            val punctuated = punctuate(joined) ?: return false
            val split = splitBack(punctuated, batch) ?: return false
            result.addAll(split)
            batch = ArrayList()
            batchLen = 0
            return true
        }

        for (piece in pieces) {
            if (batchLen + piece.length > maxBatchChars) {
                if (!flushBatch()) return null
            }
            batch.add(piece)
            batchLen += piece.length + 1
        }
        if (!flushBatch()) return null
        return result
    }

    /**
     * 双指针对齐：标点结果 = 原文字符序列 + 插入的标点/空白。
     * 逐一在 punctuated 中按序匹配 raw 字符，不匹配的非字母字符视为插入标点。
     * 模型改写了原文（出现原文没有的字母/数字）或对齐耗尽时返回 null。
     */
    fun splitBack(punctuated: String, rawPieces: List<String>): List<String>? {
        val pChars = punctuated.toCharArray()
        var pIdx = 0
        val output = ArrayList<String>()

        for (pieceIdx in rawPieces.indices) {
            val piece = rawPieces[pieceIdx]
            val cur = StringBuilder()
            val rChars = piece.filter { it != ' ' }.toCharArray()
            var rIdx = 0
            while (rIdx < rChars.size) {
                if (pIdx >= pChars.size) return null
                val pc = pChars[pIdx]
                if (pc == rChars[rIdx] || pc.lowercaseChar() == rChars[rIdx].lowercaseChar()) {
                    cur.append(pc)
                    rIdx++
                    pIdx++
                } else if (pc.isLetter() || pc.isDigit()) {
                    return null
                } else {
                    cur.append(pc)
                    pIdx++
                }
            }
            if (pieceIdx == rawPieces.size - 1) {
                while (pIdx < pChars.size) {
                    cur.append(pChars[pIdx])
                    pIdx++
                }
            } else {
                // 尾随标点归属当前窗口，直到遇见下一个原文字符
                while (pIdx < pChars.size && !(pChars[pIdx].isLetter() || pChars[pIdx].isDigit())) {
                    cur.append(pChars[pIdx])
                    pIdx++
                }
            }
            output.add(cur.toString().trim())
        }
        return output
    }

    /**
     * 标点模型不可用时的修补：压缩连续重复标点；强边界（长停顿/收尾）处补句号
     */
    fun fallbackPunctuate(text: String, isStrongBoundary: Boolean): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val out = StringBuilder()
        var prev: Char? = null
        for (ch in trimmed) {
            if (prev != null && prev == ch && !(ch.isLetter() || ch.isDigit() || ch == ' ')) {
                continue
            }
            out.append(ch)
            prev = ch
        }
        if (isStrongBoundary && out.isNotEmpty() && out.last() !in sentenceEnders) {
            val hasChinese = out.any { it.code > 127 }
            out.append(if (hasChinese) "。" else ".")
        }
        return out.toString()
    }

    /**
     * 组装文章段落。
     */
    fun compose(
        windows: List<TranscriptWindow>,
        punctuate: ((String) -> String?)?,
        maxParagraphChars: Int = 180,
        paragraphGap: Double = 2.0
    ): List<ComposedParagraph> {
        val nonEmpty = windows.filter { stripPunctuation(it.text).isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()

        // 1. 每窗口剥标点
        val rawPieces = nonEmpty.map { stripPunctuation(it.text) }

        // 2. 全文标点恢复并按窗口拆回；失败走 3 降级
        var punctuatedPieces: List<String>? = null
        if (punctuate != null) {
            punctuatedPieces = restorePunctuation(rawPieces, punctuate = punctuate)
        }

        // 3. 降级：保留 ASR 自带标点 + 规则修补
        val finalPieces: List<String> = if (punctuatedPieces != null) {
            punctuatedPieces
        } else {
            nonEmpty.mapIndexed { idx, w ->
                fallbackPunctuate(
                    w.text,
                    isStrongBoundary = w.gapAfter >= paragraphGap || idx == nonEmpty.size - 1
                )
            }
        }

        // 4. 逐窗口切句 -> 带时间锚点的句子流
        data class AnchoredSentence(
            val text: String,
            val start: Double,
            val gapAfter: Double
        )
        val sentences = ArrayList<AnchoredSentence>()
        for (idx in finalPieces.indices) {
            val w = nonEmpty[idx]
            val piece = finalPieces[idx]
            val parts = splitSentences(piece)
            for (sIdx in parts.indices) {
                val s = parts[sIdx]
                val isLast = sIdx == parts.size - 1
                sentences.add(AnchoredSentence(s, w.start, if (isLast) w.gapAfter else 0.0))
            }
        }

        // 5. 组段：长度达标后在句尾分段；长停顿强制分段
        val paragraphs = ArrayList<ComposedParagraph>()
        var curText = ""
        var curStart: Double? = null
        for (s in sentences) {
            if (curStart == null) curStart = s.start
            curText += joinToken(curText, s.text)
            if (curText.length >= maxParagraphChars || s.gapAfter >= paragraphGap) {
                paragraphs.add(ComposedParagraph(curText, curStart))
                curText = ""
                curStart = null
            }
        }
        if (curText.isNotEmpty() && curStart != null) {
            paragraphs.add(ComposedParagraph(curText, curStart))
        }
        return paragraphs
    }

    /**
     * 句子拼接：英文句子之间补空格，中文直接相连
     */
    private fun joinToken(current: String, next: String): String {
        if (current.isEmpty()) return next
        val lastIsASCII = current.last().code in 0..127
        val firstIsASCII = next.first().code in 0..127
        return if (lastIsASCII && firstIsASCII) "$current $next" else "$current$next"
    }
}
