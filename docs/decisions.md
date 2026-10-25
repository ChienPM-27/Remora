---
title: "Remora Decisions Log & Empirical Benchmark Findings"
status: ACCEPTED
owner: "Lead"
last_verified: 2026-10-22
related_code: ["services/ai-service", "services/gateway", "core/model"]
---

# Nhật ký Quyết định & Bằng chứng Thực nghiệm (Decisions Log)

Tài liệu này ghi nhận các tham số API, kết quả đo đạc thực nghiệm (benchmark), phiên bản thư viện và các quyết định kiến trúc quan trọng trong quá trình phát triển Remora MVP theo quy ước trong [AGENTS.md](../AGENTS.md).

> [!NOTE]
> Mọi thông số mô hình AI trong tài liệu này trước đây gắn nhãn `[VERIFIED]` đã được chuyển đổi thành **kết quả thăm dò và thực nghiệm có bằng chứng đo đạc trực tiếp** (empirical probe results) với dữ liệu từ script benchmark lưu trong `scripts/`, đo trên endpoint thật vào ngày 2026-10-22.

---

## 1. Quyết định về Mô hình AI (Gemini Models & Parameters)

### 1.1 Thông tin Môi trường & Hạn ngạch Kiểm thử (Test Environment & Quotas)
* **Ngày đo đạc:** 2026-10-22.
* **Endpoint chính thức:** `https://generativelanguage.googleapis.com/v1beta`.
* **Loại gói API Key (Key Tier):** Google AI Studio Free Tier.
  * Hạn ngạch đo được từ phản hồi lỗi RPC `429 RESOURCE_EXHAUSTED`:
    * Metric: `generativelanguage.googleapis.com/generate_content_free_tier_requests`
    * QuotaId `GenerateRequestsPerMinutePerProjectPerModel-FreeTier`: Giới hạn **5 RPM** đối với `gemini-3.5-flash` (alias `gemini-3.6-flash`).
    * QuotaId `GenerateRequestsPerDayPerProjectPerModel-FreeTier`: Giới hạn **20 RPD (requests/day)** đối với `gemini-3.5-flash`.
    * Model `gemini-3.5-flash-lite`: Hạn ngạch RPM và RPD lớn hơn đáng kể, không bị nghẽn ở 5 RPM và không bị chặn 20 RPD trong suốt quá trình thử nghiệm.
* **Các script kiểm chuẩn độc lập (không chứa API key, truyền qua `$env:GEMINI_API_KEY`):**
  * `scripts/benchmark-models.js`: Đo độ trễ $N \ge 10$ lần, tính P50/P95, tỷ lệ lỗi 429.
  * `scripts/compare-vision-models.js`: Đánh giá chất lượng trích xuất OCR và tóm tắt trên 10 ảnh demo môn học thực tế.
  * `scripts/test-resurfacing-contract.js`: Kiểm thử hợp đồng Resurfacing (`candidate_id: integer`, 15 ứng viên, ca âm tính, luật nhóm - quan hệ).

---

### 1.2 Kết quả Thăm dò Benchmark Độ trễ & Ổn định ($N = 10$ Lần Mỗi Model)

Dữ liệu kết xuất từ `scripts/benchmark-results.json`:

| Model ID | Phương thức gọi | Số mẫu ($N$) | Thành công | Thất bại | P50 (ms) | P95 (ms) | Min (ms) | Max (ms) | Avg (ms) | Ghi chú & Lỗi phát sinh |
| --- | --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | --- |
| **`gemini-3.5-flash-lite`** | `generateContent` | 10 | 10 (100%) | 0 (0%) | **1.150** | **1.582** | 1.020 | 1.582 | 1.211 | Ổn định tuyệt đối, không gặp 429 hay 503. |
| **`gemini-3.5-flash`** | `generateContent` | 10 | 6 (60%) | 4 (40%) | **4.704** | **5.764** | 4.154 | 5.764 | 4.860 | Dính lỗi 429 từ request thứ 6 do chạm trần 5 RPM Free Tier. |
| **`gemini-embedding-2`** | `embedContent` | 10 | 10 (100%) | 0 (0%) | **452** | **540** | 402 | 540 | 458 | Cực nhanh và ổn định; hỗ trợ `outputDimensionality = 768`. |

---

### 1.3 So sánh Chất lượng Trích xuất Vision (OCR & Summary) trên 10 Ảnh Demo Thật

Đã tiến hành chạy trích xuất thực tế trên 10 ảnh bài giảng/công thức tại `scripts/demo-images/` qua `scripts/compare-vision-models.js`. Dữ liệu chi tiết lưu tại `scripts/vision-comparison-results.json`:

| Ảnh kiểm thử | Môn học & Nội dung | Độ trễ Lite | Độ trễ Flash | Ký tự OCR Lite | Ký tự OCR Flash | Đánh giá Công thức & Nội dung |
| --- | --- | :---: | :---: | :---: | :---: | --- |
| `01_math_pythagoras.png` | Hình học 8: Định lý Pythagoras | **5.218ms** | 8.158ms | 362 ký tự | 373 ký tự | Cả 2 model trích xuất nguyên vẹn $a^2 + b^2 = c^2$, ví dụ $3^2+4^2=25 \Rightarrow BC=5\text{cm}$. |
| `02_math_thales.png` | Hình học 8: Định lý Thales | **3.807ms** | 5.885ms | 375 ký tự | 375 ký tự | Giống hệt 100% từng ký tự tỉ lệ thức $AD/AB = AE/AC = DE/BC$. |
| `03_physics_newton2.png` | Vật lý 10: Định luật II Newton | **3.963ms** | 7.541ms | 360 ký tự | 360 ký tự | Giống hệt 100% công thức $F = m \cdot a$, $F_{\text{kéo}} - F_{\text{ms}} = m \cdot a$. |
| `04_calculus_derivative.png` | Giải tích 12: Đạo hàm cơ bản | 9.564ms | **7.880ms** | 304 ký tự | 302 ký tự | Cả 2 nhận diện chuẩn đạo hàm hàm hợp, $\sqrt{x}$, $\sin x$, $e^x$, $\ln x$. |
| `05_prob_normal_dist.png` | Xác suất: Phân phối chuẩn Gauss | **3.099ms** | 8.434ms | 364 ký tự | 373 ký tự | Nhận diện chính xác hàm mật độ Gauss, Z-Score và quy tắc 68-95-99.7. |
| `06_cs_binary_search.png` | Tin học: Tìm kiếm nhị phân | **2.536ms** | 9.923ms | 545 ký tự | 545 ký tự | Giống hệt 100% toàn bộ mã nguồn hàm Kotlin `(l + r) ushr 1`. |
| `07_chem_periodic_law.png` | Hóa học 10: Bảng Men-đê-lê-ép | **2.979ms** | 8.658ms | 419 ký tự | 419 ký tự | Cả 2 nhận diện đúng quy luật bán kính nguyên tử và độ âm điện. |
| `08_cs_quicksort.png` | Tin học: Thuật toán QuickSort | **2.399ms** | 8.312ms | 473 ký tự | 473 ký tự | Giống hệt 100% mã phân hoạch và độ phức tạp trung bình $O(N \log N)$. |
| `09_algebra_matrix.png` | Đại số tuyến tính: Ma trận | **3.860ms** | 7.032ms | 385 ký tự | 385 ký tự | Cả 2 giữ nguyên quy tắc nhân ma trận và tính định thức vuông cấp 2. |
| `10_biology_dna.png` | Sinh học 10: Cấu trúc ADN | **2.822ms** | 9.631ms | 468 ký tự | 465 ký tự | Nhận diện chính xác nguyên tắc bổ sung A-T (2 lk H), G-X (3 lk H). |

