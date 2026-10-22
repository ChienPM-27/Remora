# AGENTS.md: AI Orchestration Service

Chỉ thị và ranh giới bắt buộc dành cho AI Coding Agents làm việc bên trong dịch vụ `services/ai-service`.
Giao tiếp với nhóm bằng tiếng Việt. Viết mã nguồn, định danh, ghi chú và commit messages bằng tiếng Anh.

---

## 1. Ranh giới Dịch vụ (Service Boundaries)

### Những gì ĐƯỢC PHÉP gọi:
* Gọi ra Google Gemini API (HTTPS REST) bằng `GEMINI_API_KEY`.
* Nhận HTTP requests từ `gateway` thông qua mạng nội bộ Docker (`http://ai-service:8082`).
* Kiểm tra header nội bộ `X-Internal-Secret` để xác thực request xuất phát từ `gateway`.

### Những gì TUYỆT ĐỐI CẤM (Never Do):
* **CẤM mở cổng công khai ra ngoài Internet.** Service này chỉ được phép lắng nghe trong Docker private network `remora-net`.
* **CẤM kết nối trực tiếp vào cơ sở dữ liệu `postgres`** (hoàn toàn Stateless đối với dữ liệu người dùng).
* **CẤM ghi file ảnh, file nhị phân hoặc văn bản OCR ra ổ đĩa máy chủ.** Toàn bộ xử lý phải thực hiện trực tiếp trong bộ nhớ RAM và giải phóng ngay sau khi phản hồi.
* **CẤM gọi vòng lặp LLM N lần trong Resurfacing.** Bắt buộc phải gộp toàn bộ danh sách ứng viên vào 1 prompt duy nhất cho Stage 2.
* **CẤM sửa đổi contract mà không cập nhật `services/ai-service/openapi.yaml` trước.**

---

## 2. Quy trình Thêm Endpoint AI Mới (Checklist)

1. Mở `services/ai-service/openapi.yaml`, khai báo endpoint và schema dữ liệu (kèm `additionalProperties: false`).
2. Kiểm tra linter:
   ```bash
   npx -y @stoplight/spectral-cli lint "services/ai-service/openapi.yaml" --fail-severity warn
   ```
3. Tạo prompt template có versioning rõ ràng (ví dụ: `PromptTemplates.V1.EXTRACTION_SYSTEM_INSTRUCTION`).
4. Viết bài unit test cho bộ parser JSON với 4 kịch bản: JSON hợp lệ, JSON thiếu trường, JSON sai enum, JSON malformed.
5. Triển khai code trong Ktor và kiểm tra `./gradlew :ai-service:test`.

---

## 3. Lệnh Kiểm thử & Xây dựng (Commands)

```bash
# Chạy Unit Tests của AI Service
cd services
./gradlew :ai-service:test

# Chạy Linter kiểm tra OpenAPI contract
npx -y @stoplight/spectral-cli lint "services/ai-service/openapi.yaml"
```

---

## 4. Quy tắc Ghi Nhật ký & Bảo vệ Quyền riêng tư (Logging & Privacy)

* Chỉ ghi mã `request_id`, `user_id` định danh, tên model Gemini, số token tiêu thụ, độ trễ `latency_ms` và mã HTTP.
* **Tuyệt đối CẤM ghi chuỗi `ocr_text`, chuỗi base64 ảnh hay nội dung tóm tắt học tập của sinh viên vào log.**
