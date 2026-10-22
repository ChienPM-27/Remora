# AGENTS.md: Gateway Service

Chỉ thị và ranh giới bắt buộc dành cho AI Coding Agents làm việc bên trong dịch vụ `services/gateway`.
Giao tiếp với nhóm bằng tiếng Việt. Viết mã nguồn, định danh, ghi chú và commit messages bằng tiếng Anh.

---

## 1. Ranh giới Dịch vụ (Service Boundaries)

### Những gì ĐƯỢC PHÉP gọi:
* Đọc/ghi bảng xác thực và hạn mức (`users`, `user_quotas`, `audit_access_logs`) trên CSDL `postgres` qua JDBC.
* Gọi HTTP POST sang `http://ai-service:8082` thông qua mạng nội bộ Docker.
* Ký và giải mã token JWT bằng `JWT_SECRET` lấy từ biến môi trường.

### Những gì TUYỆT ĐỐI CẤM (Never Do):
* **CẤM gọi trực tiếp ra Google Gemini API.** Mọi yêu cầu liên quan đến AI bắt buộc phải chuyển tiếp sang `ai-service`.
* **CẤM nắm giữ hoặc đọc biến môi trường `GEMINI_API_KEY`.**
* **CẤM lưu trữ nội dung ảnh, file nhị phân hoặc `ocr_text` vào cơ sở dữ liệu.**
* **CẤM tự sinh ngẫu nhiên `JWT_SECRET` trong bộ nhớ khi khởi động.** Bắt buộc phải đọc từ biến môi trường.
* **CẤM sửa đổi hành vi route mà không cập nhật file `openapi.yaml` trước.**

---

## 2. Quy trình Thêm Endpoint Mới (Checklist)

1. Mở `services/gateway/openapi.yaml`, khai báo endpoint theo chuẩn OpenAPI 3.1.
2. Kiểm tra linter:
   ```bash
   npx -y @stoplight/spectral-cli lint "services/gateway/openapi.yaml" --fail-severity warn
   ```
3. Cập nhật tài liệu client contract tại `docs/architecture/05-client-backend-contract.md`.
4. Viết bài kiểm thử route bằng Ktor Test Engine.
5. Triển khai code trong Ktor và kiểm tra `./gradlew :gateway:test`.

---

## 3. Lệnh Kiểm thử & Xây dựng (Commands)

```bash
# Chạy Unit Tests của Gateway
cd services
./gradlew :gateway:test

# Chạy Linter kiểm tra OpenAPI contract
npx -y @stoplight/spectral-cli lint "services/gateway/openapi.yaml"
```

---

## 4. Quy tắc Ghi Nhật ký & Bảo mật (Logging & Privacy)

* Mọi request đi qua Gateway bắt buộc phải sinh hoặc chuyển tiếp header `X-Request-Id`.
* Định dạng log JSON ra `stdout` có đầy đủ: `timestamp`, `level`, `service: "gateway"`, `request_id`, `user_id`, `route`, `status`, `latency_ms`.
* **Tuyệt đối không log mật khẩu, token thô, nội dung câu hỏi tìm kiếm hay payload ảnh.**