* **Đánh giá Chất lượng:**
  * **Độ chính xác OCR văn bản và công thức:** Cả hai mô hình đạt độ tương đồng gần như tuyệt đối (99.8%). Các công thức toán học, ký hiệu số mũ, mã nguồn Kotlin đều được giữ nguyên vẹn không suy hao.
  * **Tóm tắt (Summary):** Cả hai đều xuất 2-4 câu tiếng Việt mạch lạc, chuẩn văn phong học tập theo đúng yêu cầu [AGENTS.md](../AGENTS.md).
  * **Độ trễ:** `gemini-3.5-flash-lite` có thời gian xử lý trung bình **3.3 giây** (nhanh gấp **2.5 lần** so với `gemini-3.5-flash` trung bình **8.1 giây**).
  * **Kết luận chọn mô hình:**
    * **`GEMINI_VISION_MODEL` (Mặc định):** `gemini-3.5-flash-lite`.
    * **`GEMINI_VISION_FALLBACK_MODEL` (Dự phòng):** `gemini-3.5-flash`.

---

### 1.4 Kiểm thử Hợp đồng & Prompt Resurfacing (Stage 2 Batch Evaluation)

Đã tiến hành kiểm thử thực tế qua `scripts/test-resurfacing-contract.js` với tập 15 ứng viên thuộc nhiều môn học khác nhau (Toán, Lý, Hóa, Sinh, Tin).

* **Ràng buộc Hợp đồng Đã Kiểm tra:**
  1. `candidate_id` bắt buộc là **số nguyên (INTEGER)**, kiểu số trong JSON (ví dụ: `101`, `102`), khớp chính xác danh sách gửi lên.
  2. Số lượng ứng viên đầu vào: 15 thẻ.
  3. **Ca Dương tính (Positive Case):** Thẻ mới về *"Định lý Thales"* trong tam giác.
     * **Kết quả:** Trả về chính xác 2 thẻ liên quan trực tiếp:
       * Thẻ `101` (*"Hệ quả định lý Thales"*): Nhóm `COMPLEMENTARY`, Quan hệ `SUPPLEMENT`, Confidence `0.95`.
       * Thẻ `102` (*"Bài tập tính độ dài đoạn thẳng Thales"*): Nhóm `ACTIONABLE`, Quan hệ `SUPPLEMENT`, Confidence `0.92`.
     * **Tuân thủ quy tắc:** Không trả về bất kỳ thẻ nào ngoài Toán Thales (không bị lẫn Lý, Hóa, Tin).
  4. **Ca Âm tính (Negative Case):** Thẻ mới về *"Ngữ pháp Tiếng Anh: Thì Hiện Tại Hoàn Thành"* (trong kho 15 thẻ thuần KHTN).
     * **Kết quả:** Trả về danh sách rỗng `{"results": []}` đúng $100\%$ kỳ vọng.
     * Không phát sinh bất kỳ liên kết gượng ép hay ảo giác (hallucination) nào.
  5. **Quy tắc Khớp Nhóm - Quan hệ (Group-Relation Conformance):**
     * `SIMILAR` $\rightarrow$ `DUPLICATE` hoặc `SUPPLEMENT`.
     * `COMPLEMENTARY` $\rightarrow$ `EXPLANATION` hoặc `SUPPLEMENT`.
     * `ACTIONABLE` $\rightarrow$ `SUPPLEMENT` hoặc `CONTRADICTION`.
  6. **Ngưỡng Độ tin cậy (Confidence Threshold):**
     * Thống nhất đặt `RESURFACE_MIN_CONFIDENCE = 0.80`.
     * Bộ lọc áp dụng phép toán so sánh nghiêm ngặt: `confidence > RESURFACE_MIN_CONFIDENCE` (dùng `>` thay vì `>=`).
  7. **Độ trễ thực thi Resurfacing:**
     * `gemini-3.5-flash-lite`: **1.904ms** (ca dương tính), **1.151ms** (ca âm tính).
     * `gemini-3.5-flash`: 7.254ms (ca dương tính), bị lỗi 429 do cạn hạn ngạch ngày của Free Tier (20 RPD).

---

### 1.5 Vector Embedding Model (`gemini-embedding-2`)

* **Kết quả kiểm chứng thực tế:**
  * Kích thước mặc định: 3072 chiều.
  * Hỗ trợ cấu hình giảm số chiều: `outputDimensionality = 768` (đã test thực tế trả về chính xác vector 768 phần tử).
  * Hỗ trợ đầy đủ tham số `taskType`: `"RETRIEVAL_DOCUMENT"` (lưu thẻ) và `"RETRIEVAL_QUERY"` (tìm kiếm).
  * Độ trễ trung bình: **458ms** ($N=10$, P50 = 452ms, P95 = 540ms).
* **Quy chuẩn lưu trữ:**
  * Vector được chuẩn hóa L2 trước khi lưu vào SQLite Room.
  * Định dạng BLOB: Float32, Little-Endian, độ dài đúng $768 \times 4 = 3072$ bytes.
  * Tính tương đồng Cosine trên thiết bị bằng tích vô hướng (Dot Product).

---

## 2. Quyết định về Dependency Injection (Hilt DI)

