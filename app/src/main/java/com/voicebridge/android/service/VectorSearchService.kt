package com.voicebridge.android.service

/**
 * 向量检索引擎
 * 职责：
 * 1. 在内存中高效计算两个 Float 向量的点积 (Dot Product)
 * 2. 计算两个 Float 向量的余弦相似度 (Cosine Similarity)
 * 3. 批量检索最匹配的 Top-K 条目并降序排序
 */
object VectorSearchService {

    /**
     * 计算两个向量的点积
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var result = 0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        return result
    }

    /**
     * 计算两个向量的余弦相似度
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var aSqr = 0f
        var bSqr = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            aSqr += a[i] * a[i]
            bSqr += b[i] * b[i]
        }
        val denom = Math.sqrt(aSqr.toDouble()) * Math.sqrt(bSqr.toDouble())
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }

    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        return cosineSimilarity(a.toFloatArray(), b.toFloatArray())
    }

    private fun List<Float>.toFloatArray(): FloatArray {
        val arr = FloatArray(this.size)
        for (i in this.indices) {
            arr[i] = this[i]
        }
        return arr
    }

    /**
     * 批量计算并排序，返回最相似的前 N 个条目
     */
    fun <T> search(
        query: FloatArray,
        items: List<T>,
        extractEmbedding: (T) -> FloatArray?,
        topK: Int = 10,
        isNormalized: Boolean = false
    ): List<Pair<T, Float>> {
        val results = ArrayList<Pair<T, Float>>()
        for (item in items) {
            val emb = extractEmbedding(item) ?: continue
            if (emb.size != query.size) continue
            
            val score = if (isNormalized) {
                dotProduct(query, emb)
            } else {
                cosineSimilarity(query, emb)
            }
            results.add(Pair(item, score))
        }
        
        results.sortByDescending { it.second }
        return results.take(topK)
    }
}
