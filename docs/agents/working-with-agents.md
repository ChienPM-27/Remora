---
title: "Remora Working Agreement with Coding Agents"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["AGENTS.md", ".github/pull_request_template.md"]
---

# Quy trình Làm việc với AI Coding Agents (Working with Agents)

Tài liệu này hướng dẫn cách nhóm kỹ sư Remora phối hợp cùng các AI Coding Agents (Antigravity, Claude Code, Cursor) thông qua phương pháp luận **Spec-Driven Development** (kết hợp **GitHub Spec Kit** và **Superpowers**).

---

## 1. Phân định Vai trò: Spec Kit vs Superpowers

Trong dự án Remora, hai công cụ AI được phân công ranh giới rõ ràng:

* **GitHub Spec Kit là Nhà hoạch định Duy nhất (The Single Planner):**
  - Chịu trách nhiệm quy trình chuẩn hóa: `constitution` → `specify` → `plan` → `tasks`.
  - Mọi nhiệm vụ thực thi trong 24h bắt buộc phải xuất phát từ file danh mục nhiệm vụ duy nhất: `tasks.md`.
  - **CẤM** tạo một bản kế hoạch thứ hai ở bất kỳ nơi nào khác.
* **Superpowers là Động cơ Thực thi & Kiểm thử (Execution & Review Engine):**
  - Đảm nhận việc thực thi từng task từ `tasks.md` (qua subagents hoặc inline execution).
  - Kiểm thử, gỡ lỗi (systematic debugging) và rà soát chất lượng mã nguồn (code review).

---

## 2. Quy tắc Bắt buộc cho Agent trước khi Chạm vào Code

Mỗi khi agent bắt đầu một phiên làm việc hoặc nhận task mới:
1. **Đọc kỹ chỉ thị:** Đọc file [`AGENTS.md`](../../AGENTS.md) ở thư mục gốc và file `AGENTS.md` riêng của microservice mà agent chuẩn bị sửa đổi.
2. **Làm đúng 01 task tại một thời điểm:** Không gộp nhiều task lớn vào một lượt làm việc; giữ thay đổi nhỏ gọn đủ cho một Pull Request ngắn.
3. **Triết lý Contract-First:** Mọi thay đổi liên quan đến API endpoint, kiểu dữ liệu JSON hay mã lỗi **bắt buộc phải sửa đổi tệp `openapi.yaml` tương ứng trước**, sau đó cập nhật tài liệu kiến trúc và code trong cùng một PR.
4. **Không suy đoán tham số:** Không nhớ model ID, tham số API từ bộ nhớ; phải kiểm tra tài liệu chính thức hoặc chạy lệnh thử nghiệm thật, ghi nhận vào `docs/decisions.md`.
5. **Kiểm tra thực tế:** Trước khi báo cáo hoàn thành, bắt buộc phải chạy lệnh kiểm thử thực tế và đưa ra output bằng chứng. Tuyệt đối không dùng cụm từ "should work" hay "chắc là chạy được".

---

## 3. Quy trình Đóng góp Pull Request (PR Workflow)

Mọi Pull Request tạo bởi lập trình viên hoặc agent đều phải áp dụng mẫu kiểm tra tại [`.github/pull_request_template.md`](../../.github/pull_request_template.md):
- Xác nhận task ID tương ứng từ Spec Kit `tasks.md`.
- Checklist kiểm tra Unit test và lệnh build xanh.
- Mục bắt buộc: *"Docs updated / ADR needed?"* — Xác nhận tài liệu đã được cập nhật đồng bộ với code hay chưa.

---

## 4. Danh mục Prompt Mẫu (Reusable Prompts)

Các prompt chuẩn hóa dành cho agent được lưu trữ tại thư mục [`docs/agents/prompts/`](prompts/):
- [`add-endpoint.md`](prompts/add-endpoint.md): Quy trình thêm một API endpoint mới (sửa OpenAPI -> lint -> sinh DTO -> implement route -> viết unit test).
- [`add-service.md`](prompts/add-service.md): Quy trình tạo mới một microservice (viết ADR -> cập nhật Service Catalog -> tạo openapi.yaml -> Dockerfile -> compose).
- [`fix-bug.md`](prompts/fix-bug.md): Quy trình sửa lỗi hệ thống (tái lập lỗi bằng bài test -> sửa code -> kiểm tra hồi quy).