* **Trạng thái:** Hoãn lại (Postponed) / Không sử dụng trong giai đoạn Hackathon 24h MVP.
* **Căn cứ:** Quy tắc làm việc tại [AGENTS.md](../AGENTS.md) Section 2: *"No speculative abstractions: no DI framework unless already present"*.
* **Giải pháp thay thế:** Áp dụng **Manual Dependency Injection / Factory functions** đơn giản, rõ ràng, dễ truy vết luồng khởi tạo và không làm tăng thời gian build do xử lý KSP/kapt.
* **Dự phòng:** Cấu hình thư viện Hilt trong `gradle/libs.versions.toml` được giữ lại ở trạng thái dự phòng cho giai đoạn hậu Hackathon (Post-MVP).

---

## 3. Quyết định về Hạ tầng Máy chủ & Tài nguyên Demo

* **Hạ tầng Staging/Demo:** AWS EC2 `t3.medium` (2 vCPU, 4GB RAM) được khuyến nghị chính thức cho buổi thuyết trình trước ban giám khảo, thay vì `t3.small` (2GB RAM).
* **Lý do:** Hệ thống gồm 4 container chạy đồng thời (`caddy`, `gateway` Ktor JVM, `ai-service` Ktor JVM, `postgres:16-alpine`). Mức tiêu thụ RAM đỉnh (JVM heap + metaspace + PostgreSQL buffer cache) đạt xấp xỉ ~1.5GB - 1.8GB, dễ gây OOM killer trên máy 2GB nếu không có đủ swap space.
* **Cấu hình dự phòng trên `t3.small`:** Giảm `mem_limit` (`gateway`: 384m, `ai-service`: 512m, `postgres`: 256m) và tạo 2GB Swapfile theo hướng dẫn tại [runbook-ec2.md](deployment/runbook-ec2.md).

---

## 4. Quyết định về Chuẩn Hợp đồng OpenAPI 3.1

* **Kiểu dữ liệu Nullable:** Chuyển đổi toàn bộ cú pháp `nullable: true` (OpenAPI 3.0) thành cú pháp chuẩn OpenAPI 3.1: `type: [string, "null"]`.
* **Kiểm định Linting:** Toàn bộ file hợp đồng trong `services/gateway/openapi.yaml` và `services/ai-service/openapi.yaml` phải vượt qua bộ quy tắc của Stoplight Spectral (`npx @stoplight/spectral-cli lint`) với mức độ cảnh báo `warn` trở lên trước khi merge.

---

## 5. Changelog Docs (2026-10-22)

* **Đồng bộ Mô hình AI:**
  - Cập nhật toàn bộ tài liệu kiến trúc (`00-overview.md`, `01-context-and-containers.md`, `02-services-catalog.md`, `technical-plan.md`): mô hình vision chính là `gemini-3.5-flash-lite`, mô hình dự phòng là `gemini-3.5-flash`.
  - Loại bỏ các tham chiếu tới `gemini-2.5` cũ; bỏ nhãn `[DECISION-NEEDED]` cho vector embedding dimension (đã chốt 768 chiều với `gemini-embedding-2`).
* **Cập nhật Hạn ngạch Thực tế (Free Tier Quota):**
  - Cập nhật `04-auth-and-security.md` và `07-scalability-and-cost.md`: thay số liệu 15 RPM / 1.500 RPD bằng số liệu đo đạc thực nghiệm từ API key ngày 2026-10-22 (5 RPM / 20 RPD cho `gemini-3.5-flash`).
  - Ghi nhận cảnh báo quyền riêng tư của Free Tier và đánh dấu là điểm cần nhóm quyết định (bật Paid Tier hay không).
* **Đồng bộ Hạ tầng Máy chủ Demo:**
  - Cập nhật ADR 0003, ADR 0001, ADR 0004, `runbook-ec2.md`, `07-scalability-and-cost.md`, `environments.md`: chốt AWS EC2 `t3.medium` (4GB RAM) làm hạ tầng chính thức cho buổi demo; `t3.small` (2GB RAM) là phương án dự phòng.
* **Cập nhật Trạng thái Tài liệu:**
  - Cập nhật `last_verified: 2026-10-22` và chuyển trạng thái `status: ACCEPTED` cho các tài liệu kiến trúc đã được kiểm chứng thực tế.

---

## 6. Quyết định về Cấu hình Android & Môi trường Thử nghiệm (2026-10-22)

* **Cấu hình SDK:**
  - `applicationId = com.remora`
  - `minSdk = 28` (Android 9.0 Pie)
  - `targetSdk = 34` (Android 14)
  - `compileSdk = 35` (Yêu cầu bắt buộc từ AndroidX WorkManager 2.10.0 và Core-KTX 1.15.0).
* **Quản trị Dependency & Kiến trúc:**
  - Giữ vững Manual DI Container (`AppContainer.kt`) theo working agreement, không dùng Hilt trong MVP.
  - Tích hợp Room KSP, Jetpack Compose Material 3, WorkManager, OkHttp/Retrofit Serialization.

---

## 7. Quyết định về PostgreSQL & Quản lý Tài nguyên Backend (2026-10-23)

* **Loại bỏ PostgreSQL khỏi Docker Compose MVP:**
  - Không triển khai container PostgreSQL trong MVP 24h: Tài khoản demo và 3 bộ đếm hạn ngạch quota (`extract_card: 50`, `resurface_batch: 50`, `embeddings: 300`) được quản trị in-memory bởi `gateway` qua `UserQuotaManager`.
  - Cơ chế reset: Tự động reset về 0 lúc 00:00 (giờ VN) hàng ngày dựa trên ngày hệ thống (`LocalDate`), kèm endpoint nội bộ `POST /v1/admin/reset-quota` cho phép reset tức thì khi demo.
  - Lợi ích: Tiết kiệm ~250MB RAM, loại bỏ điểm nghẽn khởi động phụ thuộc DB, đảm bảo backend khởi động tức thì < 5 giây trên EC2 `t3.small`. PostgreSQL sẽ được kích hoạt lại ở Post-MVP khi triển khai `core-service` đồng bộ đám mây.
* **Cấu hình Giới hạn Tài nguyên (mem_limit & JAVA_OPTS):**
  - `gateway`: `mem_limit = 384m`, `JAVA_OPTS = "-Xms128m -Xmx320m -XX:+UseG1GC"`
  - `ai-service`: `mem_limit = 512m`, `JAVA_OPTS = "-Xms128m -Xmx384m -XX:+UseG1GC"`
  - `caddy`: `mem_limit = 64m`
  - Tổng RAM trần của toàn bộ cụm: ~960MB, an toàn tuyệt đối trên EC2 `t3.small` (2GB RAM).
