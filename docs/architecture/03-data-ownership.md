---
title: "Remora Data Ownership & Governance"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["core/model", "services/gateway", "services/ai-service"]
---

# 03. Phân định Quyền Sở hữu Dữ liệu (Data Ownership)

Tài liệu này xác định ranh giới sở hữu dữ liệu (Data Boundaries), quyền đọc/ghi, chính sách lưu giữ (Retention Policy) và phân loại dữ liệu nhạy cảm/PII giữa Ứng dụng Android và Cụm Microservices Backend.

---

## 1. Phân định tổng quan giữa Client và Backend `[PLANNED]`

Remora tuân thủ nguyên tắc **Bảo vệ quyền riêng tư học tập (Privacy by Design)**:
- **Thiết bị di động (Client):** Sở hữu toàn bộ nội dung học tập, tài liệu bài giảng, ảnh chụp màn hình, văn bản OCR và vector biểu diễn ngữ nghĩa.
- **Máy chủ đám mây (Backend):** Chỉ sở hữu dữ liệu tài khoản, hạn ngạch sử dụng (quota) và nhật ký kỹ thuật phục vụ vận hành. **Backend tuyệt đối không lưu trữ ảnh, file nhị phân hay nội dung `ocr_text` trong cơ sở dữ liệu**.

---

## 2. Dữ liệu do Client sở hữu (On-Device Room SQLite) `[PLANNED]`

*(Mã nguồn hiện tại ở `:core:model` `[IMPLEMENTED]` đang dùng tên `Screenshot`, sẽ được refactor theo bảng dưới đây).*

### 2.1 Bảng `memory_card`
* **Vị trí lưu trữ:** Room SQLite trên bộ nhớ an toàn của thiết bị Android.
* **Quyền:** Chỉ Client Android có quyền Đọc (Read) và Ghi (Write). Backend không bao giờ có quyền truy cập trực tiếp.
* **Cấu trúc dữ liệu:**
  * `id`: `INTEGER PRIMARY KEY AUTOINCREMENT`
  * `image_uri`: `TEXT NOT NULL` (URI cục bộ dạng `content://media/external/images/media/...` hoặc `file://`)
  * `created_at`: `INTEGER NOT NULL` (Timestamp epoch ms lúc chụp)
  * `user_intent`: `TEXT` (Ghi chú lý do người dùng lưu ảnh, ví dụ: "Ôn thi cuối kỳ")
  * `title`: `TEXT NOT NULL` (Tiêu đề ngắn gọn do AI sinh, <= 80 ký tự)
  * `summary`: `TEXT NOT NULL` (Tóm tắt 2-4 câu trọng tâm, giữ nguyên công thức và số liệu)
  * `tags_json`: `TEXT NOT NULL` (JSON `{ "subject": "string", "topics": ["string"] }`)
  * `content_type`: `TEXT NOT NULL` (Enum: `THEORY | PROBLEM | SOLUTION | CODE | NOTES | UNKNOWN`)
  * `ocr_text`: `TEXT NOT NULL` (Toàn bộ văn bản đọc được trong ảnh do Gemini OCR trích xuất)
  * `embedding`: `BLOB` (Mảng float32 768 chiều, little-endian liên tục, đã chuẩn hóa L2)
  * `status`: `TEXT NOT NULL` (Enum: `PENDING | DONE | FAILED`)
  * `attempt_count`: `INTEGER DEFAULT 0` (Số lần đã thử gọi AI)
  * `error_code`: `TEXT` (Mã lỗi khi thất bại, ví dụ: `TIMEOUT`, `QUOTA_EXCEEDED`, `NETWORK_OFFLINE`)

### 2.2 Bảng `resurfacing_result`
* **Vị trí lưu trữ:** Room SQLite cục bộ.
* **Cấu trúc dữ liệu:**
  * `id`: `INTEGER PRIMARY KEY AUTOINCREMENT`
  * `new_card_id`: `INTEGER NOT NULL` (Khóa ngoại tham chiếu `memory_card.id` mới)
  * `candidate_card_id`: `INTEGER NOT NULL` (Khóa ngoại tham chiếu `memory_card.id` cũ được gợi ý)
  * `group`: `TEXT NOT NULL` (Enum: `SIMILAR | COMPLEMENTARY | ACTIONABLE`)
  * `relation`: `TEXT NOT NULL` (Enum: `DUPLICATE | SUPPLEMENT | EXPLANATION | CONTRADICTION`)
  * `new_info`: `TEXT` (Thông tin mới nhận diện được giữa hai thẻ)
  * `benefit`: `TEXT` (Lợi ích mang lại khi kết nối 2 kiến thức)
  * `reason`: `TEXT NOT NULL` (Lời giải thích ngắn gọn hiển thị cho sinh viên)
  * `confidence`: `REAL NOT NULL` (Điểm tin cậy từ 0.0 đến 1.0)
  * `created_at`: `INTEGER NOT NULL`

### 2.3 Bảng `app_setting`
* `id`: `INTEGER PRIMARY KEY` (Row ID cố định `1L`)
* `consent_granted`: `BOOLEAN NOT NULL DEFAULT FALSE` (Người dùng đã chấp thuận chính sách onboarding / quyền riêng tư; độc lập với cờ thông báo)
* `notifications_enabled`: `BOOLEAN NOT NULL DEFAULT TRUE` (Bật/tắt thông báo gợi nhớ kiến thức resurfacing alert)
* `paused`: `BOOLEAN NOT NULL DEFAULT FALSE` (Tạm dừng phát hiện và nhắc lưu ảnh chụp màn hình vô thời hạn)
* `pause_until`: `INTEGER NOT NULL DEFAULT 0` (Thời điểm epoch timestamp ms kết thúc tạm dừng có thời hạn, ví dụ nút "Tắt 1 giờ")

