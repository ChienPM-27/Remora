---
title: "Remora Definition of Done (DoD)"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["AGENTS.md"]
---

# Tiêu chuẩn Hoàn thành (Definition of Done - DoD)

Tài liệu này xác định bảng kiểm (Checklist) bắt buộc mà mọi thành viên và coding agent phải tuân thủ trước khi tuyên bố một Task hoặc một Pull Request đã hoàn thành.

---

## 1. Bảng kiểm Hoàn thành cho Mỗi Task (Per-Task DoD Checklist)

Một đầu việc (Task) chỉ được coi là hoàn thành (`DONE`) khi đáp ứng đầy đủ tất cả các tiêu chí sau:

- [ ] **1. Đáp ứng Tiêu chí Chấp nhận (Acceptance Criteria):** Tính năng hoạt động đúng theo đặc tả chi tiết của task trong `tasks.md`.
- [ ] **2. Có Unit Test kèm theo:** Nếu task chạm vào logic toán học, phân tích cú pháp, mã hóa dữ liệu hoặc phân nhóm thuật toán (Cosine, BLOB, JSON Parser, Time-range Resolver, Resurfacing Evaluator), unit test bắt buộc phải được viết cùng lúc và chạy pass 100%.
- [ ] **3. Lệnh Kiểm thử & Biên dịch Chạy Pass:**
  * Đối với Android: Chạy thực tế và báo cáo kết quả của lệnh:
    ```bash
    ./gradlew lint testDebugUnitTest assembleDebug
    ```
    *(Tuyệt đối không nói "chắc là chạy được" hoặc suy đoán; phải có output thực tế).*
  * Đối với Backend: Chạy unit test Ktor và Spectral lint (nếu có sửa OpenAPI):
    ```bash
    npx -y @stoplight/spectral-cli lint "services/gateway/openapi.yaml" "services/ai-service/openapi.yaml"
    ```
- [ ] **4. Tuân thủ Quyền riêng tư & Bảo mật Bí mật:**
  * Không chứa bất kỳ API key, JWT token thật, password hay secret nào trong code, commit hoặc test.
  * Không log nội dung ảnh, không log câu hỏi tìm kiếm, không log `ocr_text`.
- [ ] **5. Kiểm chứng trên Thiết bị Thật (Real Device Verification):**
  * Đối với các task chạm vào phát hiện screenshot (`MediaStore`), thông báo (`Notification RemoteInput`) hoặc luồng demo chính: Bắt buộc phải cài đặt và chạy thử nghiệm thành công ít nhất 01 lần trên điện thoại Android thật.
- [ ] **6. Cập nhật Tài liệu Kỹ thuật (Docs Updated):**
  * Nếu task có thay đổi cấu trúc bảng, thêm endpoint hoặc thay đổi tham số: Tài liệu kiến trúc tương ứng (`03`, `04`, `05`, `openapi.yaml`) phải được cập nhật đồng bộ trong cùng một PR.
  * Nếu có quyết định mới hoặc giá trị API đã xác minh: Ghi chú ngắn gọn vào `docs/decisions.md` hoặc ADR tương ứng.

---

## 2. Quy tắc Đóng băng Tính năng (Feature Freeze Rule lúc h22)

Sau mốc **h22** của cuộc thi:
* **CẤM** tạo thêm tính năng mới, cấm đổi cấu trúc CSDL hoặc đổi hợp đồng API.
* Mọi PR sau h22 chỉ được phép: sửa lỗi nghiêm trọng (bug fix), cải thiện bài test và chuẩn bị dữ liệu / video demo.