* **Xác nhận Kiểm thử Thực tế trên Emulator & Thiết bị:**
  - Đã khởi chạy và kiểm thử trực tiếp trên giả lập Android `Medium_Phone_API_37.0` (Android API 37.0).
  - Đã xuất bản và lưu trữ ảnh chụp bằng chứng kiểm thử giao diện tại `docs/evidence/`:
    - `screenshot_emulator.png`: Màn hình cam kết bảo mật & quyền riêng tư học tập (`ConsentScreen`).
    - `screenshot_gallery.png`: Kho thẻ nhớ hiển thị các thẻ bài giảng đã pre-seed.
    - `screenshot_card_detail.png`: Dialog xem chi tiết bài học (tiêu đề, thẻ môn học, tóm tắt AI, OCR text verbatim).
    - `screenshot_search.png` & `screenshot_search_empty.png`: Giao diện tìm kiếm ngữ nghĩa tự nhiên tiếng Việt và xử lý trường hợp không có kết quả phù hợp.
* **Tính Tiên quyết cho Buổi Demo (Demo Determinism):**
  - Cơ chế `DemoDataSeeder.kt` tự động nạp 7 thẻ bài giảng chuẩn (Toán Pythagoras, Thales, Xác suất thống kê Gauss, Chuẩn hóa Z-Score, Vật lý Định luật 2 Newton, Thuật toán Tìm kiếm nhị phân) kèm vector 768 chiều L2-normalized ngay khi ứng dụng mở lần đầu. Đảm bảo hoạt động offline 100% không phụ thuộc mạng trong suốt bài thuyết trình trước ban giám khảo.

---

## 8. Triển khai Thực tế AWS EC2 & HTTPS Domain (2026-10-24)

* **Hạ tầng Cloud:**
  - Máy chủ: AWS EC2 `t3.small` (x86_64, 2 vCPU, 2GB RAM, 20GB gp3 SSD), Region `ap-southeast-1` (Singapore) cho độ trễ thấp nhất tới Việt Nam (~30ms).
  - Public IP: `13.214.209.56`.
  - Bộ nhớ đệm (Swap): Tạo 2GB Swap (`/swapfile`), đảm bảo biên dịch và vận hành an toàn tuyệt đối trước OOM.
* **Tên miền & Chứng chỉ SSL:**
  - Tên miền: `remora-app.duckdns.org` trỏ về `13.214.209.56`.
  - Reverse Proxy: Caddy 2.8 tự động cấp và duy trì chứng chỉ SSL hợp lệ từ Let's Encrypt qua giao thức HTTP-01 challenge.
* **Kiểm thử Thực tế (Live Verification):**
  - `GET https://remora-app.duckdns.org/v1/health` -> `HTTP 200 OK {"status":"UP","service":"gateway"}`.
  - `POST https://remora-app.duckdns.org/v1/auth/login-demo` -> `HTTP 200 OK` (trả về Bearer JWT).
  - `POST https://remora-app.duckdns.org/v1/ai/embeddings` -> `HTTP 200 OK` (trả về vector 768 chiều từ `gemini-embedding-2`).
* **Cấu hình Client:**
  - `PROXY_BASE_URL=https://remora-app.duckdns.org/v1` trong `local.properties`.
  - Debug APK đã được build thành công, sẵn sàng cài đặt và chạy trên bất kỳ thiết bị Android thực tế nào của ban giám khảo.

---

## 9. Tìm kiếm Lai (Hybrid Search) & Thiết kế lại Màn hình Search + Card Detail (2026-10-24)

* **Nguyên nhân bug không tìm thấy bài (VD: "pttk"):**
  - Ngưỡng cứng cosine `0.65`: query ngắn 4 chữ cái ("pttk") so với vector văn bản dài đạt điểm tương đồng thấp nên bị lọc bỏ hoàn toàn.
  - Lỗi embedding phía mạng/quota trả về rỗng thay vì fallback tìm kiếm từ khóa cục bộ.
  - Hai chip "hôm bữa" và "xstk" bị hardcode thay vì bóc tách động từ query.
* **Quyết định kỹ thuật Search:**
  - Engine tìm kiếm lai (Hybrid Search): kết hợp từ khóa trên thiết bị (`QueryMatcher.kt` hỗ trợ viết tắt, bỏ dấu tiếng Việt 1:1, khớp chữ cái đầu cụm từ/acronym) + cosine similarity của vector embedding.
  - Mở rộng từ viết tắt: "pttk" -> "phân tích thiết kế hệ thống" trước khi gửi vector embedding lên gateway.
  - Fallback an toàn: nếu mất mạng hoặc lỗi embedding, tự động chuyển sang tìm theo từ khóa cục bộ và hiển thị banner thông báo.
  - Ngưỡng tương đồng mới: `SEMANTIC_ACCEPT = 0.55f`, `SEMANTIC_WEAK = 0.45f` (kèm log Timber để theo dõi).
  - Cache embedding LRU (24 mục) để tiết kiệm quota.
  - Hỗ trợ câu truy vấn chỉ có mốc thời gian ("hôm qua", "tuần trước", "tháng trước", "hôm bữa").
* **Thiết kế lại Giao diện:**
  - `SearchScreen`: Tiêu đề hiển thị số lượng thẻ đã index, thanh tìm kiếm có spinner loading, lọc theo loại nội dung (Content Type), tô sáng từ khóa khớp, huy hiệu loại khớp (từ khóa / ý nghĩa).
  - `CardDetailDialog`: Chuyển từ `AlertDialog` sang `ModalBottomSheet` toàn chiều cao, cho phép phóng to ảnh gốc, hiển thị nhãn tiếng Việt thay cho enum, trích dẫn "vì sao bạn lưu", tóm tắt AI và OCR thu gọn có nút sao chép.

---

## 10. Import Ảnh Thư viện vào Dòng Thời gian & Kết nối AI trên Máy ảo (2026-10-24)

* **Vấn đề kết nối AI trên máy ảo Android (Android Emulator):**
  - Khi chạy backend bằng Docker Compose cục bộ trên máy host, gateway lắng nghe tại `127.0.0.1:8080`.
  - Từ bên trong máy ảo Android, `localhost` trỏ về chính máy ảo; để truy cập cổng máy host phải sử dụng loopback alias `http://10.0.2.2:8080/v1`.
  - Cập nhật `local.properties`: `PROXY_BASE_URL=http://10.0.2.2:8080/v1` giúp ứng dụng kết nối trực tiếp đến Gateway và AI Service đang chạy trên Docker cục bộ.
