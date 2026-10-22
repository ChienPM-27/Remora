---
title: "Remora Testing Strategy & Verification Evidence"
status: PLANNED
owner: "QD"
last_verified: 2026-10-21
related_code: ["core/model", "services/gateway", "services/ai-service"]
---

# Chiến lược Kiểm thử & Bằng chứng Thuyết minh (Testing Strategy)

Tài liệu này xác định các cấp độ kiểm thử bắt buộc trong 24h Hackathon và quy trình thu thập bằng chứng kỹ thuật (evidence) để thuyết phục ban giám khảo.

---

## 1. Kim tự tháp Kiểm thử trong 24h Hackathon

```
               [ 1 Manual E2E Demo on Real Device ]
             ----------------------------------------
            [ 1 Compose UI Smoke Test (Save→Card→Search) ]
          --------------------------------------------------
         [ Contract Tests: Spectral OpenAPI 3.1 Validation ]
       --------------------------------------------------------
      [ Integration Tests: Ktor TestApplicationEngine & Room ]
    --------------------------------------------------------------
   [ Unit Tests: Cosine, BLOB, JSON Parsing, Time Resolver (JUnit) ]
```

---

## 2. Các Cấp độ Kiểm thử Bắt buộc (Testing Levels)

### 2.1 Unit Tests (Kiểm thử Logic Thuần - Viết cùng Code từ h2)
Áp dụng JUnit 5 / Kotlin Test cho toàn bộ các hàm xử lý logic toán học và dữ liệu cốt lõi:
1. **Toán học Vector & Chuẩn hóa:**
   - Tính Cosine similarity: Kiểm tra tích vô hướng của 2 vector trực giao (kết quả = 0.0), cùng hướng (kết quả = 1.0), ngược hướng (kết quả = -1.0).
   - Chuẩn hóa L2 (L2-normalization): Kiểm tra độ dài Euclid của vector sau chuẩn hóa luôn bằng `1.0 +/- 1e-6`.
2. **Tuần tự hóa BLOB Float32:**
   - Mã hóa mảng `FloatArray(768)` sang `ByteArray` little-endian và giải mã ngược lại (Round-trip test). Kiểm tra kích thước chính xác 3.072 bytes (`768 * 4`).
3. **Phân tích cú pháp JSON AI (Gemini Response Parser):**
   - Trường hợp JSON hoàn hảo.
   - Trường hợp JSON bị thiếu trường (`tags` bị null hoặc thiếu `subject` -> gán fallback "chưa phân loại").
   - Trường hợp `content_type` là giá trị lạ không thuộc enum -> chuyển thành `UNKNOWN`.
   - Trường hợp JSON malformed/bị cắt ngang -> bắt lỗi an toàn, không làm crash app.
4. **Bộ phân tích thời gian mơ hồ (Time-Range Resolver):**
   - Regex khớp các câu: "hôm qua" (1 ngày trước), "tuần trước" (7-14 ngày trước), "hôm bữa" (2-3 tuần trước).
5. **Xác thực kết quả Resurfacing (Stage 2 Validation):**
   - Loại bỏ bất kỳ `candidate_id` nào trong kết quả không có trong danh sách gửi lên.
   - Giới hạn tối đa 2 kết quả mỗi nhóm (`SIMILAR`, `COMPLEMENTARY`, `ACTIONABLE`).
   - Xử lý mảng `results: []` rỗng thành công.

### 2.2 Contract Tests (Kiểm thử Hợp đồng API)
* Sử dụng **Stoplight Spectral CLI** để kiểm định tự động 2 tệp `openapi.yaml` của `gateway` và `ai-service`.
* Lệnh chạy:
  ```bash
  npx -y @stoplight/spectral-cli lint "services/gateway/openapi.yaml" "services/ai-service/openapi.yaml" --fail-severity warn
  ```
* Tiêu chí hoàn thành: **0 Errors, 0 Warnings**.

### 2.3 Integration Tests (Kiểm thử Tích hợp Service)
* **Ktor Backend:** Sử dụng `io.ktor.server.testing.testApplication` để bắn request giả lập qua router của `gateway` và `ai-service`, kiểm tra xác thực JWT, trừ quota và xử lý mã lỗi HTTP RFC 9457 mà không cần khởi động toàn bộ server thật.
* **Android Room Database:** Sử dụng `Room.inMemoryDatabaseBuilder` để kiểm tra các DAO query, đảm bảo việc chèn thẻ `PENDING` và cập nhật sang `DONE` diễn ra trơn tru.

### 2.4 Smoke Test (Kiểm thử Khói Giao diện)
* Đúng **01 bài Compose UI Smoke Test tự động**: Giả lập luồng chụp màn hình -> bấm Lưu -> kiểm tra thẻ hiển thị trong danh sách -> gõ từ khóa tìm kiếm -> kiểm tra kết quả trả về.
* Toàn bộ phần còn lại được kiểm thử thủ công trực tiếp trên điện thoại thật dùng để demo.

---

## 3. Quy trình Thu thập Bằng chứng cho Ban Giám khảo (Evidence Collection)

Trước khi thuyết trình lúc h22-h24, nhóm bắt buộc phải lưu giữ 4 bằng chứng kỹ thuật:

1. **Bằng chứng CI/CD Xanh (Green CI Run Screenshot):**
   - Chụp màn hình trang GitHub Actions hiển thị toàn bộ pipeline (Lint + Unit Tests + Build APK) đã vượt qua thành công trên nhánh `main`.
2. **Báo cáo Kiểm thử Tự động (HTML Test Report):**
   - File báo cáo kết quả JUnit test (`build/reports/tests/testDebugUnitTest/index.html`) chứng minh 100% các bài test logic thuần chạy thành công.
3. **Video Quay Màn hình Luồng Demo Chính (Backup Screen Recording):**
   - Quay video độ phân giải cao (1080p) luồng demo chạy thật trên điện thoại: Bắt screenshot -> Hiện thông báo -> Nhập intent "Ôn thi giữa kỳ" -> AI phân tích thẻ -> Nhận thông báo Resurfacing 3 nhóm -> Tìm kiếm bằng câu nói tự nhiên.
   - Video này dùng làm phương án dự phòng chiếu ngay lập tức nếu máy chiếu hoặc mạng hội trường gặp sự cố.
4. **Bằng chứng Xác thực Hợp đồng (Spectral Lint Terminal Output):**
   - Chụp lại kết quả chạy lệnh Spectral chứng minh tính tuân thủ tiêu chuẩn OpenAPI 3.1.
