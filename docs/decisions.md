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

* **Vision / Card Extraction & Resurfacing Models:**
  * **Primary Model:** `gemini-3.5-flash-lite` `[VERIFIED]`
    * **Kết quả kiểm chứng thực tế:** Phản hồi JSON Schema cấu trúc chuẩn 100%, độ trễ cực nhanh (~2.4s cho trích xuất thẻ học, ~3.0s cho batch Resurfacing 2 tầng).
    * **Khuyến nghị cho Hackathon:** Sử dụng làm model chính thức cho `GEMINI_VISION_MODEL` để đáp ứng hoàn hảo ngân sách độ trễ (< 3-5 giây) và giảm thiểu rủi ro quá tải (503) khi demo trực tiếp.
  * **Secondary / High-Reasoning Model:** `gemini-3.5-flash` `[VERIFIED]`
    * **Kết quả kiểm chứng thực tế:** Khả năng suy luận chi tiết, độ trễ trung bình ~5.8s, đôi lúc gặp lỗi 503 khi server Gemini chịu tải cao.
    * **Vai trò:** Có thể cấu hình làm model nâng cao khi người dùng yêu cầu phân tích sâu các bài toán phức tạp.
  * **Chế độ phản hồi:** `response_mime_type = "application/json"` với schema ràng buộc nghiêm ngặt theo đúng đặc tả tại [AGENTS.md](../AGENTS.md).
  * **Loại bỏ ML Kit:** Không sử dụng `play-services-mlkit-text-recognition` trên thiết bị để giảm kích thước APK, tránh phụ thuộc vào Google Play Services và đảm bảo chất lượng nhận diện công thức toán/lý từ Gemini.

* **Vector Embedding Model:** `gemini-embedding-2` `[VERIFIED]`
  * **Kết quả kiểm chứng thực tế:**
    * Kích thước mặc định của model: **3072 chiều**.
    * Hỗ trợ tham số cấu hình: **`outputDimensionality = 768`** (đã test thực tế trả về chính xác vector 768 chiều).
    * Hỗ trợ đầy đủ tham số `taskType = "RETRIEVAL_DOCUMENT"` (khi lưu trữ thẻ vào CSDL) và `"RETRIEVAL_QUERY"` (khi tìm kiếm câu hỏi).
  * **Quyết định cấu hình:** Sử dụng `gemini-embedding-2` kèm tham số `outputDimensionality = 768` để khớp hoàn hảo với thiết kế Room SQLite BLOB 768d của Remora.
  * **Chuẩn hóa:** Vector được chuẩn hóa L2 trước khi lưu vào Room SQLite dưới dạng BLOB float32 little-endian, cho phép tính Cosine Similarity bằng tích vô hướng (Dot Product).

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
