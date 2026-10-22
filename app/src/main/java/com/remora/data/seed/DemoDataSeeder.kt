package com.remora.data.seed

import com.remora.core.model.contract.VectorMath
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import timber.log.Timber
import java.util.Random

object DemoDataSeeder {

    private fun generateVector(seedText: String): FloatArray {
        val random = Random(seedText.hashCode().toLong())
        val floats = FloatArray(768) { random.nextFloat() * 2f - 1f }
        return VectorMath.l2Normalize(floats)
    }

    suspend fun seedDemoCardsIfEmpty(db: RemoraDatabase) {
        val existingCount = db.memoryCardDao().getCardCount()
        if (existingCount > 0) {
            Timber.i("Database already has $existingCount cards, skipping demo seeding.")
            return
        }

        Timber.i("Pre-seeding canonical demo cards for determinism...")

        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        val demoCards = listOf(
            MemoryCard(
                id = 1,
                imageUri = "file:///android_asset/demo_pythagoras.png",
                createdAt = now - (oneDay * 20), // ~3 tuần trước (hôm bữa)
                userIntent = "Công thức cần nhớ thi giữa kỳ",
                title = "Định lý Pythagoras trong tam giác vuông",
                summary = "Trong tam giác vuông, bình phương độ dài cạnh huyền bằng tổng bình phương độ dài hai cạnh góc vuông: a^2 + b^2 = c^2.",
                tags = CardTags(subject = "Hình học", topics = listOf("TamGiácVuông", "Pythagoras", "ĐịnhLý")),
                contentType = ContentType.THEORY,
                ocrText = "Chương 1: Tam giác. Định lý Pythagoras. Cho tam giác ABC vuông tại A: BC^2 = AB^2 + AC^2.",
                embedding = generateVector("Định lý Pythagoras trong tam giác vuông a^2 + b^2 = c^2 Hình học"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 2,
                imageUri = "file:///android_asset/demo_thales_theorem.png",
                createdAt = now - (oneDay * 14), // 2 tuần trước
                userIntent = "Slide định lý Thales thầy giảng trên lớp",
                title = "Định lý Thales và hệ quả trong tam giác",
                summary = "Nếu một đường thẳng song song với một cạnh của tam giác và cắt hai cạnh còn lại thì nó định ra trên hai cạnh đó những đoạn thẳng tương ứng tỉ lệ.",
                tags = CardTags(subject = "Hình học", topics = listOf("ĐịnhLýThales", "TamGiác", "TỉLệ")),
                contentType = ContentType.THEORY,
                ocrText = "Định lý Thales: d // BC cắt AB, AC tại D, E => AD/AB = AE/AC = DE/BC.",
                embedding = generateVector("Định lý Thales và hệ quả trong tam giác tỉ lệ song song Hình học"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 3,
                imageUri = "file:///android_asset/demo_thales_exercise.png",
                createdAt = now - (oneDay * 7), // tuần trước
                userIntent = "Bài tập về nhà áp dụng Thales",
                title = "Bài tập tính độ dài đoạn thẳng bằng Thales",
                summary = "Cho tam giác ABC có DE song song BC, AD=3cm, DB=2cm, AE=4.5cm. Tính độ dài EC.",
                tags = CardTags(subject = "Hình học", topics = listOf("BàiTập", "ĐịnhLýThales", "TínhĐoạnThẳng")),
                contentType = ContentType.PROBLEM,
                ocrText = "Bài 2: Cho tam giác ABC, DE // BC. Biết AD=3, DB=2, AE=4.5. Áp dụng hệ quả Thales tính EC.",
                embedding = generateVector("Bài tập tính độ dài đoạn thẳng bằng Thales Hình học BàiTập"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 4,
                imageUri = "file:///android_asset/demo_normal_dist.png",
                createdAt = now - (oneDay * 18), // ~2-3 tuần trước (hôm bữa)
                userIntent = "Slide ôn tập thi xác suất thống kê",
                title = "Phân phối chuẩn (Gaussian Normal Distribution)",
                summary = "Phân phối xác suất liên tục có dạng hình chuông đối xứng. Đặc trưng bởi kỳ vọng μ và phương sai σ^2. Quy tắc thực hành 68-95-99.7%.",
                tags = CardTags(subject = "Xác suất thống kê", topics = listOf("PhânPhốiChuẩn", "Gauss", "QuyTắc3Sigma")),
                contentType = ContentType.THEORY,
                ocrText = "Chương 4: Biến ngẫu nhiên liên tục. Phân phối chuẩn N(mu, sigma^2). f(x) = 1/(sigma*sqrt(2pi)) * e^(-(x-mu)^2 / (2*sigma^2)).",
                embedding = generateVector("Phân phối chuẩn Gaussian Normal Distribution slide phân phối chuẩn Xác suất thống kê"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 5,
                imageUri = "file:///android_asset/demo_z_score.png",
                createdAt = now - (oneDay * 10),
                userIntent = "Mẹo tra bảng Laplace Z-Score",
                title = "Kỹ thuật chuẩn hóa biến Z và tra bảng phân phối chuẩn",
                summary = "Công thức chuyển đổi biến ngẫu nhiên X về biến chuẩn Z = (X - μ) / σ để tra diện tích tích lũy dưới đường cong chuẩn.",
                tags = CardTags(subject = "Xác suất thống kê", topics = listOf("ZScore", "TraBảng", "ChuẩnHóa")),
                contentType = ContentType.SOLUTION,
                ocrText = "Đặt Z = (X - mu)/sigma ~ N(0, 1). P(a < X < b) = Phi((b-mu)/sigma) - Phi((a-mu)/sigma).",
                embedding = generateVector("Kỹ thuật chuẩn hóa biến Z tra bảng Laplace phân phối chuẩn Xác suất thống kê"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 6,
                imageUri = "file:///android_asset/demo_newton2.png",
                createdAt = now - (oneDay * 2), // hôm qua / hôm kia
                userIntent = "Định luật 2 Newton vật lý đại cương",
                title = "Định luật II Newton và phương trình động lực học",
                summary = "Gia tốc của một vật cùng hướng với lực tác dụng và có độ lớn tỉ lệ thuận với độ lớn của lực, tỉ lệ nghịch với khối lượng của vật: F = m*a.",
                tags = CardTags(subject = "Vật lý", topics = listOf("Newton", "ĐộngLựcHọc", "Lực")),
                contentType = ContentType.THEORY,
                ocrText = "Định luật 2 Newton: F_vecto = m * a_vecto. Tổng hợp lực F = dp/dt.",
                embedding = generateVector("Định luật II Newton phương trình động lực học F = m*a Vật lý"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 7,
                imageUri = "file:///android_asset/demo_binary_search.png",
                createdAt = now - (oneDay * 5),
                userIntent = "Code thuật toán tìm kiếm nhị phân",
                title = "Thuật toán tìm kiếm nhị phân (Binary Search)",
                summary = "Thuật toán tìm kiếm trên mảng đã sắp xếp với độ phức tạp thời gian O(log N). Chia đôi khoảng tìm kiếm sau mỗi bước lặp.",
                tags = CardTags(subject = "Khoa học máy tính", topics = listOf("ThuậtToán", "BinarySearch", "CấuTrúcDữLiệu")),
                contentType = ContentType.CODE,
                ocrText = "int binarySearch(int arr[], int l, int r, int x) { while (l <= r) { int m = l + (r - l) / 2; if (arr[m] == x) return m; ... } }",
                embedding = generateVector("Thuật toán tìm kiếm nhị phân Binary Search O(log N) Khoa học máy tính"),
                status = ProcessingStatus.DONE
            )
        )

        for (card in demoCards) {
            db.memoryCardDao().insertCard(MemoryCardEntity.fromDomainModel(card))
        }

        Timber.i("Successfully pre-seeded ${demoCards.size} canonical demo cards.")
    }
}
