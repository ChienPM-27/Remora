package com.remora.benchmark

import com.remora.search.LexicalMatcher
import com.remora.search.ParsedQuery
import com.remora.search.QueryParser
import com.remora.search.SearchableDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Scale benchmark v2 for the on-device lexical channel (QueryMatcher) — gate evidence for
 * T033/T044 (SQLite FTS). The v1 fixture (24 cards) was too small to support the "lexical
 * channel is enough" claim, so this test scales the same measurement to 1,000 / 5,000
 * synthetic cards generated deterministically (fixed seed) from a fixed synthetic Vietnamese
 * study vocabulary. Consent-safe by construction: no real screenshots, no private gallery
 * data, no user content.
 *
 * Dataset layout per corpus size:
 *  - planted: 5 cards per planted query (10 queries) containing ALL target terms; the
 *    generator therefore knows the exact ground truth.
 *  - generic-sharing distractors: contain the generic half of a query ("dinh ly" without
 *    "bayes", "phan tich" without "tung phan", "dao ham" without "rieng", "phan phoi"
 *    without "chuan", "duong di" without "euler"). They probe the coverage gate and make
 *    precision measurable; each pool is 4% of the corpus.
 *  - near-miss distractors: share exactly one term ("sql" without "join"; "quan he" without
 *    ERD/nhieu). The 2-term "sql" pool leaks through the <=2-term half-coverage rule (1 of
 *    2 terms is enough); the 4-term ERD pool must not match (2 of 4 < the 2/3 gate).
 *  - noise: unrelated synthetic study cards sharing no query term.
 *
 * Two matcher behaviors surfaced at scale (kept deliberately as findings): the acronym
 * containment rule matches "ctdl" inside the initials of "Cong thuc Dinh ly Bayes"
 * ("ctdlb"), and single-term "SQL" cards match the 2-term query "sql join".
 *
 * Measured per size: per-query precision/recall vs planted ground truth, per-query
 * sequential in-memory scan latency (System.nanoTime, production-like full scan), P50/P95
 * over queries, and stability (3 repeated passes must return identical result sets).
 */
class LexicalScaleBenchmarkTest {

    private data class SynthCard(
        val id: Long,
        val title: String,
        val subject: String,
        val topics: List<String>,
        val intent: String,
        val summary: String,
        val ocr: String
    ) {
        fun toDoc() = SearchableDoc(
            title = title, subject = subject, topics = topics,
            intent = intent, summary = summary, ocr = ocr
        )
    }

    private data class PendingCard(
        val title: String,
        val subject: String,
        val topics: List<String>,
        val intent: String,
        val summary: String,
        val ocr: String,
        val plantedQuery: Int = -1
    )

    private data class ScaleQuery(val name: String, val query: String, val junk: Boolean = false)

    private class Corpus(val cards: List<SynthCard>, val groundTruth: Map<Int, Set<Long>>) {
        val composition: String get() = groundTruth.values.sumOf { it.size }.toString()
    }

    private class PassResult(val matched: List<Set<Long>>, val nanos: LongArray)

    private class PlantedSpec(
        val subject: String,
        val topics: List<String>,
        val titles: List<String>,
        val summary: String,
        val ocr: String,
        val intents: List<String>
    )

    private class FixedPool(
        val subjects: List<String>,
        val titles: List<String>,
        val summary: String,
        val ocr: String,
        val topics: List<String>
    ) {
        fun draw(rng: Random): PendingCard = PendingCard(
            title = titles[rng.nextInt(titles.size)],
            subject = subjects[rng.nextInt(subjects.size)],
            topics = listOf(topics[rng.nextInt(topics.size)]),
            intent = "",
            summary = summary,
            ocr = ocr
        )
    }

    private companion object {
        const val SEED = 20261026L
        const val SHUFFLE_SEED = 424242L
        const val PLANTED_PER_QUERY = 5
        const val PASSES = 3
        val CORPUS_SIZES = intArrayOf(1_000, 5_000)
    }

