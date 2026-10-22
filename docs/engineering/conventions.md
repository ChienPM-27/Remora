---
title: "Remora Engineering Conventions & Code Guidelines"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["services/gateway", "services/ai-service", "core/model"]
---

# Quy chuẩn Kỹ thuật & Phong cách Lập trình (Engineering Conventions)

Tài liệu này xác định các quy chuẩn bắt buộc về đặt tên, cấu trúc lỗi, phân trang, phiên bản API và định dạng nhật ký áp dụng trên toàn bộ repository Remora (Android Client & Ktor Backend).

---

## 1. Ngôn ngữ & Đặt tên (Language & Naming Conventions)

* **Giao tiếp & Tài liệu:** Trao đổi trong nhóm, thảo luận PR và tài liệu giải thích bằng **tiếng Việt**.
* **Mã nguồn (Codebase):** Tên lớp, biến, hàm, chú thích code (comments) và commit messages bắt buộc viết bằng **tiếng Anh**.
* **Quy tắc đặt tên:**
  * **Kotlin Class / Interface / Enum:** `PascalCase` (ví dụ: `MemoryCard`, `ContentType`, `ResurfaceBatchRequest`).
  * **Functions & Methods:** `camelCase`, bắt đầu bằng động từ (ví dụ: `extractCardMetadata`, `computeCosineSimilarity`, `generateEmbedding`).
  * **Variables & Fields:** `camelCase` (ví dụ: `candidateId`, `userIntent`, `createdAt`).
  * **JSON API Fields & Database Columns:** `snake_case` (ví dụ: `candidate_id`, `user_intent`, `content_type`, `ocr_text`).
  * **Environment Variables:** `SCREAMING_SNAKE_CASE` (ví dụ: `JWT_SECRET`, `GEMINI_API_KEY`, `DEMO_DAILY_QUOTA`).
  * **Constants:** `SCREAMING_SNAKE_CASE` (ví dụ: `MAX_CANDIDATES = 15`, `DEFAULT_EMBEDDING_DIM = 768`).

---

## 2. Chuẩn hóa Phản hồi Lỗi (RFC 9457 Problem Details)

Toàn bộ các phản hồi lỗi HTTP (`4xx` và `5xx`) từ API Gateway và các microservice nội bộ bắt buộc phải trả về theo tiêu chuẩn **RFC 9457 Problem Details** với header `Content-Type: application/problem+json`:

```json
{
  "type": "https://remora.study/errors/quota-exceeded",
  "title": "Daily AI Quota Exceeded",
  "status": 429,
  "code": "QUOTA_EXCEEDED",
  "detail": "Tài khoản demo đã sử dụng hết định mức 50 lượt trích xuất thẻ trong ngày hôm nay.",
  "instance": "/v1/ai/extract-card"
}
```

### 2.1 Bảng mã lỗi ổn định (Stable Error Codes)

| `code` | HTTP Status | Ý nghĩa |
| --- | --- | --- |
| `BAD_REQUEST` | 400 | Payload JSON hoặc định dạng ảnh không hợp lệ |
| `TOKEN_INVALID` | 401 | JWT sai chữ ký hoặc cấu trúc không đọc được |
| `TOKEN_EXPIRED` | 401 | JWT đã hết hạn sử dụng (`exp < now`) |
| `FORBIDDEN` | 403 | Thiếu hoặc sai header `X-Internal-Secret` |
| `NOT_FOUND` | 404 | Không tìm thấy tài nguyên được yêu cầu |
| `RATE_LIMITED` | 429 | Vượt quá tần suất gọi API (theo IP hoặc user) |
| `QUOTA_EXCEEDED` | 429 | Đã chạm trần định mức AI của tài khoản demo |
| `AI_UPSTREAM_ERROR` | 502 | Gemini API trả về lỗi hoặc schema không parse được |
| `SERVICE_UNAVAILABLE`| 503 | CSDL PostgreSQL hoặc service phụ thuộc bị ngắt kết nối |
| `GATEWAY_TIMEOUT` | 504 | Cuộc gọi AI vượt quá giới hạn thời gian (timeout) |

---

## 3. Quản lý Phiên bản API (API Versioning)

* Mọi endpoint công khai và nội bộ đều bắt buộc có tiền tố phiên bản: `/v1`.
* Tuyệt đối không thay đổi kiểu dữ liệu hoặc xóa trường trong `/v1` mà không thông qua cập nhật hợp đồng tại `openapi.yaml`. Mọi breaking change phải nâng lên `/v2`.

---

## 4. Phân trang (Pagination) `[PLANNED]`

Đối với các endpoint danh sách (sau MVP):
* Sử dụng chuẩn Cursor-based Pagination hoặc Limit/Offset:
  * `limit`: Số bản ghi tối đa (mặc định 20, tối đa 50).
  * `cursor`: Con trỏ Base64 trỏ tới bản ghi tiếp theo.
* Cấu trúc trả về:
  ```json
  {
    "items": [...],
    "next_cursor": "eyJpZCI6NDJ9",
    "has_more": true
  }
  ```

---

## 5. Quy chuẩn Commit Message (Conventional Commits)

Toàn bộ commit trong repository phải tuân thủ chuẩn Conventional Commits:
* `feat:` Tính năng mới (ví dụ: `feat: implement stage 2 resurfacing batch evaluator`).
* `fix:` Sửa lỗi (ví dụ: `fix: handle 429 retry-after header in workmanager`).
* `test:` Thêm hoặc sửa bài kiểm thử (ví dụ: `test: add unit test for cosine similarity`).
* `docs:` Cập nhật tài liệu kỹ thuật (ví dụ: `docs: update openapi spec for additionalProperties`).
* `refactor:` Tái cấu trúc mã nguồn không làm đổi tính năng (ví dụ: `refactor: extract token validator to ktor plugin`).
* `chore:` Công việc bảo trì Gradle, Docker, CI/CD (ví dụ: `chore: add memory limits in compose`).
