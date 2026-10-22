package com.remora.core.model.fake

import com.remora.core.model.contract.TextEmbedder
import com.remora.core.model.contract.VisionAnalysisResult
import com.remora.core.model.contract.VisionAnalyzer
import com.remora.core.model.entity.ContentType
import kotlinx.coroutines.delay
import java.util.Random

class FakeVisionAnalyzer(
    private val simulatedDelayMs: Long = 800L
) : VisionAnalyzer {

    override suspend fun analyze(
        imageBytes: ByteArray,
        mimeType: String,
        userIntent: String?
    ): Result<VisionAnalysisResult> {
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)

        return Result.success(
            VisionAnalysisResult(
                title = "Slide: Phân phối nhị thức & Poisson",
                summary = "Định nghĩa phân phối nhị thức B(n, p), công thức tính xác suất Bernoulli và điều kiện xấp xỉ Poisson khi n lớn, p nhỏ.",
                subject = "Xác Suất Thống Kê",
                tags = listOf("XSTK", "NhịThức", "Poisson", "CôngThức"),
                contentType = ContentType.THEORY,
                ocrText = "Chương 2: Các quy luật phân phối xác suất rời rạc. 1. Phân phối Bernoulli. 2. Phân phối Nhị thức B(n, p). P(X=k) = C_n^k * p^k * (1-p)^(n-k)."
            )
        )
    }
}

class FakeTextEmbedder : TextEmbedder {
    override suspend fun embed(text: String): Result<FloatArray> {
        val random = Random(text.hashCode().toLong())
        val vector = FloatArray(768) { random.nextFloat() * 2f - 1f }
        return Result.success(vector)
    }
}