    private val queries = listOf(
        ScaleQuery("bayes-exact", "định lý Bayes"),
        ScaleQuery("tich-phan-folded", "tich phan tung phan"),
        ScaleQuery("ctdl-abbrev", "ctdl"),
        ScaleQuery("mmt-abbrev", "mmt"),
        ScaleQuery("big-o-mixed", "big O complexity"),
        ScaleQuery("sql-join", "sql join"),
        ScaleQuery("dao-ham-rieng", "đạo hàm riêng"),
        ScaleQuery("phan-phoi-chuan", "phân phối chuẩn"),
        ScaleQuery("erd-multi", "ERD quan hệ nhiều nhiều"),
        ScaleQuery("duong-di-euler", "đường đi Euler"),
        ScaleQuery("junk-nau-an", "công thức nấu ăn", junk = true),
        ScaleQuery("junk-game", "cách chơi game offline", junk = true)
    )

    /** One spec per planted query (index aligned with [queries], first 10 entries). */
    private val plantedSpecs = listOf(
        PlantedSpec(
            subject = "Xác suất thống kê", topics = listOf("xác suất có điều kiện"),
            titles = listOf(
                "Định lý Bayes phát biểu", "Định lý Bayes ví dụ minh họa", "Công thức Định lý Bayes",
                "Bài tập Định lý Bayes", "Ứng dụng Định lý Bayes"
            ),
            summary = "Định lý Bayes cập nhật xác suất hậu nghiệm từ bằng chứng quan sát: P(A|B) = P(B|A)P(A)/P(B).",
            ocr = "dinh ly Bayes xac suat dieu kien cong thuc hau nghiem bai tap vi du",
            intents = listOf("ôn thi", "", "công thức cần nhớ", "", "")
        ),
        PlantedSpec(
            subject = "Giải tích", topics = listOf("tích phân"),
            titles = listOf(
                "Tích phân từng phần công thức", "Tích phân từng phần ví dụ u dv", "Tích phân từng phần bài tập",
                "Công thức Tích phân từng phần", "Tích phân từng phần với ln x"
            ),
            summary = "Tích phân từng phần: u dv = uv - v du, áp dụng khi bị tích là tích của hai loại hàm khác nhau.",
            ocr = "tich phan tung phan u dv uv cong thuc giai tich bai tap ln x",
            intents = listOf("", "ôn thi", "", "chép lại công thức", "")
        ),
        PlantedSpec(
            subject = "Cấu trúc dữ liệu", topics = listOf("danh sách", "cây", "ngăn xếp", "bảng băm", "đồ thị"),
            titles = listOf(
                "Danh sách liên kết đơn", "Cây nhị phân tìm kiếm", "Ngăn xếp và hàng đợi",
                "Bảng băm hash table", "Đồ thị duyệt BFS DFS"
            ),
            summary = "Cấu trúc dữ liệu cơ bản: thao tác chèn, xóa, tìm kiếm và độ phức tạp tương ứng.",
            ocr = "cau truc du lieu danh sach lien ket cay nhi phan ngan xep hang doi bang bam do thi bfs dfs",
            intents = listOf("", "", "ôn tập nhanh", "", "")
        ),
        PlantedSpec(
            subject = "Mạng máy tính", topics = listOf("mạng", "giao thức"),
            titles = listOf(
                "Mô hình OSI bảy tầng", "Giao thức TCP và IP", "Địa chỉ IP và subnet",
                "Giao thức HTTP và DNS", "Truyền tin qua socket"
            ),
            summary = "Mạng máy tính: phân lớp giao thức và truyền gói tin giữa các thiết bị đầu cuối.",
            ocr = "mang may tinh osi tcp ip dia chi subnet http dns socket goi tin truyen",
            intents = listOf("ôn thi", "", "", "", "ghi chú")
        ),
        PlantedSpec(
            subject = "Giải thuật", topics = listOf("độ phức tạp"),
            titles = listOf(
                "Big-O notation cơ bản", "Big-O của thuật toán sắp xếp", "Big-O cheat sheet",
                "Big-O binary search log n", "So sánh Big-O các thuật toán"
            ),
            summary = "Big-O notation mô tả cận trên thời gian chạy: complexity O(1), O(log n), O(n log n).",
            ocr = "big O notation complexity upper bound sorting algorithms binary search log n",
            intents = listOf("", "", "", "", "")
        ),
        PlantedSpec(
            subject = "Cơ sở dữ liệu", topics = listOf("SQL", "join"),
            titles = listOf(
                "SQL join INNER và LEFT", "SQL join FULL OUTER", "SQL join RIGHT",
                "SQL join nhiều bảng", "SQL join tự nối"
            ),
            summary = "SQL join ghép dòng từ hai bảng theo điều kiện khớp nối.",
            ocr = "sql join inner left right full outer ghep dong bang dieu kien khop",
            intents = listOf("", "", "", "ôn thi", "")
        ),
        PlantedSpec(
            subject = "Giải tích", topics = listOf("đạo hàm"),
            titles = listOf(
                "Đạo hàm riêng hàm hai biến", "Đạo hàm riêng và gradient", "Bài tập Đạo hàm riêng",
                "Đạo hàm riêng ví dụ mẫu", "Đạo hàm riêng công thức"
            ),
            summary = "Đạo hàm riêng theo từng biến khi các biến còn lại giữ nguyên; gradient là vector các đạo hàm riêng.",
            ocr = "dao ham rieng hai bien gradient vector vi du bai tap cong thuc giai tich",
            intents = listOf("", "ôn thi", "", "", "")
        ),
        PlantedSpec(
            subject = "Xác suất thống kê", topics = listOf("phân phối"),
            titles = listOf(
                "Phân phối chuẩn mật độ", "Phân phối chuẩn N và sigma", "Phân phối chuẩn bảng Z",
                "Bài tập Phân phối chuẩn", "Phân phối chuẩn chuẩn hóa Z score"
            ),
            summary = "Phân phối chuẩn với mật độ hình chuông, đối xứng qua trung bình.",
            ocr = "phan phoi chuan mat do hinh chuong doi xung trung binh bang z score xac suat",
            intents = listOf("", "", "ôn thi cuối kỳ", "", "")
        ),
        PlantedSpec(
            subject = "PTTK", topics = listOf("ERD", "quan hệ"),
            titles = listOf(
                "Sơ đồ ERD quan hệ nhiều nhiều", "ERD thực thể quan hệ nhiều nhiều",
                "Quan hệ nhiều nhiều trong ERD", "Tách quan hệ nhiều nhiều ERD",
                "ERD quan hệ nhiều nhiều thư viện"
            ),
            summary = "Quan hệ nhiều nhiều trong ERD được tách qua thực thể trung gian khi thiết kế dữ liệu.",
            ocr = "ERD thuc the quan he nhieu nhieu trung gian thiet ke du lieu so do",
            intents = listOf("", "", "", "ôn thi", "")
        ),
        PlantedSpec(
            subject = "Toán rời rạc", topics = listOf("đồ thị"),
            titles = listOf(
                "Đường đi Euler điều kiện", "Đường đi Euler trong đồ thị", "Bài tập Đường đi Euler",
                "Đường đi Euler và chu trình", "Ví dụ Đường đi Euler"
            ),
            summary = "Đường đi Euler đi qua mỗi cạnh đúng một lần; điều kiện là đồ thị liên thông có không quá hai đỉnh bậc lẻ.",
            ocr = "duong di Euler do thi lien thong canh dinh bac le chu trinh vi du",
            intents = listOf("ôn thi", "", "", "", "")
        )
    )

