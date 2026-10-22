---
title: "Remora Decisions Log & Verified Parameters"
status: ACCEPTED
owner: "Lead"
last_verified: 2026-10-22
related_code: ["services/ai-service", "services/gateway", "core/model"]
---

# Nhật ký Quyết định & Tham số Xác minh (Decisions Log)

Tài liệu này ghi nhận các tham số API, phiên bản thư viện và các quyết định kiến trúc quan trọng trong quá trình phát triển Remora MVP theo quy ước trong [AGENTS.md](../AGENTS.md).

---

## 1. Quyết định về Mô hình AI (Gemini Models & Parameters)

* **Vision / Card Extraction Model ID:** `gemini-3.5-flash` `[VERIFY]`
  * **Trạng thái:** `[VERIFY]` — Cần kiểm chứng trực tiếp bằng request thực tế tới Gemini API trước khi chốt dùng chính thức trong demo/production.
  * **Trách nhiệm:** Nhận ảnh chụp màn hình nén và ghi chú `user_intent`, thực hiện trích xuất thẻ học (`title`, `summary`, `tags`, `content_type`) và OCR toàn bộ văn bản trong ảnh (`ocr_text`) trong **01 API call duy nhất**.
  * **Loại bỏ ML Kit:** Không sử dụng `play-services-mlkit-text-recognition` trên thiết bị để giảm kích thước APK, tránh phụ thuộc vào Google Play Services và đảm bảo chất lượng nhận diện công thức toán/lý từ Gemini.
  * **Chế độ phản hồi:** `response_mime_type = "application/json"` với schema ràng buộc nghiêm ngặt.
  * **Cách kiểm tra tính khả dụng của Model ID:**
    1. Gọi REST API lấy danh sách models với `GEMINI_API_KEY`:
       ```bash
       curl "https://generativelanguage.googleapis.com/v1beta/models?key=${GEMINI_API_KEY}"
       ```
    2. Gửi request test sinh nội dung:
       ```bash
       curl -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${GEMINI_API_KEY}" \
         -H 'Content-Type: application/json' \
         -d '{"contents":[{"parts":[{"text":"ping"}]}]}'
       ```
    3. Nếu mã lỗi là 404 (Model Not Found), kiểm tra danh sách model khả dụng trong tài liệu chính thức của Google AI và cập nhật biến `GEMINI_VISION_MODEL`.

* **Vector Embedding Model ID:** `gemini-embedding-2` `[VERIFY]`
  * **Trạng thái:** `[VERIFY]` — Cần kiểm chứng trực tiếp bằng request thực tế tới Gemini API trước khi chốt dùng chính thức.
  * **Trách nhiệm:** Sinh vector biểu diễn ngữ nghĩa cho chuỗi văn bản được tổng hợp từ: `user_intent` + `tags` + `summary` + `ocr_text` (cắt ngắn tối đa 1000 ký tự).
  * **Kích thước vector embedding:** 768 chiều `[VERIFY]` (cần kiểm chứng độ dài thực tế của mảng float trả về: có thể là 768, 1536 hoặc 3072 tùy thuộc vào phiên bản model và tham số output_dimensionality).
  * **Chuẩn hóa:** Vector được chuẩn hóa L2 trước khi lưu vào Room SQLite dưới dạng BLOB float32 little-endian, cho phép tính Cosine Similarity bằng tích vô hướng (Dot Product).
  * **Cách kiểm tra kích thước embedding:**
    1. Gửi request thử nghiệm `embedContent`:
       ```bash
       curl -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:embedContent?key=${GEMINI_API_KEY}" \
         -H 'Content-Type: application/json' \
         -d '{"content":{"parts":[{"text":"test embedding"}]}}'
       ```
    2. Kiểm tra độ dài của mảng `embedding.values` trong phản hồi JSON (ví dụ qua công cụ `jq '.embedding.values | length'`).
    3. Ghi nhận số chiều thực tế vào file này và cập nhật cấu hình Room Database.

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