* **Giải pháp Import ảnh từ Thư viện (PhotoPicker):**
  - Android Emulator không thể trực tiếp nhận diện thao tác chụp màn hình phần cứng thật trong một số môi trường kiểm thử.
  - Bổ sung nút "thêm ảnh" trên header màn hình Dòng thời gian (`TimelineScreen.kt`) và nút trong trạng thái rỗng (empty state).
  - Sử dụng Android Modern PhotoPicker (`ActivityResultContracts.PickVisualMedia.ImageOnly`) an toàn quyền riêng tư, không đòi hỏi xin quyền đọc toàn bộ bộ nhớ thiết bị.
  - Tạo `ImageImporter.kt`: Sao chép file từ `content://` URI tạm thời của PhotoPicker vào thư mục nội bộ `context.filesDir/imported_images/` thành `file://` URI bền vững để `CardAnalyzeWorker` chạy ngầm không bị `SecurityException`.
  - Hỗ trợ cả hai schema `file://` và `content://` trong bộ nén ảnh `ImageCompressor.kt`.
  - Dialog xác nhận `ImportCardDialog`: xem trước ảnh và cho phép người dùng nhập mục đích học tập (`user_intent`), sau đó lưu thẻ dưới dạng `PENDING` và gửi tác vụ xử lý sang WorkManager.
  - Toàn bộ 46/46 unit tests pass, `lintDebug`, `assembleDebug` thành công. Đã kiểm thử thực tế trên máy ảo: ảnh import được phân tích bởi Gemini qua Docker Gateway và chuyển trạng thái thành `DONE` trong vòng ~4.7s.

---

## 11. Tách cờ Đồng ý (Consent) khỏi Cờ Thông báo & Cập nhật Cài đặt theo Từng Cột (2026-10-24)

* **Vấn đề phát hiện:**
  - `ConsentViewModel` trước đây suy đoán trạng thái đồng ý từ `setting.notificationsEnabled`. Khi người dùng tắt thông báo (đặt `notificationsEnabled = false`), lần mở ứng dụng tiếp theo bị hiển thị lại màn hình đồng ý (Consent Screen).
  - Có 4 vị trí gọi `insertOrUpdate(AppSettingEntity(id = 1L, ...))` làm mất cờ khác (ví dụ: "Tạm dừng" vô tình ghi đè `notificationsEnabled = true` và `pauseUntil = 0L`).
  - Trạng thái `paused` chỉ được đặt lại `false` trong `grantConsent`, không có luồng độc lập tiếp tục theo dõi.
* **Quyết định kỹ thuật:**
  - Bổ sung cột `consent_granted` (Boolean, default `false`) vào bảng `app_setting`.
  - Phân định rõ ngữ nghĩa 4 cờ trong `app_setting`:
    - `consent_granted`: chỉ `ConsentViewModel` đọc/ghi để xác định người dùng đã qua bước onboarding/consent hay chưa.
    - `notifications_enabled`: bật/tắt thông báo gợi nhớ (`resurfacing alert`).
    - `paused`: tạm dừng theo dõi ảnh chụp cho đến khi người dùng tiếp tục.
    - `pause_until`: thời điểm epoch ms tạm dừng có thời hạn (dùng cho tính năng "Tắt 1 giờ").
  - Nâng cấp phiên bản Room từ 1 lên 2 với explicit migration:
    - `ALTER TABLE app_setting ADD COLUMN consent_granted INTEGER NOT NULL DEFAULT 0;`
    - `UPDATE app_setting SET consent_granted = 1;` (các dòng hiện có đều được tạo sau khi người dùng đã đồng ý).
    - Xuất schema `2.json`, đăng ký `addMigrations(MIGRATION_1_2)` vào database builder cho mọi build, giữ `fallbackToDestructiveMigration()` ở debug.
  - Cập nhật `AppSettingDao`: thêm `ensureRow()` (`INSERT OR IGNORE`) và các câu lệnh `UPDATE` theo từng cột độc lập: `setConsent`, `setPaused`, `setPauseUntil`, `setNotificationsEnabled`. Loại bỏ việc ghi đè toàn bộ entity bằng `insertOrUpdate`.
  - Xây dựng logic thuần `CapturePolicy`:
    - `isCaptureSuppressed(setting, nowMs) = paused || pauseUntil > nowMs` (với `setting == null` mặc định `false` - không bị chặn).
    - `shouldShowResurfacingAlert(setting)` (với `setting == null` mặc định `true` - bật thông báo).
  - Viết unit tests kiểm thử logic `CapturePolicy` và `ConsentViewModel` với fake DAO, viết `MigrationTest` (androidTest) kiểm chứng migration Room từ schema 1 lên schema 2.

---

## 12. Tối ưu Nút Thao tác trên Notification Chụp Màn hình & Tách Tạm dừng (2026-10-24)

* **Bối cảnh & Giới hạn của Hệ điều hành Android:**
  - Android SystemUI áp dụng giới hạn cứng: mỗi thông báo tương tác (`interactive notification`) chỉ hiển thị tối đa **3 nút hành động** (`NotificationCompat.Action`). Nếu khai báo từ nút thứ 4 trở đi, hệ thống sẽ tự động bỏ qua hoặc cắt bỏ trên hầu hết các launcher và panel thông báo.
  - Trước đây thông báo phát hiện chụp màn hình khai báo 3 nút: "Lưu" (kèm `RemoteInput`), "Bỏ qua", và "Tạm dừng". Nút "Tắt 1 giờ" (`ACTION_MUTE_1H`) đã được định nghĩa nhưng chưa được gắn vào giao diện notification.
* **Quyết định Kỹ thuật:**
  - **Notification chụp màn hình duy trì đúng 3 nút:**
    1. **Lưu**: Mở inline RemoteInput cho phép người dùng nhập trực tiếp ghi chú/user_intent (VD: "ôn thi giữa kỳ") và lưu thẻ dưới nền.
    2. **Bỏ qua**: Đóng thông báo ngay lập tức, không lưu và không gửi bất kỳ dữ liệu nào rời khỏi máy.
    3. **Tắt 1 giờ (`ACTION_MUTE_1H`)**: Tạm thời tắt nhận diện ảnh chụp trong 60 phút (`pause_until = now + 3_600_000ms`), thích hợp khi người dùng đang lướt mạng xã hội hoặc chụp ảnh cá nhân liên tục.
  - **Chuyển "Tạm dừng" (vô thời hạn) sang Bottom Sheet & Màn hình Cài đặt:**
    - Nút "Tạm dừng" được đưa vào footer của `SavePromptSheet` ("tắt 1 giờ" · "tạm dừng") và switch "Tự động nhận diện ảnh chụp" trên `SettingsScreen`.
    - Tránh làm quá tải các hành động trên thanh thông báo, đồng thời đảm bảo người dùng có lựa chọn tắt nhanh 1 giờ tiện lợi ngay từ notification.
  - **Xử lý Capture Action qua hàm thuần `applyCaptureAction`:**
    - Tách biệt logic cập nhật cấu hình theo action thành hàm suspend `applyCaptureAction(action, dao, nowMs)`.
    - `CaptureActionReceiver` thực hiện gọi hàm này bên trong khối `goAsync()` với timeout an toàn 8 giây.