    /** Distractors sharing the generic half of a query but never the specific target term. */
    private val genericSharingPools = listOf(
        FixedPool( // "định lý" without "bayes" -> probes query 0
            subjects = listOf("Giải tích", "Toán rời rạc"),
            titles = listOf("Định lý sin", "Định lý cos", "Định lý Talet", "Định lý Pythagore", "Định lý Rolle", "Định lý trung trực"),
            summary = "Phát biểu định lý kèm ví dụ minh họa và bài tập luyện tập.",
            ocr = "dinh ly phat bieu vi du chung minh bai tap hinh hoc",
            topics = listOf("định lý")
        ),
        FixedPool( // "phân tích"/"tích phân" without "từng phần" -> probes query 1
            subjects = listOf("Giải tích", "PTTK"),
            titles = listOf("Phân tích đa thức", "Phân tích yêu cầu chức năng", "Phân tích thiết kế tổng thể", "Tích phân bội hai", "Tích phân xác định"),
            summary = "Phương pháp phân tích và các bước giải cho dạng toán tương ứng.",
            ocr = "phan tich da thuc yeu cau thiet ke tich phan boi hai xac dinh vi du",
            topics = listOf("phân tích")
        ),
        FixedPool( // "đạo hàm" without "riêng" -> probes query 6
            subjects = listOf("Giải tích"),
            titles = listOf("Đạo hàm cấp hai", "Đạo hàm của hàm hợp", "Đạo hàm theo tham số", "Bảng đạo hàm thường gặp"),
            summary = "Đạo hàm và quy tắc chuỗi áp dụng cho hàm số một biến.",
            ocr = "dao ham cap hai ham hop tham so quy tac chuoi vi du giai",
            topics = listOf("đạo hàm")
        ),
        FixedPool( // "phân phối" without "chuẩn" -> probes query 7
            subjects = listOf("Xác suất thống kê"),
            titles = listOf("Phân phối nhị thức", "Phân phối mũ", "Phân phối rời rạc", "Phân phối Bernoulli", "Phân phối đều"),
            summary = "Kỳ vọng và phương sai của các phân phối rời rạc thường gặp.",
            ocr = "phan phoi nhi thu mu roi rac deu bernoulli ky vong phuong sai",
            topics = listOf("phân phối")
        ),
        FixedPool( // "đường đi" without "euler" -> probes query 9
            subjects = listOf("Toán rời rạc"),
            titles = listOf("Đường đi ngắn nhất", "Đường đi Hamilton", "Đường đi độ dài nhỏ nhất", "Đường đi trong đồ thị"),
            summary = "Đường đi trên đồ thị có trọng số và thuật toán tìm đường.",
            ocr = "duong di ngan nhat hamilton do thi trong so thuat toan tim duong",
            topics = listOf("đồ thị")
        )
    )

