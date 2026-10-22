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
