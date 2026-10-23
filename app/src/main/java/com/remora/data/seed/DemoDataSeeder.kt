package com.remora.data.seed

import com.remora.BuildConfig
import com.remora.core.model.contract.VectorMath
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import timber.log.Timber
import kotlin.math.sin

object DemoDataSeeder {

    /**
     * Computes a fully deterministic, non-random sinusoidal basis vector from seed text hash,
     * normalized with L2 norm to guarantee reproducible cosine similarity / dot product.
     */
    private fun deterministicVector(seedText: String): FloatArray {
        val floats = FloatArray(768)
        val hash = seedText.hashCode()
        for (i in 0 until 768) {
            val angle = (i * 0.17 + (hash % 100) * 0.31).toFloat()
            floats[i] = sin(angle)
        }
        return VectorMath.l2Normalize(floats)
    }

    suspend fun seedDemoCardsIfEmpty(db: RemoraDatabase) {
        // Enforce: only run in debug builds
        if (!BuildConfig.DEBUG) {
            Timber.i("DemoDataSeeder skipped: not a debug build")
            return
        }

        val existingCount = db.memoryCardDao().getCardCount()
        if (existingCount > 0) {
            Timber.i("Database already has $existingCount cards, skipping demo seeding.")
            return
        }

        Timber.i("Pre-seeding canonical demo cards with real asset images for determinism...")

        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        val demoCards = listOf(
            MemoryCard(
                id = 1,
                imageUri = "file:///android_asset/01_math_pythagoras.png",
                createdAt = now - (oneDay * 20),
                userIntent = "Công thức cần nhớ thi giữa kỳ",
                title = "Định lý Pythagoras trong tam giác vuông",
                summary = "Trong tam giác vuông, bình phương độ dài cạnh huyền bằng tổng bình phương độ dài hai cạnh góc vuông: a^2 + b^2 = c^2.",
                tags = CardTags(subject = "Hình học", topics = listOf("TamGiácVuông", "Pythagoras", "ĐịnhLý")),
                contentType = ContentType.THEORY,
                ocrText = "Chương 1: Tam giác. Định lý Pythagoras. Cho tam giác ABC vuông tại A: BC^2 = AB^2 + AC^2.",
                embedding = deterministicVector("Định lý Pythagoras trong tam giác vuông a^2 + b^2 = c^2 Hình học"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 2,
                imageUri = "file:///android_asset/02_math_thales.png",
                createdAt = now - (oneDay * 14),
                userIntent = "Slide định lý Thales thầy giảng trên lớp",
                title = "Định lý Thales và hệ quả trong tam giác",
                summary = "Nếu một đường thẳng song song với một cạnh của tam giác và cắt hai cạnh còn lại thì nó định ra trên hai cạnh đó những đoạn thẳng tương ứng tỉ lệ.",
                tags = CardTags(subject = "Hình học", topics = listOf("ĐịnhLýThales", "TamGiác", "TỉLệ")),
                contentType = ContentType.THEORY,
                ocrText = "Định lý Thales: d // BC cắt AB, AC tại D, E => AD/AB = AE/AC = DE/BC.",
                embedding = deterministicVector("Định lý Thales và hệ quả trong tam giác tỉ lệ song song Hình học"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 3,
                imageUri = "file:///android_asset/03_physics_newton2.png",
                createdAt = now - (oneDay * 2),
                userIntent = "Định luật 2 Newton vật lý đại cương",
                title = "Định luật II Newton và phương trình động lực học",
                summary = "Gia tốc của một vật cùng hướng với lực tác dụng và có độ lớn tỉ lệ thuận với độ lớn của lực, tỉ lệ nghịch với khối lượng của vật: F = m*a.",
                tags = CardTags(subject = "Vật lý", topics = listOf("Newton", "ĐộngLựcHọc", "Lực")),
                contentType = ContentType.THEORY,
                ocrText = "Định luật 2 Newton: F_vecto = m * a_vecto. Tổng hợp lực F = dp/dt.",
                embedding = deterministicVector("Định luật II Newton phương trình động lực học F = m*a Vật lý"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 4,
                imageUri = "file:///android_asset/04_calculus_derivative.png",
                createdAt = now - (oneDay * 8),
                userIntent = "Bảng đạo hàm các hàm số cơ bản",
                title = "Bảng đạo hàm cơ bản và quy tắc chuỗi hàm hợp",
                summary = "Đạo hàm các hàm số đa thức, hàm lượng giác, hàm mũ và logarithm. Quy tắc đạo hàm tích u*v và thương u/v.",
                tags = CardTags(subject = "Giải tích", topics = listOf("ĐạoHàm", "GiảiTích", "HàmSố")),
                contentType = ContentType.THEORY,
                ocrText = "(x^n)' = n*x^(n-1); (sin x)' = cos x; (e^x)' = e^x; (ln x)' = 1/x.",
                embedding = deterministicVector("Bảng đạo hàm cơ bản quy tắc chuỗi hàm hợp Giải tích"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 5,
                imageUri = "file:///android_asset/05_prob_normal_dist.png",
                createdAt = now - (oneDay * 18),
                userIntent = "Slide ôn tập thi xác suất thống kê",
                title = "Phân phối chuẩn (Gaussian Normal Distribution)",
                summary = "Phân phối xác suất liên tục có dạng hình chuông đối xứng. Đặc trưng bởi kỳ vọng μ và phương sai σ^2. Quy tắc thực hành 68-95-99.7%.",
                tags = CardTags(subject = "Xác suất thống kê", topics = listOf("PhânPhốiChuẩn", "Gauss", "QuyTắc3Sigma")),
                contentType = ContentType.THEORY,
                ocrText = "Chương 4: Biến ngẫu nhiên liên tục. Phân phối chuẩn N(mu, sigma^2). f(x) = 1/(sigma*sqrt(2pi)) * e^(-(x-mu)^2 / (2*sigma^2)).",
                embedding = deterministicVector("Phân phối chuẩn Gaussian Normal Distribution slide phân phối chuẩn Xác suất thống kê"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 6,
                imageUri = "file:///android_asset/06_cs_binary_search.png",
                createdAt = now - (oneDay * 5),
                userIntent = "Code thuật toán tìm kiếm nhị phân",
                title = "Thuật toán tìm kiếm nhị phân (Binary Search)",
                summary = "Thuật toán tìm kiếm trên mảng đã sắp xếp với độ phức tạp thời gian O(log N). Chia đôi khoảng tìm kiếm sau mỗi bước lặp.",
                tags = CardTags(subject = "Khoa học máy tính", topics = listOf("ThuậtToán", "BinarySearch", "CấuTrúcDữLiệu")),
                contentType = ContentType.CODE,
                ocrText = "int binarySearch(int arr[], int l, int r, int x) { while (l <= r) { int m = l + (r - l) / 2; if (arr[m] == x) return m; ... } }",
                embedding = deterministicVector("Thuật toán tìm kiếm nhị phân Binary Search O(log N) Khoa học máy tính"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 7,
                imageUri = "file:///android_asset/07_chem_periodic_law.png",
                createdAt = now - (oneDay * 11),
                userIntent = "Bảng tuần hoàn Men-đê-lê-ép ôn thi Hóa",
                title = "Định luật tuần hoàn và bảng hệ thống tuần hoàn Men-đê-lê-ép",
                summary = "Tính chất của các đơn chất cũng như dạng và tính chất của các hợp chất của các nguyên tố biến đổi tuần hoàn theo điện tích hạt nhân nguyên tử.",
                tags = CardTags(subject = "Hóa học", topics = listOf("BảngTuầnHoàn", "ĐịnhLuậtTuầnHoàn", "NguyênTố")),
                contentType = ContentType.THEORY,
                ocrText = "Định luật Men-đê-lê-ép: Các nguyên tố xếp theo chiều tăng dần số proton. Độ âm điện tăng dần theo chu kỳ.",
                embedding = deterministicVector("Định luật tuần hoàn bảng hệ thống tuần hoàn Men-đê-lê-ép Hóa học"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 8,
                imageUri = "file:///android_asset/08_cs_quicksort.png",
                createdAt = now - (oneDay * 6),
                userIntent = "Giải thuật sắp xếp nhanh QuickSort",
                title = "Thuật toán sắp xếp nhanh (QuickSort)",
                summary = "Thuật toán chia để trị, chọn phần tử chốt (pivot) và phân hoạch mảng. Độ phức tạp trung bình O(N log N).",
                tags = CardTags(subject = "Khoa học máy tính", topics = listOf("QuickSort", "ChiaĐểTrị", "SắpXếp")),
                contentType = ContentType.CODE,
                ocrText = "void quickSort(int arr[], int low, int high) { int pi = partition(arr, low, high); quickSort(arr, low, pi - 1); ... }",
                embedding = deterministicVector("Thuật toán sắp xếp nhanh QuickSort chia để trị O(N log N) Khoa học máy tính"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 9,
                imageUri = "file:///android_asset/09_algebra_matrix.png",
                createdAt = now - (oneDay * 16),
                userIntent = "Công thức tính định thức ma trận",
                title = "Phép nhân ma trận và tính định thức cấp 2",
                summary = "Điều kiện để hai ma trận nhân được với nhau. Công thức tính định thức ma trận vuông 2x2: det(A) = a*d - b*c.",
                tags = CardTags(subject = "Đại số tuyến tính", topics = listOf("MaTrận", "ĐịnhThức", "ĐạiSố")),
                contentType = ContentType.THEORY,
                ocrText = "Ma trận A 2x2: [a, b; c, d] => det(A) = ad - bc. Phép nhân ma trận C_ij = sum(A_ik * B_kj).",
                embedding = deterministicVector("Phép nhân ma trận định thức cấp 2 det(A) = ad - bc Đại số tuyến tính"),
                status = ProcessingStatus.DONE
            ),
            MemoryCard(
                id = 10,
                imageUri = "file:///android_asset/10_biology_dna.png",
                createdAt = now - (oneDay * 22),
                userIntent = "Cấu trúc xoắn kép phân tử ADN",
                title = "Mô hình cấu trúc xoắn kép ADN (Watson - Crick)",
                summary = "Phân tử ADN gồm hai chuỗi polynucleotide xoắn song song ngược chiều. Nguyên tắc bổ sung A liên kết với T bằng 2 liên kết Hydro, G liên kết với X bằng 3 liên kết Hydro.",
                tags = CardTags(subject = "Sinh học", topics = listOf("ADN", "DiTruyền", "NguyênTắcBổSung")),
                contentType = ContentType.THEORY,
                ocrText = "Nguyên tắc bổ sung: A-T (2 lk H), G-X (3 lk H). Đường kính xoắn 2nm, chiều cao mỗi chu kỳ xoắn 3.4nm (10 cặp nu).",
                embedding = deterministicVector("Cấu trúc xoắn kép ADN Watson Crick nguyên tắc bổ sung Sinh học"),
                status = ProcessingStatus.DONE
            )
        )

        for (card in demoCards) {
            db.memoryCardDao().insertCard(MemoryCardEntity.fromDomainModel(card))
        }

        Timber.i("Successfully pre-seeded ${demoCards.size} canonical demo cards with real assets.")
    }
}