    /**
     * Distractors sharing exactly one query term (discrimination probes).
     * The "sql"-only pool matches the 2-term query "sql join" under the <=2-term
     * half-coverage rule; the "quan he" pool (2 of 4 terms) stays below the 2/3 gate.
     */
    private val nearMissPools = listOf(
        FixedPool( // "sql" without "join" -> probes query 5
            subjects = listOf("Cơ sở dữ liệu"),
            titles = listOf("Câu lệnh SQL truy vấn", "SQL view và index", "Trigger SQL thủ tục", "SQL lọc dữ liệu"),
            summary = "Tổng hợp các câu lệnh SQL thường dùng cho truy vấn đơn.",
            ocr = "cau lenh sql truy van view index trigger loc du lieu",
            topics = listOf("SQL")
        ),
        FixedPool( // "quan hệ" (2 of 4 terms) without ERD/nhiều -> probes query 8
            subjects = listOf("Cơ sở dữ liệu"),
            titles = listOf("Quan hệ trong mô hình dữ liệu", "Ràng buộc quan hệ", "Mô hình dữ liệu quan hệ"),
            summary = "Các kiểu quan hệ và ràng buộc trong mô hình dữ liệu.",
            ocr = "quan he mo hinh du lieu rang buoc khoa chinh khoa ngoai",
            topics = listOf("mô hình dữ liệu")
        )
    )

