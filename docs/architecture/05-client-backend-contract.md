---
title: "Remora Client-Backend Contract & Offline Resilience"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["app", "services/gateway", "core/model"]
---

# 05. Hợp đồng Tương tác Client - Backend & Cơ chế Chịu lỗi Ngoại tuyến

Tài liệu này xác định các cam kết kỹ thuật (SLA/Contract) giữa Ứng dụng Android và API Gateway, cơ chế hàng đợi bất đồng bộ WorkManager, chiến lược thử lại (Retry Policy) và xử lý ngoại lệ mạng.

---

## 1. Nguyên tắc cốt lõi: UI không bao giờ bị chặn (Non-Blocking UI) `[PLANNED]`

Để đảm bảo trải nghiệm tức thì trên điện thoại khi sinh viên học bài:
1. Khi sinh viên bấm **Lưu (Save)** trên thông báo hoặc ứng dụng:
   - Bản ghi thẻ học được chèn ngay vào Room SQLite với trạng thái:
     `status = PENDING`, `attempt_count = 0`.
   - Giao diện người dùng (Gallery, Recent Cards) hiển thị thẻ bài học ngay lập tức với nhãn "Đang phân tích AI...".
2. Hệ thống đẩy một công việc (OneTimeWorkRequest) vào **WorkManager**:
   - Ràng buộc: `NetworkType.CONNECTED`.
   - Công việc này chạy ngầm độc lập với vòng đời của Activity/Giao diện.
3. Khi WorkManager gọi backend thành công:
   - Dữ liệu thẻ được cập nhật (`title`, `summary`, `tags`, `content_type`, `ocr_text`, `embedding`).
   - Cột `status` chuyển thành `DONE`.
   - Giao diện Room `Flow<List<MemoryCard>>` tự động phát tín hiệu cập nhật mà không cần người dùng vuốt tải lại.

---

## 2. Chiến lược Thử lại & Xử lý Mã lỗi (Retry Policy & Error Handling) `[PLANNED]`

```
[Request Gửi từ Client]
       ↓
[Thất bại: Mất mạng hoặc Timeout (>20s)] → Giữ PENDING → WorkManager Retry Backoff (10s, 30s, 60s)
       ↓
[Thất bại: 429 Too Many Requests / Quota] → Đọc Retry-After → Giữ PENDING → Delay WorkManager
       ↓
[Thất bại: 4xx Lỗi dữ liệu / Schema hỏng] → Chuyển FAILED ngay lập tức (không retry vô hạn)
       ↓
[Số lần thử vượt quá ngưỡng (attempt_count >= 3)] → Chuyển FAILED → Hiện nút "Thử lại bằng tay" trên UI
```

### 2.1 Bảng quy ước phản hồi lỗi & Hành vi của Client

| Mã trạng thái HTTP | Nguyên nhân | Header đi kèm | Hành vi của Client Android |
| --- | --- | --- | --- |
| **`200 OK`** | Thành công | - | Lưu dữ liệu, chuyển thẻ sang `status = DONE`. |
| **`400 Bad Request`** | Dữ liệu sai định dạng | Problem JSON | Chuyển `status = FAILED`, ghi nhận `error_code = BAD_REQUEST`. |
| **`401 Unauthorized`** | Token hết hạn hoặc sai | Problem JSON | Tự động gọi endpoint `/v1/auth/login-demo` để lấy token mới rồi thử lại 1 lần duy nhất; nếu vẫn lỗi thì yêu cầu đăng nhập lại. |
| **`429 Too Many Requests`** | Vượt Rate limit hoặc Quota AI | `Retry-After: <giây>` | Giữ thẻ ở trạng thái `PENDING`. Lập lịch WorkManager chạy lại sau khoảng thời gian `Retry-After` (mặc định 60 giây). **Không bao giờ làm crash app**. |
| **`500 / 502 / 503`** | Lỗi máy chủ hoặc DB quá tải | Problem JSON | Tăng `attempt_count += 1`. Nếu `attempt_count < 3`: xếp hàng thử lại với Exponential Backoff. Nếu `>= 3`: chuyển `status = FAILED`. |
| **`504 Gateway Timeout`** | Gemini xử lý quá lâu (>15s) | Problem JSON | Thử lại qua WorkManager với backoff. |
| **Mất kết nối mạng (Offline)** | Điện thoại mất Wifi/4G | - | Giữ `PENDING`. WorkManager tự động kích hoạt khi có mạng trở lại nhờ ràng buộc `NetworkType.CONNECTED`. |

---

## 3. Khử trùng lặp & Tiết kiệm Chi phí (Deduplication & Idempotency) `[PLANNED]`

Để tối ưu chi phí token Gemini và tốc độ phản hồi:
1. **Kiểm tra trùng lặp bằng hàm băm (Hash Check):**
   - Trước khi gửi ảnh lên backend, client tính chuỗi hash SHA-256 của file ảnh.
   - Nếu trong Room SQLite đã có một thẻ cùng hash và `status == DONE`, client tái sử dụng luôn dữ liệu phân tích và vector cũ, không gọi backend thêm một lần nào nữa.
2. **Nén ảnh trước khi tải lên (Client Compression):**
   - Ảnh chụp màn hình gốc thường có độ phân giải 2K-4K dung lượng 3MB - 8MB.
   - Client tự động nén và resize ảnh (chiều dài tối đa 1560px, chuẩn nén JPEG chất lượng 80-85%, dung lượng còn ~300KB - 500KB) trước khi gửi qua API. Giúp giảm 80% độ trễ truyền dữ liệu mạng.