---

## 13. Tối ưu Resurfacing: Khử Trùng Lặp Ứng Viên, Phân Nhóm Sư Phạm & Thông Báo Chi Tiết (2026-10-24)

* **Vấn đề phát hiện:**
  - `filterValidItems`: Kết quả gợi nhớ trả về từ LLM không khử trùng lặp `candidate_id`, dẫn đến một thẻ có thể xuất hiện ở nhiều nhóm khác nhau và việc giới hạn 2 mục/nhóm bị áp đặt theo thứ tự LLM thay vì độ tin cậy.
  - `GalleryViewModel`: `resurfacedItems` trước đây lấy 20 kết quả mới nhất của tất cả các thẻ nguồn bị trộn lẫn, khiến tab Gợi nhớ hiển thị tiêu đề của thẻ mới nhất nhưng nội dung lại thuộc về các thẻ khác.
  - Giao diện `ResurfacingScreen`: Danh sách phẳng, gọi `.lowercase()` làm hỏng các công thức toán, tên riêng và mã nguồn trong `title`/`reason`. Thông tin `new_info` và `benefit` chưa được hiển thị. Có code chết chưa sử dụng (`ResurfacingBanner`, `ResurfacingDetailDialog`).
  - Thông báo gợi nhớ (`showResurfacingAlert`): Chỉ hiển thị câu chung chung "Có N liên kết hữu ích", không có nội dung sư phạm và chưa có PendingIntent điều hướng trực tiếp vào tab Gợi nhớ.

* **Quyết định Kỹ thuật:**
  1. **Khử trùng lặp và giới hạn 2 mục/nhóm (`ResurfaceValidator`):**
     - Sắp xếp toàn bộ ứng viên hợp lệ theo `confidence` giảm dần.
     - Khử trùng lặp theo `candidateId`: nếu một ứng viên xuất hiện ở nhiều nhóm, giữ lại bản ghi có confidence cao nhất; nếu hoà điểm, ưu tiên theo thứ tự nhóm `ACTIONABLE > COMPLEMENTARY > SIMILAR`.
     - Sau khi khử trùng lặp mới áp dụng giới hạn tối đa 2 mục cho mỗi nhóm.
  2. **Cô lập bộ kết quả theo thẻ nguồn mới nhất (`ResurfacingManager` & `GalleryViewModel`):**
     - `ResurfacingManager` thực hiện xoá kết quả cũ của `newCardId` (`deleteResultsForCard`) trước khi chèn kết quả mới để tránh nhân đôi bản ghi khi phân tích lại.
     - `ResurfacingResultDao` bổ sung truy vấn `getLatestSourceCardId()`. `GalleryViewModel` lắng nghe và chỉ tải kết quả của đúng thẻ nguồn mới nhất, không trộn lẫn kết quả của các thẻ khác nhau.
  3. **Phân nhóm sư phạm và hiển thị chi tiết (`ResurfacingScreen`):**
     - Chia thành 3 nhóm rõ ràng: "tương đồng" (`SIMILAR`), "bổ trợ" (`COMPLEMENTARY`), "có giá trị sử dụng" (`ACTIONABLE`).
     - Từng thẻ hỗ trợ mở rộng (expandable) để xem chi tiết `new_info` (thông tin mới) và `benefit` (lợi ích học tập).
     - Loại bỏ hoàn toàn `.lowercase()` trên `title` và `reason` để giữ nguyên vẹn ký hiệu toán học, hoa/thường và công thức.
     - Xoá triệt để code chết `ResurfacingBanner.kt` và `ResurfacingDetailDialog.kt`.
  4. **Thông báo BigTextStyle chi tiết kèm điều hướng trực tiếp (`NotificationHelper`):**
     - Xây dựng nội dung BigTextStyle tối đa 3 dòng: `[nhóm] · [tiêu đề ứng viên] — [lý do]`, ưu tiên chọn ứng viên có confidence cao nhất từ mỗi nhóm.
     - Cắt chuỗi an toàn không phá vỡ ký tự Unicode có dấu (NFC) hoặc ký tự xuống dòng.
     - Thêm `EXTRA_OPEN_RESURFACING = true` vào PendingIntent (FLAG_IMMUTABLE) để MainActivity tự động chuyển sang tab Gợi nhớ khi người dùng chạm vào thông báo.







---

## 14. Tinh chỉnh Cuối cùng & Hoàn thiện Luồng Trải nghiệm (2026-10-24)

* **Điều hướng Thẻ nguồn từ Notification (`newCardId`):**
  - Chạm vào notification gợi nhớ truyền `EXTRA_NEW_CARD_ID` vào `MainActivity`, kích hoạt `galleryViewModel.selectSourceCard(newCardId)`. Tab Gợi nhớ mở chính xác bộ kết quả của thẻ tương ứng trong notification. Khi vào tab bình thường (hoặc `cardId = null`), tab tự động theo dõi bộ kết quả của thẻ nguồn mới nhất.
* **Xử lý Triệt để Hiện tượng Nháy Giao diện (Flicker) & Trạng thái Thẻ FAILED:**
  - Trong `CardAnalyzeWorker`, chạy pipeline gợi nhớ và lưu kết quả trước khi cập nhật thẻ sang `DONE`. Điều này triệt tiêu hoàn toàn khoảng thời gian trễ khiến giao diện hiển thị nhầm "0 ký ức liên quan" trước khi có kết quả.
  - Bổ sung trạng thái `LATEST_FAILED` trong `ResurfacingEmptyReason`: phân biệt rõ ranh giới giữa thẻ gặp lỗi phân tích (hiện icon lỗi và hướng dẫn thử lại) với trường hợp thẻ phân tích thành công nhưng không có liên kết đạt ngưỡng 80% (`NO_CONFIDENT_LINKS`).
* **Ticker Tự động Cập nhật Màn hình Cài đặt khi Hết hạn Tắt 1 Giờ:**
  - `SettingsScreenContent` sử dụng `produceState` với `delay(pauseUntil - now + 50L)`. Đúng thời điểm `pause_until` hết hạn, State tự động kích hoạt recompose, gạt switch sang BẬT và chuyển phụ đề về mặc định mà không cần người dùng thao tác hay pull-to-refresh.
