# AI Orchestration Service (`ai-service`)

Dịch vụ nội bộ chuyên trách điều phối và giao tiếp độc quyền với Google Gemini API, thực hiện trích xuất thẻ học có cấu trúc, sinh vector embeddings và đánh giá quan hệ Memory Resurfacing theo lô.

* **Chủ sở hữu (Owner):** AI
* **Trạng thái:** `[PLANNED]`
* **Thư mục mã nguồn:** `services/ai-service/`
* **Hợp đồng API:** [`openapi.yaml`](openapi.yaml)

---

## 1. Trách nhiệm & Ranh giới (Responsibilities & Non-Responsibilities)

### Trách nhiệm (Responsibilities):
* Là dịch vụ **duy nhất nắm giữ `GEMINI_API_KEY`** và thực hiện các cuộc gọi ra Google Gemini API.
* Trích xuất thẻ học (`/v1/ai/extract-card`): Gọi `gemini-3.5-flash-lite` (fallback `gemini-3.5-flash`) ở chế độ JSON Structured Output với JSON Schema cố định, đọc OCR văn bản và tạo thẻ.
* Sinh vector embeddings (`/v1/ai/embeddings`): Gọi `gemini-embedding-2` để nhận vector 768 chiều.
* Đánh giá Stage 2 Resurfacing theo lô (`/v1/ai/resurface-batch`): Gom toàn bộ tối đa 15 ứng viên vào đúng 1 prompt duy nhất gửi tới Gemini để phân loại vào 3 nhóm (`SIMILAR`, `COMPLEMENTARY`, `ACTIONABLE`).
* Kiểm tra header nội bộ `X-Internal-Secret` để ngăn chặn truy cập trái phép.
* Đo lường chi phí token và độ trễ từng lần gọi AI.

### Không chịu trách nhiệm (Non-Responsibilities):
* **Không mở cổng công cộng ra ngoài Internet** (chỉ lắng nghe trong mạng nội bộ Docker `remora-net`).
* **Hoàn toàn không lưu trữ dữ liệu (Stateless):** Không lưu ảnh, không lưu text OCR, không có database riêng.
* **Không xử lý xác thực người dùng** (việc này do `gateway` đảm nhiệm).

---

## 2. Bảng biến môi trường (Environment Variables)

| Tên biến | Bắt buộc | Ví dụ giá trị | Là bí mật? | Mục đích |
| --- | --- | --- | --- | --- |
| `PORT` | Có | `8082` | Không | Cổng HTTP nội bộ mà service lắng nghe |
| `GEMINI_API_KEY` | Có | `<YOUR_GEMINI_API_KEY>` | **Có** | Khóa API chính thức của Google Gemini |
| `GEMINI_VISION_MODEL` | Không | `gemini-3.5-flash-lite` | Không | Model chính dùng cho Vision OCR & Extraction |
| `GEMINI_VISION_FALLBACK_MODEL` | Không | `gemini-3.5-flash` | Không | Model dự phòng khi model chính lỗi/quá tải |
| `GEMINI_EMBEDDING_MODEL`| Không | `gemini-embedding-2` | Không | Model dùng để sinh vector embedding |
| `INTERNAL_SERVICE_KEY` | Có | `<GENERATE_WITH_OPENSSL_RAND_HEX_32>` | **Có** | Khóa bí mật nội bộ đối chiếu với `gateway` |
| `GEMINI_TIMEOUT_MS` | Không | `12000` | Không | Giới hạn thời gian chờ phản hồi từ Gemini |

---

## 3. Hướng dẫn Chạy & Kiểm thử Cục bộ (Run Locally & Test)

### Chạy trực tiếp bằng Gradle:
```bash
cd services
./gradlew :ai-service:run
```

### Chạy qua Docker Compose:
```bash
docker compose up -d ai-service
```

### Kiểm thử nhanh bằng `curl`:
```bash
# 1. Kiểm tra Liveness
curl http://localhost:8082/v1/healthz

# 2. Thử nghiệm sinh embedding nội bộ
curl -X POST http://localhost:8082/v1/ai/embeddings \
  -H "Content-Type: application/json" \
  -H "X-Internal-Secret: <INTERNAL_SERVICE_KEY>" \
  -H "X-User-Id: usr_demo_student_01" \
  -H "X-Request-Id: req_test_001" \
  -d '{"text":"Phân phối chuẩn Gaussian"}'
```

---

## 4. Điểm giám sát Sức khỏe (Health Endpoints)

* `GET /v1/healthz`: Trả về `{"status":"UP"}` nếu tiến trình Ktor Netty đang chạy.
* `GET /v1/readyz`: Trả về `{"status":"READY", "gemini_api":"CONFIGURED"}` nếu đã nạp thành công `GEMINI_API_KEY`.

---

## 5. Chế độ Suy thoái khi Gặp Lỗi (Degradation Modes)

* **Khi Gemini API trả về lỗi hoặc JSON Schema bị thiếu trường:** Bộ parser của `ai-service` áp dụng cơ chế tự làm sạch (Sanitization):
  * Giữ nguyên các trường hợp lệ.
  * Nếu thiếu môn học: gán `tags.subject = "chưa phân loại"`.
  * Nếu `content_type` không hợp lệ: gán thành `UNKNOWN`.
  * Tuyệt đối không làm sập tiến trình hoặc vứt bỏ ảnh của người dùng.
* **Khi Gemini API trả về mã lỗi 429 (Hết hạn ngạch Gemini):** Trả về ngay mã `429` cho `gateway` kèm `Retry-After` để phía Android đưa vào hàng đợi WorkManager.