---

## 4. Hợp đồng Dữ liệu Resurfacing (Stage 2 Resurfacing Rules) `[PLANNED]`

Khi client gọi endpoint `/v1/ai/resurface-batch`:

### 4.1 Bảng dữ liệu rời khỏi thiết bị theo từng Endpoint (Data Egress Matrix)

| Endpoint | Dữ liệu rời khỏi thiết bị | Dữ liệu KHÔNG BAO GIỜ rời khỏi thiết bị | Ghi chú bảo mật |
| --- | --- | --- | --- |
| **`POST /v1/auth/login-demo`** | `username`, `password` | Thông tin cá nhân thực tế | Chỉ dùng tài khoản demo cấu hình qua biến môi trường. Rate limit theo IP. |
| **`POST /v1/ai/extract-card`** | File ảnh nén JPEG/PNG + chuỗi `user_intent` | Các ảnh khác trong thư viện, vị trí địa lý | Chỉ gửi sau khi người dùng chủ động bấm Lưu. |
| **`POST /v1/ai/embeddings`** | Chuỗi văn bản cần sinh vector: ghép từ `user_intent`, `tags`, `summary`, và `ocr_text` (cắt tối đa 1.000 ký tự); hoặc câu hỏi truy vấn tìm kiếm của người dùng | File ảnh gốc, định danh cá nhân | Endpoint độc lập, phục vụ cả lưu thẻ và tìm kiếm ngữ nghĩa. |
| **`POST /v1/ai/resurface-batch`** | Thẻ mới (`title`, `summary`, `tags`, `content_type`, `user_intent`) + Danh sách top-N ứng viên (chỉ gồm `candidate_id`, `title`, `summary`, `tags`, `user_intent`, `content_type`) | **KHÔNG gửi file ảnh của thẻ cũ**<br>**KHÔNG gửi `ocr_text` của thẻ cũ** | Schema áp dụng `additionalProperties: false` để từ chối mọi trường ngoài danh mục. |

### 4.2 Dữ liệu gửi đi cho Resurfacing (Request Payload)
- **Thẻ mới (`new_card`):** Gửi đúng 5 trường metadata văn bản:
  - `title`: Tiêu đề thẻ mới (String).
  - `summary`: Tóm tắt thẻ mới (String).
  - `tags`: Nhãn môn học & chủ đề (`{ "subject": String, "topics": [String] }`).
  - `content_type`: Loại nội dung (`THEORY`, `PROBLEM`, `SOLUTION`, `CODE`, `NOTES`, `UNKNOWN`).
  - `user_intent`: Ghi chú lúc chụp của thẻ mới (String, có thể null).
  - *(Không gửi ảnh, không gửi `ocr_text`).*
- **Danh sách N ứng viên (`candidates` - top-15 từ Cosine similarity):**
  Mỗi ứng viên gửi **đúng 6 trường metadata văn bản:**
  - `candidate_id`: ID của thẻ cũ trong Room SQLite (Int/Long).
  - `title`: Tiêu đề thẻ cũ.
  - `summary`: Tóm tắt thẻ cũ.
  - `tags`: Nhãn môn học & chủ đề thẻ cũ.
  - `content_type`: Loại nội dung thẻ cũ.
  - `user_intent`: Ghi chú lúc chụp của thẻ cũ.
  - *(TUYỆT ĐỐI KHÔNG GỬI `ocr_text` VÀ KHÔNG GỬI FILE ẢNH CỦA BẤT KỲ THẺ NÀO).*

### 4.3 Các cam kết bắt buộc của Backend đối với kết quả trả về
1. **Bảo toàn ID (Candidate ID Guarantee):**
   Mọi `candidate_id` trong mảng kết quả `results` trả về bắt buộc phải nằm trong danh sách các ID đã gửi lên. Client tự động loại bỏ (drop) bất kỳ kết quả nào có `candidate_id` không tồn tại trong danh sách ứng viên ban đầu.
2. **Giới hạn số lượng (Group Ceiling):**
   Mỗi nhóm (`SIMILAR`, `COMPLEMENTARY`, `ACTIONABLE`) có **tối đa 2 kết quả**.
3. **Danh sách rỗng hợp lệ (Empty Results is Valid):**
   Nếu không có ứng viên nào đạt ngưỡng tin cậy, backend trả về `"results": []`. Client chấp nhận danh sách rỗng là kết quả hợp lệ, không coi đây là lỗi.
4. **Ngưỡng tin cậy cấu hình được (`RESURFACE_MIN_CONFIDENCE`) `[PLANNED]`:**
   Client chỉ lưu và hiển thị gợi ý nếu `confidence > RESURFACE_MIN_CONFIDENCE`. Giá trị mặc định ban đầu là `0.80` (sử dụng điều kiện `>` thay vì `>=`), được cấu hình linh hoạt trong cài đặt hoặc build config để tinh chỉnh tối ưu dựa trên dữ liệu thực tế của bộ ảnh demo.
5. **Khớp nhóm và quan hệ (Relation-Group Conformance):**
   Mô hình AI chỉ trả về gợi ý khi có lợi ích học tập cụ thể, trong đó `relation` phải khớp chính xác với `group`:
   - `SIMILAR`: `relation` bắt buộc là `DUPLICATE` hoặc `SUPPLEMENT`.
   - `COMPLEMENTARY`: `relation` bắt buộc là `EXPLANATION` hoặc `SUPPLEMENT`.
   - `ACTIONABLE`: `relation` bắt buộc là `SUPPLEMENT` hoặc `CONTRADICTION`.
