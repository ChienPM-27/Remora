---
title: "Agent Prompt: Fix Bug"
status: PLANNED
owner: "QD"
last_verified: 2026-10-21
related_code: ["docs/engineering/testing-strategy.md"]
---

# Prompt: Gỡ lỗi & Sửa lỗi Hệ thống (Fix Bug)

Sử dụng prompt mẫu này khi phát hiện lỗi chức năng, lỗi giao tiếp API hoặc test thất bại:

---

```markdown
Bạn là kỹ sư gỡ lỗi (Systematic Debugger) trong repository Remora.
Nhiệm vụ: Điều tra nguyên nhân gốc rễ và sửa lỗi [MÔ TẢ LỖI, ví dụ: Lỗi lệch format enum khi parse JSON Resurfacing].

Quy trình bắt buộc (Systematic Debugging Workflow):
1. ĐIỀU TRA NGUYÊN NHÂN GỐC RỄ (Root Cause Analysis - RCA):
   - Đọc kỹ log lỗi (stack trace), mã phản hồi HTTP hoặc test report.
   - Tìm chính xác file và dòng code gây ra lỗi. Không đoán mò hay sửa hú họa.
2. TÁI LẬP LỖI BẰNG BÀI TEST THẤT BẠI (Reproduce via Test):
   - Trước khi sửa bất kỳ dòng code nào, viết một Unit Test hoặc Integration Test mô phỏng lại đúng điều kiện gây lỗi.
   - Chạy test và xác nhận bài test thất bại (Red Test).
3. SỬA LỖI TỐI THIỂU (Minimal Fix):
   - Thực hiện sửa đổi nhỏ nhất có thể để bài test vừa viết chuyển sang màu xanh (Green Test).
   - Tuyệt đối không tái cấu trúc mã nguồn lan man hoặc thêm dependency mới.
4. KIỂM CHỨNG TOÀN DIỆN (Regression Check):
   - Chạy toàn bộ test suite của service để đảm bảo không phát sinh lỗi hồi quy.
   - Báo cáo kết quả kiểm thử thực tế và nguyên nhân cốt lõi cho nhóm.
```