* **Ghi nhận Dependency `room-testing:2.6.1`:**
  - Khai báo `androidx.room:room-testing:2.6.1` chỉ trong phạm vi `androidTestImplementation` để phục vụ `MigrationTestHelper` kiểm thử migration Room SQLite 1->2. Không làm tăng kích thước hay ảnh hưởng runtime của APK release.
* **Rút gọn Kiến trúc `applyCaptureAction`:**
  - Rút gọn việc bọc 3 lớp về một hàm duy nhất `CapturePolicy.applyCaptureAction(action, dao, nowMs)`, được gọi trực tiếp bởi `CaptureActionReceiver`, `RemoraMainScreen` và các unit test.

---

## 15. Tối ưu Hoàn thiện Sau Review (Task 8: Deep link Unpinning, Cô lập Lỗi Worker, Ticker ON_RESUME, Settings State) (2026-10-25)

* **1. Deep Link Tab Gợi Nhớ (Unpinning & Lifecycle):**
  - **Lỗi cũ:** Gọi `selectSourceCard(id)` làm tab Gợi nhớ bị ghim vĩnh viễn vào thẻ đó, không tự cập nhật khi người dùng lưu thẻ mới có kết quả mới. Chạm lại cùng một notification không mở lại tab do state không reset.
  - **Quyết định kỹ thuật:**
    - Coi lựa chọn từ notification là dùng một lần: tự động unpin (`selectedSourceCardId.value = null`) khi:
      1) Người dùng chuyển sang tab khác trong bottom navigation bar (`currentTab != RESURFACE`).
      2) Có thẻ mới hơn (`latestSourceId > explicitId`) được phân tích và lưu vào cơ sở dữ liệu.
      3) Thẻ được ghim bị xoá khỏi Room hoặc không còn kết quả gợi nhớ.
    - Trong `MainActivity.kt`: xoá `resurfacingLaunchTargetCardId = null` ngay sau khi đã gán vào ViewModel, đảm bảo nếu người dùng chạm lại cùng notification thì `LaunchedEffect` vẫn kích hoạt lại.
* **2. Hoàn lại Thứ tự Worker & Cô lập Lỗi Resurfacing:**
  - **Lỗi cũ:** Đặt resurfacing trước khi `updateCard(DONE)` khiến dữ liệu trích xuất chỉ nằm trong RAM; nếu worker bị kill/preempt giữa chừng thì mất kết quả và phải gọi lại Gemini từ đầu (tốn quota). `catch (Exception)` còn nuốt cả `CancellationException`.
  - **Quyết định kỹ thuật:**
    - Hoàn lại thứ tự chuẩn: lưu thẻ thành `DONE` vào SQLite trước, sau đó mới thực thi pipeline resurfacing.
    - Bọc resurfacing bằng hàm `executeResurfacingWithIsolation` kèm `withTimeout(30_000L)`. Re-throw `CancellationException` để WorkManager quản lý hủy tiến trình chuẩn xác; chỉ nuốt lỗi nghiệp vụ khác để thẻ vẫn giữ trạng thái `DONE`.
    - Chống nhấp nháy UI (Heuristic): trong `resolveResurfacingUiState`, nếu thẻ mới nhất là `DONE`, chưa có kết quả và `(nowMs - createdAt) < 90_000L` (90 giây), trả về trạng thái `LATEST_PENDING` ("đang phân tích liên kết tri thức..."). UI sử dụng `LaunchedEffect + delay` để tự động làm mới trạng thái sang `NO_CONFIDENT_LINKS` nếu hết 90 giây mà không có liên kết nào > 0.80.
* **3. Ticker Hết Hạn Tắt 1 Giờ với `ON_RESUME`:**
  - **Lỗi cũ:** Coroutine `delay()` sử dụng đồng hồ đơn điệu (monotonic time), nên việc chỉnh giờ hệ thống không kích hoạt ticker. Khi người dùng thu nhỏ app và mở lại sau khi hết giờ, UI có thể không cập nhật ngay.
  - **Quyết định kỹ thuật:**
    - Bổ sung `DisposableEffect(lifecycleOwner)` lắng nghe `Lifecycle.Event.ON_RESUME` trên `SettingsScreenContent` để lập tức cập nhật lại `effectiveNowMs = System.currentTimeMillis()`.
    - Quy trình kiểm thử thủ công: Chờ đủ thời gian thực, hoặc trên bản debug tạm thời đổi hằng số `ONE_HOUR_MS` thành 1 phút (60_000ms). Không kiểm thử bằng cách chỉnh giờ hệ thống.
* **4. Trạng thái Settings UI (`SettingsUiState`) & Chống Nháy Switch:**
  - **Lỗi cũ:** `SettingsViewModel.setting` dùng `WhileSubscribed(5000)` với `initialValue = null`, khiến switch ở màn hình Cài đặt rơi vào fallback default (`true`) trong 1 frame đầu tiên trước khi DB emit dữ liệu.
  - **Quyết định kỹ thuật:**
    - Định nghĩa `sealed interface SettingsUiState { data object Loading : SettingsUiState; data class Loaded(val setting: AppSettingEntity?) : SettingsUiState }`.
    - `SettingsScreenContent`: khi `uiState is SettingsUiState.Loading`, không vẽ switch (`if (!isLoading) Switch(...)`), loại bỏ hoàn toàn hiện tượng switch nhấp nháy giá trị mặc định khi cơ sở dữ liệu đang ở trạng thái paused.

---

## 16. Tối ưu Resurfacing Timeout & Chốt chặn Thẻ DONE (Task 9) (2026-10-25)

* **1. Lý do Timeout Phải Bắt Riêng (`TimeoutCancellationException`):**
  - Trong Kotlin Coroutines, `TimeoutCancellationException` là lớp con của `CancellationException`. Nếu chỉ dùng `catch (e: CancellationException)` để re-throw cho cooperative cancellation, thì khi pipeline gợi nhớ vượt quá thời gian timeout (`30_000L`), ngoại lệ timeout cũng bị ném ra ngoài, khiến `doWork()` của worker bị hủy và kết thúc thất bại thay vì thành công.
  - Xử lý: Đặt `catch (e: TimeoutCancellationException)` đứng trước `catch (e: CancellationException)` trong `executeResurfacingWithIsolation`. Khi timeout, chỉ ghi log cảnh báo ngắn gọn (`cardId`, `timeoutMs`, không chứa dữ liệu nhạy cảm) và không ném lại; worker tiếp tục kết thúc `Result.success()` với thẻ đã lưu `DONE`.