    /** Pure noise: unrelated synthetic study cards sharing no query term. */
    private val noiseSubjects = listOf(
        "Ngữ văn", "Lịch sử", "Địa lý", "Sinh học", "Hóa đại cương", "Vật lý 2",
        "Kinh tế vi mô", "Tâm lý học", "Triết học", "Anh văn học thuật", "PTTK", "Cơ sở dữ liệu", "Quản trị học"
    )
    private val noiseTitles = listOf(
        "Tổng quan môn {s} chương {n}", "Bài tập chương {n} môn {s}", "Ghi chú bài giảng {s} tuần {w}",
        "Tóm tắt nội dung {s} chương {n}", "Slide bài giảng {s} phần {k}", "Ôn tập cuối kỳ {s}",
        "Khái niệm cơ bản trong {s}", "Thảo luận nhóm môn {s}", "Phương pháp học tập môn {s}",
        "Kiểm tra giữa kỳ môn {s} năm {y}"
    )
    private val noiseSummaries = listOf(
        "Nội dung chính của chương {n} gồm các khái niệm cơ bản và ví dụ minh họa.",
        "Ghi lại các ý chính để ôn tập kiểm tra giữa kỳ.",
        "Bài giảng tuần {w} của giảng viên về đề tài nhóm.",
        "Tóm tắt lý thuyết và các dạng bài tập thường gặp.",
        "Slide trình chiếu cho buổi học thứ {d}.",
        "Câu hỏi thảo luận và bài tập về nhà chương {n}.",
        "Phương pháp giải các dạng bài tập trắc nghiệm.",
        "Kết quả kiểm tra giữa kỳ và cuối kỳ của năm học {y}."
    )
    private val noiseOcrs = listOf(
        "noi dung chuong {n} ghi chu y chinh on tap kiem tra",
        "bai tap lon nhom trinh bay slide buoi hoc thu {d}",
        "tom tat chuong {n} cau hoi thao luan bai tap ve nha",
        "slide bai giang tuan {w} on tap giua ky cuoi ky",
        "khai niem co ban vi du minh hoa de tai thuyet trinh",
        "phuong phap hoc tap ghi chu nhanh chuong {n} de thi"
    )
    private val noiseTopics = listOf("khái niệm", "tóm tắt", "ôn tập", "bài giảng", "thảo luận", "kiểm tra", "trình bày", "ghi chú")
    private val noiseIntents = listOf("", "", "", "ôn thi cuối kỳ", "ghi chú nhanh", "tóm tắt để ôn")

    private fun noiseCard(rng: Random): PendingCard {
        val subject = noiseSubjects[rng.nextInt(noiseSubjects.size)]
        val n = 1 + rng.nextInt(14)
        val w = 1 + rng.nextInt(15)
        val k = 1 + rng.nextInt(6)
        val d = 1 + rng.nextInt(12)
        val y = 2023 + rng.nextInt(4)
        fun fill(t: String) = t
            .replace("{s}", subject).replace("{n}", n.toString()).replace("{w}", w.toString())
            .replace("{k}", k.toString()).replace("{d}", d.toString()).replace("{y}", y.toString())
        return PendingCard(
            title = fill(noiseTitles[rng.nextInt(noiseTitles.size)]),
            subject = subject,
            topics = listOf(noiseTopics[rng.nextInt(noiseTopics.size)]),
            intent = noiseIntents[rng.nextInt(noiseIntents.size)],
            summary = fill(noiseSummaries[rng.nextInt(noiseSummaries.size)]),
            ocr = fill(noiseOcrs[rng.nextInt(noiseOcrs.size)])
        )
    }

    private fun buildCorpus(size: Int): Corpus {
        val rng = Random(SEED)
        val pending = mutableListOf<PendingCard>()

        plantedSpecs.forEachIndexed { qi, spec ->
            repeat(PLANTED_PER_QUERY) { k ->
                pending += PendingCard(
                    title = spec.titles[k % spec.titles.size],
                    subject = spec.subject,
                    topics = listOf(spec.topics[k % spec.topics.size]),
                    intent = spec.intents[k % spec.intents.size],
                    summary = spec.summary,
                    ocr = spec.ocr,
                    plantedQuery = qi
                )
            }
        }
        val genericPerPool = size / 25 // 4% of the corpus per generic-sharing pool
        for (pool in genericSharingPools) repeat(genericPerPool) { pending += pool.draw(rng) }
        val nearMissPerPool = size / 50 // 2% per near-miss pool
        for (pool in nearMissPools) repeat(nearMissPerPool) { pending += pool.draw(rng) }
        while (pending.size < size) pending += noiseCard(rng)

        // Deterministic interleave, then assign stable ids by final position.
        val order = pending.indices.shuffled(Random(SHUFFLE_SEED))
        val cards = ArrayList<SynthCard>(pending.size)
        val plantedIds = HashMap<Int, MutableSet<Long>>()
        order.forEachIndexed { position, source ->
            val p = pending[source]
            val id = (position + 1).toLong()
            cards += SynthCard(id, p.title, p.subject, p.topics, p.intent, p.summary, p.ocr)
            if (p.plantedQuery >= 0) {
                plantedIds.getOrPut(p.plantedQuery) { LinkedHashSet() }.add(id)
            }
        }
        return Corpus(cards, plantedIds)
    }

