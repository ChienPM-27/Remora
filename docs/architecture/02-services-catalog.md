---
title: "Remora Services Catalog"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["services/gateway", "services/ai-service"]
---

# 02. Danh mục dịch vụ (Services Catalog)

Tài liệu này cung cấp bảng tra cứu toàn diện và thông tin chi tiết về từng service trong hệ thống microservices của Remora.

---

## 1. Bảng tổng hợp danh mục dịch vụ (Service Matrix) `[PLANNED]`

| Service | Trách nhiệm chính (Responsibility) | Owner | Phụ thuộc (Dependencies) | Dữ liệu quản lý (Data Owned) | Cổng (Port) | Hiển thị mạng (Visibility) | MVP Slice | Trạng thái |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **`gateway`** *(kèm module `auth` nội bộ)* | Cổng vào duy nhất; kiểm tra token JWT; rate limit & trừ quota AI theo user; sinh `X-Request-Id`; định tuyến HTTP; cấp token demo qua module `auth` tích hợp sẵn. | `AI` / `Lead` | `postgres`, `ai-service` | Không sở hữu DB riêng (chỉ đọc/ghi quota & audit log trên `postgres`) | `8080` (HTTP nội bộ sau TLS Terminator) | Public (Internet) | **Yes** | `[PLANNED]` |
| **`ai-service`** | Giữ Gemini API Key; trích xuất thẻ học (OCR + JSON); sinh embedding vector; đánh giá quan hệ Resurfacing theo lô; chuẩn hóa schema. | `AI` | Google Gemini API | Không lưu trữ (Stateless hoàn toàn đối với nội dung người dùng) | `8082` (HTTP) | Internal Only (Docker net) | **Yes** | `[PLANNED]` |
| **`postgres`** | CSDL quan hệ lưu trữ tài khoản demo, thông tin hạn mức sử dụng (quota) và nhật ký truy cập audit. | `Lead` | Không có | Schema `remora_auth`, bảng `users`, `user_quotas`, `audit_logs` | `5432` (TCP) | Internal Only (Docker net) | **Yes** | `[PLANNED]` |
| **`auth-service`** | Dịch vụ định danh độc lập, OAuth2, SSO, quản trị người dùng nâng cao. *(Tách ra từ module `auth` của `gateway`)* | `Lead` | `postgres` | Bảng `users`, `refresh_tokens`, `sessions` | `8083` (HTTP) | Internal Only | **No** (Post-MVP) | `[PLANNED]` |
| **`core-service`** | Đồng bộ thẻ nhớ đa thiết bị; sao lưu thẻ nhớ lên cloud; tìm kiếm vector bằng `pgvector`. | `Lead` | `postgres` | Bảng thẻ nhớ đám mây, vector embedding dự phòng | `8081` (HTTP) | Internal Only (Docker net) | **No** (Post-MVP) | `[PLANNED]` |

---

## 2. Chi tiết từng dịch vụ (Service Breakdown)

### 2.1 Service: `gateway` (kèm module `auth` nội bộ) `[PLANNED]`
* **Công nghệ:** Kotlin + Ktor (Netty engine).
* **Mục đích:** Là tấm khiên bảo vệ toàn bộ cụm dịch vụ phía sau, chịu trách nhiệm xác thực, phân quyền, kiểm soát lưu lượng và chuyển tiếp request an toàn.
* **Cơ chế Module Auth trong MVP:**
  * Trong MVP 24h, chức năng xác thực (`auth`) nằm **trực tiếp bên trong tiến trình của `gateway`** dưới dạng một Ktor Route module (`/v1/auth/*`), **không tạo microservice riêng `services/auth`**.
  * Cả hai dùng chung bí mật `JWT_SECRET` để ký và xác thực token HMAC-SHA256 trong cùng một bộ nhớ.
  * **Điểm tách (Split Point):** Khi hệ thống bước sang Post-MVP, hỗ trợ nhiều nhà cung cấp định danh (Google Sign-In, Apple ID) hoặc đạt quy mô ước tính vượt ~1.000 DAU, module `auth` sẽ được tách ra thành service độc lập `auth-service` chạy trên cổng `8083`, chuyển sang cơ chế cặp khóa bất đối xứng (Ed25519 hoặc RS256 qua JWKS).
* **Các trách nhiệm chính:**
  1. **Minimal Auth Module:** Cung cấp endpoint `/v1/auth/login-demo` để cấp phát Self-issued JWT cho tài khoản sinh viên thử nghiệm.
  2. **JWT Authentication Filter:** Kiểm tra tính hợp lệ của access token trong header `Authorization: Bearer <token>` trên mọi route được bảo vệ.
  3. **Rate Limiting & AI Quota Guard:** Giới hạn số request/phút (ví dụ: 60 req/min) và số lượng phân tích AI tối đa trong 24h của mỗi sinh viên (tránh cạn kiệt ngân sách Gemini).
  4. **Request Tracing:** Tự động tạo và gắn header `X-Request-Id` nếu client chưa có, truyền xuyên suốt qua `ai-service` và ghi vào log có cấu trúc.
  5. **Reverse Proxy & Routing:** Chuyển tiếp `/v1/ai/*` sang `ai-service:8082`.