---

## 3. Dữ liệu do Backend sở hữu (PostgreSQL Backend) `[PLANNED]`

Toàn bộ các bảng này nằm trong PostgreSQL container (`postgres:16-alpine`), do service `gateway` (kèm module `auth`) trực tiếp đọc/ghi.

### 3.1 Bảng `users`
* `id`: `VARCHAR(64) PRIMARY KEY` (ví dụ: `usr_demo_student_01`)
* `username`: `VARCHAR(50) UNIQUE NOT NULL`
* `password_hash`: `VARCHAR(255) NOT NULL` (bcrypt/argon2 hash của tài khoản demo)
* `display_name`: `VARCHAR(100)`
* `role`: `VARCHAR(20) DEFAULT 'STUDENT'`
* `status`: `VARCHAR(20) DEFAULT 'ACTIVE'`
* `created_at`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP`

### 3.2 Bảng `user_quotas`
* `user_id`: `VARCHAR(64) PRIMARY KEY REFERENCES users(id)`
* `daily_card_limit`: `INTEGER DEFAULT 50` (Giới hạn số thẻ phân tích/ngày trong demo)
* `used_today`: `INTEGER DEFAULT 0`
* `resurface_batch_limit`: `INTEGER DEFAULT 100`
* `resurface_used_today`: `INTEGER DEFAULT 0`
* `last_reset_date`: `DATE DEFAULT CURRENT_DATE`

### 3.3 Bảng `audit_access_logs`
* `id`: `BIGSERIAL PRIMARY KEY`
* `request_id`: `VARCHAR(64) NOT NULL`
* `user_id`: `VARCHAR(64)`
* `route`: `VARCHAR(100) NOT NULL`
* `status_code`: `INTEGER NOT NULL`
* `latency_ms`: `INTEGER NOT NULL`
* `created_at`: `TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP`
* *(Cam kết bảo mật: Bảng này chỉ lưu trữ các trường định danh kỹ thuật trên; tuyệt đối KHÔNG có cột lưu nội dung câu hỏi tìm kiếm, không lưu payload body, không lưu ảnh hay văn bản OCR).*

---

## 4. Ranh giới dữ liệu rời khỏi thiết bị (Data In-Transit & Privacy Boundaries) `[PLANNED]`

Nhóm cam kết tính minh bạch tuyệt đối về việc dữ liệu nào được phép rời khỏi thiết bị di động:

| Hành động | Dữ liệu gửi lên Backend | Dữ liệu KHÔNG BAO GIỜ gửi lên Backend | Thời điểm gửi |
| --- | --- | --- | --- |
| **Phân tích thẻ mới (`/v1/ai/extract-card`)** | 1. Ảnh nén tạm thời (để Gemini đọc)<br>2. Ghi chú `user_intent` | Metadata thiết bị, các ảnh khác trong máy | **Chỉ sau khi** người dùng chủ động bấm **Lưu** trên thông báo. |
| **Tạo Embedding (`/v1/ai/embeddings`)** | Chuỗi văn bản tổng hợp: `user_intent + tags + summary + ocr_text` (cắt tối đa 1.000 ký tự) | File ảnh gốc, định danh cá nhân | Ngay sau khi nhận kết quả phân tích thẻ. |
| **Gợi nhớ Resurfacing (`/v1/ai/resurface-batch`)** | 1. Thẻ mới gồm 5 trường văn bản: `title`, `summary`, `tags`, `content_type`, `user_intent`<br>2. **Mỗi thẻ ứng viên cũ gồm đúng 6 trường metadata văn bản:** `candidate_id`, `title`, `summary`, `tags`, `content_type`, `user_intent` | **TUYỆT ĐỐI KHÔNG GỬI ẢNH CỦA THẺ MỚI HOẶC CŨ**<br>**TUYỆT ĐỐI KHÔNG GỬI `ocr_text` CỦA THẺ MỚI HOẶC CŨ** | Sau khi tính Cosine top-15 trên máy. |

---

## 5. Chính sách lưu giữ & Vòng đời dữ liệu (Retention & Lifecycle) `[PLANNED]`

1. **Trên Client Android:**
   - Dữ liệu `memory_card` được lưu vĩnh viễn trên máy cho đến khi sinh viên chủ động bấm "Xóa thẻ" hoặc xóa dữ liệu ứng dụng.
   - Thẻ ở trạng thái `FAILED` được lưu giữ cùng mã lỗi để sinh viên có thể bấm "Thử lại" bất kỳ lúc nào.
2. **Tại Backend & Bộ nhớ đệm:**
   - Ảnh tải lên `ai-service` được xử lý trực tiếp trong stream bộ nhớ RAM (In-memory buffer), gửi sang Gemini API và lập tức giải phóng (Garbage Collected), không ghi tạm ra file trên ổ cứng.
   - Bảng `audit_access_logs` trong PostgreSQL được tự động xoay vòng hoặc xóa các bản ghi cũ hơn 7 ngày.