    /** One full production-like pass: sequential in-memory scan per query over the whole corpus. */
    private fun runPass(parsed: List<ParsedQuery>, docs: List<SearchableDoc>, ids: List<Long>): PassResult {
        val matched = ArrayList<Set<Long>>(parsed.size)
        val nanos = LongArray(parsed.size)
        for (qi in parsed.indices) {
            val q = parsed[qi]
            val hits = LinkedHashSet<Long>()
            val start = System.nanoTime()
            for (i in docs.indices) {
                if (LexicalMatcher.score(q, docs[i]).isMatch) hits.add(ids[i])
            }
            nanos[qi] = System.nanoTime() - start
            matched.add(hits)
        }
        return PassResult(matched, nanos)
    }

    private fun percentile(sorted: List<Double>, p: Double): Double =
        sorted[(ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)) - 1]

    @Test
    fun lexicalScaleBenchmark() {
        for (size in CORPUS_SIZES) runSize(size)
    }

    private fun runSize(size: Int) {
        val corpus = buildCorpus(size)
        // Determinism gate: identical corpus on rebuild with the same seed.
        assertEquals("corpus generation must be deterministic (seed $SEED)", buildCorpus(size).cards, corpus.cards)

        val docs = corpus.cards.map { it.toDoc() }
        val ids = corpus.cards.map { it.id }
        val parsed = queries.map { QueryParser.parse(it.query) }

        // One untimed warm-up pass (JIT), then PASSES measured passes.
        runPass(parsed, docs, ids)
        val passes = (1..PASSES).map { runPass(parsed, docs, ids) }
        for (p in 1 until passes.size) {
            assertEquals("stability: pass ${p + 1} result sets differ from pass 1", passes[0].matched, passes[p].matched)
        }

        val plantedCount = plantedSpecs.size * PLANTED_PER_QUERY
        val genericCount = genericSharingPools.size * (size / 25)
        val nearMissCount = nearMissPools.size * (size / 50)
        val lines = mutableListOf(
            "cards=$size planted=$plantedCount genericSharing=$genericCount nearMiss=$nearMissCount noise=${size - plantedCount - genericCount - nearMissCount}"
        )

        var totalRelevant = 0
        var totalHits = 0
        var totalMatched = 0
        val latMicros = DoubleArray(queries.size)
        for (qi in queries.indices) {
            val q = queries[qi]
            val matched = passes[0].matched[qi]
            val relevant = if (q.junk) emptySet() else corpus.groundTruth[qi].orEmpty()
            val hits = matched intersect relevant
            val precision = if (matched.isEmpty()) 1.0 else hits.size.toDouble() / matched.size
            val recall = if (relevant.isEmpty()) 1.0 else hits.size.toDouble() / relevant.size
            latMicros[qi] = passes.map { it.nanos[qi] }.average() / 1000.0
            lines += "${q.name.padEnd(20)} P=${"%.3f".format(precision)} R=${"%.3f".format(recall)}" +
                " matched=${matched.size.toString().padStart(4)} relevant=${relevant.size.toString().padStart(2)}" +
                " lat(us)=${"%.0f".format(latMicros[qi])}"
            if (q.junk) {
                assertTrue("junk query '${q.query}' must match nothing, got ${matched.sorted()}", matched.isEmpty())
            } else {
                assertTrue("recall gate (>= 0.95) failed for '${q.query}': $recall", recall >= 0.95)
                totalRelevant += relevant.size
                totalHits += hits.size
                totalMatched += matched.size
            }
        }
        val microP = totalHits.toDouble() / totalMatched
        val microR = totalHits.toDouble() / totalRelevant
        val sorted = latMicros.sorted()
        val p50 = percentile(sorted, 50.0)
        val p95 = percentile(sorted, 95.0)
        lines += "scan/query P50=${"%.0f".format(p50)}us P95=${"%.0f".format(p95)}us perCard=${"%.1f".format(p50 * 1000.0 / size)}ns"
        lines += "MICRO(planted) P=${"%.3f".format(microP)} R=${"%.3f".format(microR)} junkMatches=0 stability=$PASSES-identical-passes"
        println("=== LEXICAL SCALE BENCHMARK v2 size=$size (seed=$SEED) ===\n" + lines.joinToString("\n"))
    }
}
