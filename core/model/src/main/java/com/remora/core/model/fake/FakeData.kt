package com.remora.core.model.fake

import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.Screenshot
import java.util.Random

object FakeData {

    private fun generateMockEmbedding(seedText: String): FloatArray {
        val random = Random(seedText.hashCode().toLong())
        val vector = FloatArray(768) { random.nextFloat() * 2f - 1f }
        return vector
    }

    val sampleScreenshots: List<Screenshot> = listOf(
        Screenshot(
            id = 1,
            imageUri = "file:///android_asset/sample_normal_dist.png",
            capturedAt = System.currentTimeMillis() - 86400000L * 3, // 3 ngày trước
            userIntent = "Ôn thi giữa kỳ",
            rawOcrText = "Chương 3: Phân phối chuẩn Gauss. Hàm mật độ xác suất f(x) = 1/(sigma*sqrt(2pi)) * e^(-(x-mu)^2/(2sigma^2)). Đường cong hình chuông.",
            title = "Slide 22: Phân phối chuẩn (Normal Distribution)",
            summary = "Định nghĩa phân phối Gauss, hàm mật độ xác suất hình chuông đối xứng qua kỳ vọng μ và độ lệch chuẩn σ.",
            subject = "Xác Suất Thống Kê",
            tags = listOf("XSTK", "PhânPhốiChuẩn", "Gauss", "LýThuyết"),
            contentType = ContentType.THEORY,
            embedding = generateMockEmbedding("Phân phối chuẩn Gauss Xác Suất Thống Kê"),
            status = ProcessingStatus.READY
        ),
        Screenshot(
            id = 2,
            imageUri = "file:///android_asset/sample_prob_density.png",
            capturedAt = System.currentTimeMillis() - 86400000L * 7, // 1 tuần trước
            userIntent = "Công thức cần nhớ",
            rawOcrText = "Biến ngẫu nhiên liên tục. Hàm mật độ xác suất f(x) >= 0. Tích phân từ -vô cùng đến +vô cùng bằng 1. E(X) = int(x*f(x)dx).",
            title = "Slide 14: Hàm mật độ xác suất f(x)",
            summary = "Khái niệm biến ngẫu nhiên liên tục, tính chất tích phân hàm mật độ và công thức tính kỳ vọng toán học.",
            subject = "Xác Suất Thống Kê",
            tags = listOf("XSTK", "BiếnNgẫuNhiên", "CôngThức"),
            contentType = ContentType.THEORY,
            embedding = generateMockEmbedding("Hàm mật độ xác suất Biến ngẫu nhiên liên tục"),
            status = ProcessingStatus.READY
        ),
        Screenshot(
            id = 3,
            imageUri = "file:///android_asset/sample_exercise.png",
            capturedAt = System.currentTimeMillis() - 86400000L * 10, // 10 ngày trước
            userIntent = "Bài tập về nhà thầy cho",
            rawOcrText = "Bài 3.12: Cho biến ngẫu nhiên X tuân theo phân phối chuẩn N(10, 4). Hãy tính xác suất để X nhận giá trị trong khoảng (8, 12).",
            title = "Đề bài tập XSTK: Tính xác suất phân phối chuẩn",
            summary = "Đề bài tính xác suất của biến ngẫu nhiên trong khoảng xác định, yêu cầu chuẩn hóa về biến chuẩn Z.",
            subject = "Xác Suất Thống Kê",
            tags = listOf("XSTK", "BàiTập", "ĐềThi"),
            contentType = ContentType.PROBLEM,
            embedding = generateMockEmbedding("Đề bài tập phân phối chuẩn N(10,4)"),
            status = ProcessingStatus.READY
        ),
        Screenshot(
            id = 4,
            imageUri = "file:///android_asset/sample_solution_gauss.png",
            capturedAt = System.currentTimeMillis() - 86400000L * 14, // 2 tuần trước
            userIntent = "Cách tra bảng Z score mẫu",
            rawOcrText = "Lời giải: Đặt Z = (X - mu)/sigma. Với mu=10, sigma=2, ta có P(8 < X < 12) = P(-1 < Z < 1) = 2*Phi(1) - 1 = 0.6826.",
            title = "Bài giải mẫu Gauss: Kỹ thuật đổi biến Z-Score",
            summary = "Phương pháp chuẩn hóa biến ngẫu nhiên và kỹ thuật tra bảng hàm Laplace Phi(z) để tính xác suất hình chuông.",
            subject = "Xác Suất Thống Kê",
            tags = listOf("XSTK", "BàiGiải", "ZScore", "MẹoGiải"),
            contentType = ContentType.SOLUTION,
            embedding = generateMockEmbedding("Bài giải mẫu tra bảng Z score Gauss"),
            status = ProcessingStatus.READY
        ),
        Screenshot(
            id = 5,
            imageUri = "file:///android_asset/sample_calculus.png",
            capturedAt = System.currentTimeMillis() - 86400000L * 20, // 20 ngày trước
            userIntent = "Slide đạo hàm riêng giải tích",
            rawOcrText = "Giải tích 2: Đạo hàm riêng cấp 1. df/dx và df/dy. Định lý Schwarz về tính liên tục của đạo hàm hỗn hợp.",
            title = "Giải tích 2: Đạo hàm riêng và vi phân toàn phần",
            summary = "Lý thuyết đạo hàm riêng hàm nhiều biến, vi phân cấp 1 và định lý Schwarz về đạo hàm hỗn hợp.",
            subject = "Giải Tích",
            tags = listOf("GiảiTích", "ĐạoHàmRiêng", "ToánCaoCấp"),
            contentType = ContentType.THEORY,
            embedding = generateMockEmbedding("Giải tích đạo hàm riêng vi phân toàn phần"),
            status = ProcessingStatus.READY
        )
    )
}