* **Cổng mạng:** `8080` (HTTP nội bộ sau TLS Terminator, hoặc nhận trực tiếp nếu chạy debug cục bộ).
* **Các biến môi trường bắt buộc:**
  * `PORT=8080`
  * `JWT_SECRET`: Khóa bí mật dùng để ký và xác thực JWT HMAC-SHA256.
  * `DATABASE_URL`: Đường dẫn kết nối PostgreSQL (`jdbc:postgresql://postgres:5432/remora_db`).
  * `DB_USER` / `DB_PASSWORD`: Tài khoản truy cập DB.
  * `AI_SERVICE_URL`: URL nội bộ của AI service (`http://ai-service:8082`).
  * `INTERNAL_SERVICE_KEY`: Khóa xác thực chia sẻ dùng khi gọi sang các dịch vụ nội bộ.

---

### 2.2 Service: `ai-service` `[PLANNED]`
* **Công nghệ:** Kotlin + Ktor (Netty engine).
* **Mục đích:** Đóng gói toàn bộ logic tương tác với Google Gemini AI, quản lý prompt theo phiên bản (Prompt Versioning), xác thực JSON Schema trả về và tính toán chi phí token.
* **Các trách nhiệm chính:**
  1. **Trích xuất thẻ nhớ (`/v1/ai/extract-card`):** Nhận ảnh nhị phân/base64 và `user_intent`, gọi `gemini-3.5-flash-lite` (hoặc fallback sang `gemini-3.5-flash`) ở chế độ JSON Structured Output với schema cố định (`title`, `summary`, `tags`, `content_type`, `ocr_text`).
  2. **Tạo Vector Embedding (`/v1/ai/embeddings`):** Nhận văn bản đã tổng hợp (`user_intent + tags + summary + ocr_text`), gọi `gemini-embedding-2` để nhận vector 768 chiều.
  3. **Đánh giá Memory Resurfacing theo lô (`/v1/ai/resurface-batch`):** Nhận thông tin thẻ mới và danh sách tối đa 15 ứng viên (chỉ text metadata), gọi LLM 1 lần duy nhất để phân loại quan hệ thành 3 nhóm (`SIMILAR`, `COMPLEMENTARY`, `ACTIONABLE`).
  4. **Không lưu trữ nội dung (Stateless):** Tuyệt đối không ghi ảnh hay văn bản người dùng vào ổ đĩa hoặc cơ sở dữ liệu.
* **Cổng mạng:** `8082` (Internal Only - Không mở ra ngoài host).
* **Các biến môi trường bắt buộc:**
  * `PORT=8082`
  * `GEMINI_API_KEY`: Khóa API Google Gemini.
  * `GEMINI_VISION_MODEL`: Mặc định `gemini-3.5-flash-lite`.
  * `GEMINI_VISION_FALLBACK_MODEL`: Mặc định `gemini-3.5-flash`.
  * `GEMINI_EMBEDDING_MODEL`: Mặc định `gemini-embedding-2`.
  * `INTERNAL_SERVICE_KEY`: Khóa kiểm tra request đến từ `gateway`.

---

### 2.3 Service: `postgres` `[PLANNED]`
* **Công nghệ:** Docker image `postgres:16-alpine`.
* **Mục đích:** Lưu trữ dữ liệu xác thực, hạn ngạch sử dụng và nhật ký hoạt động nội bộ.
* **Cơ chế khởi tạo:** File SQL kịch bản khởi tạo `init.sql` được mount vào `/docker-entrypoint-initdb.d/` trong Docker Compose. **Không sử dụng Flyway trong MVP 24h** để tối ưu thời gian dựng hệ thống.
* **Các bảng dữ liệu sở hữu:**
  * `users`: `id`, `username`, `display_name`, `created_at`, `status`.
  * `user_quotas`: `user_id`, `daily_ai_limit`, `used_today`, `last_reset_date`.
  * `audit_logs`: `id`, `request_id`, `user_id`, `route`, `status_code`, `latency_ms`, `created_at`.
* **Cổng mạng:** `5432` (Internal Only).

---

### 2.4 Service: `core-service` `[PLANNED - Post-MVP]`
* **Mục đích:** Phục vụ tính năng đồng bộ đám mây và tìm kiếm vector trên server cho các thiết bị phụ hoặc ứng dụng nền web.
* **Thời điểm kích hoạt:** Khi dự án phát triển sau cuộc thi Hackathon. Lúc này sẽ bổ sung công cụ quản trị migration (Flyway) và cài đặt extension `pgvector` trên PostgreSQL.

---

## 3. Ma trận giao tiếp dịch vụ (Service Communication Matrix) `[PLANNED]`

| Bên gọi (Caller) | Bên nhận (Callee) | Giao thức & Cổng | Cơ chế bảo vệ (Auth) | Mục đích | Hành vi khi lỗi |
| --- | --- | --- | --- | --- | --- |
| **Android Client** | `gateway` | HTTPS / `8080` | `Authorization: Bearer <JWT>` | Gọi trích xuất thẻ, embedding, resurfacing | Retry exponential backoff tối đa 3 lần; chuyển thẻ thành `PENDING` nếu 429 hoặc rớt mạng. |
| **`gateway`** | `postgres` | JDBC / `5432` | Username / Password | Xác thực user demo, kiểm tra và trừ quota AI | Nếu DB lỗi, từ chối request với mã lỗi RFC 9457 `503 Service Unavailable`. |
| **`gateway`** | `ai-service` | HTTP / `8082` | Header `X-Internal-Secret` + `X-User-Id` | Chuyển tiếp payload phân tích và sinh vector | Timeout 15s; trả về `504 Gateway Timeout` hoặc `429` tương ứng. |
| **`ai-service`** | Google Gemini API | HTTPS / `443` | Header `x-goog-api-key` | Gọi mô hình AI Gemini | Timeout 12s; retry backoff 1 lần nếu gặp lỗi mạng tạm thời; bắt lỗi schema fallback. |