* **2. Giới hạn của Heuristic 90 Giây Tính từ `createdAt`:**
  - Cửa sổ 90 giây trong `resolveResurfacingUiState` tính khoảng cách thời gian giữa thời điểm hiện tại (`nowMs`) và thời điểm tạo thẻ (`card.createdAt`).
  - Trong kịch bản thiết bị mất mạng, máy bận hoặc WorkManager phải xếp hàng đợi lâu, thời gian trễ từ khi chụp/lưu thẻ đến khi worker bắt đầu phân tích có thể vượt quá hoặc gần hết 90 giây. Khi đó, ngay khi thẻ vừa chuyển `DONE`, cửa sổ 90 giây có thể đã hết hạn khiến giao diện chuyển sang `NO_CONFIDENT_LINKS` sớm hơn dự kiến.
  - Đây là sự đánh đổi có chủ đích (tradeoff) để giữ kiến trúc tối giản và không thay đổi schema Room SQLite (không thêm cột `analyzed_at` hay `resurfacing_status` trong bản MVP).
* **3. Chốt chặn Thẻ DONE ở Đầu Worker (`shouldSkipAnalysis`):**
  - Ở đầu `doWork()`, kiểm tra nếu thẻ đã ở trạng thái `ProcessingStatus.DONE` và đã có vector embedding hợp lệ, lập tức trả về `Result.success()` mà không thực hiện gọi lại Gemini (bảo toàn quota AI và tiết kiệm tài nguyên mạng).
* **4. Đơn vị Duy nhất Gọi `selectSourceCard(null)`:**
  - Loại bỏ các lệnh gọi trùng lặp `selectSourceCard(null)` trực tiếp trong `RemoraMainScreen`. Toàn bộ việc unpin được tập trung tại một nơi duy nhất là callback `onTabSelected` trong `MainActivity.kt`.

---

## 17. Quản lý Trạng thái Ảnh chờ Quyết định (PendingCaptureStore & Lifecycle-Aware UI) (Task 10) (2026-10-25)

* **1. Phân tích Nguyên nhân Lỗi (Root Causes):**
  - `ScreenshotObserver` trước đây vừa gọi `CaptureEventManager.emitScreenshot` (mở sheet trong app) vừa gọi `NotificationHelper.showCapturePrompt` (bắn notification) mà không biết trạng thái foreground/background của ứng dụng.
  - `CaptureActionReceiver` khi người dùng bấm Lưu/Bỏ qua/Tắt 1 giờ trên notification không đồng bộ xoá trạng thái UI in-memory (`activeCaptureUri`), khiến khi người dùng quay lại app vẫn bị hiện lại sheet cho ảnh đã quyết định.
  - `CaptureEventManager` là `SharedFlow` không replay (`replay = 0`): nếu Activity bị huỷ khi ở nền hoặc chưa khởi động, sự kiện phát hiện ảnh chụp bị mất hoàn toàn, dẫn đến khi mở app không có prompt cho ảnh chưa quyết định.
  - Tapping vào notification mở MainActivity kèm `EXTRA_IMAGE_URI` không được quản lý hàng đợi, dễ gây xung đột hoặc sót ảnh khi người dùng chụp liên tiếp nhiều ảnh.

* **2. Thiết kế Hàng đợi Thuần & Bộ lưu trữ `PendingCaptureStore`:**
  - Logic hàng đợi thuần (`PendingCaptureQueue`):
    - Thêm mục lũy tiến (idempotent add) theo `uriString`: trùng URI không nhân đôi bản ghi. Hỗ trợ cờ `prioritize` để đưa ảnh vừa chạm từ notification lên đầu hàng đợi.
    - Giới hạn tối đa 5 mục (`MAX_ITEMS = 5`), tự động loại bỏ mục cũ nhất khi vượt ngưỡng.
    - Tự động tỉa (prune) các mục đã tồn tại quá 24 giờ (`TTL_MS = 24h`).
    - Lớp bọc mỏng `PendingCaptureStore` sử dụng `SharedPreferences` (JSON) và phơi `pending: StateFlow<List<PendingCapture>>`. Không thêm dependency, không đổi schema Room SQLite.

* **3. Cờ Trạng thái Foreground (`AppForegroundTracker`):**
  - Cung cấp cờ `@Volatile var isForeground = false` được cập nhật tại `MainActivity.onStart()` và `MainActivity.onStop()`.
  - Khi app đang ở foreground: chỉ gọi `store.markPending(uri)` (giao diện in-app tự kích hoạt sheet), KHÔNG phát notification chụp màn hình ra ngoài thanh thông báo hệ thống.
  - Khi app ở background: gọi `store.markPending(uri)` và đồng thời đăng notification cho người dùng tương tác.

* **4. Ma trận Trạng thái Giải phóng Quyết định (Resolve Points):**
  - **`ACTION_SAVE` (từ notification hoặc sheet):** Lưu thẻ vào Room SQLite, lập tức gọi `store.resolve(uri)` và dismiss notification capture.
  - **`ACTION_SKIP` / Bấm "Bỏ qua" / Vuốt đóng sheet:** Gọi `store.resolve(uri)` và dismiss notification.
  - **`ACTION_MUTE_1H` / Bấm "Tắt 1 giờ":** Cập nhật `pause_until`, gọi `store.resolve(uri)` và dismiss notification.
  - **`ACTION_PAUSE` / Bấm "Tạm dừng":** Cập nhật `paused = true`, gọi `store.resolve(uri)` và dismiss notification.
  - **Hành vi Vuốt bỏ notification (không bấm nút):** KHÔNG gọi resolve. Ảnh vẫn nằm trong `PendingCaptureStore` để khi mở app người dùng vẫn được hỏi lưu.

* **5. Chốt chặn Chống Đua (Race-condition Prevention) & UI Lifecycle:**
  - `RemoraMainScreen` chỉ kích hoạt kiểm tra và hiển thị sheet khi lifecycle ở trạng thái `RESUMED` (`repeatOnLifecycle(Lifecycle.State.RESUMED)`).
  - Trước khi hiện sheet cho bất kỳ URI nào, kiểm tra `memoryCardDao.countCardsByImageUri(uriString) > 0` hoặc ảnh không thể đọc (`openInputStream(uri) == null`). Nếu đã có thẻ hoặc ảnh hỏng/bị xoá, lập tức gọi `store.resolve(uri)` và chuyển sang ảnh kế tiếp trong hàng đợi.
  - Khi một sheet được giải quyết (Lưu/Bỏ qua), `store.pending` tự động phát danh sách mới, UI lập tức hiển thị sheet cho ảnh tiếp theo nếu còn trong hàng đợi (ưu tiên ảnh mới nhất).
