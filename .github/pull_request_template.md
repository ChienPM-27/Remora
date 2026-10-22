## Summary

<!-- Mô tả ngắn gọn thay đổi và mục đích của Pull Request -->

- **Spec Kit Task ID:** `Task-XX` (từ `tasks.md`)
- **Loại thay đổi (Type of change):**
  - [ ] `feat`: Tính năng mới
  - [ ] `fix`: Sửa lỗi
  - [ ] `test`: Thêm/sửa bài kiểm thử
  - [ ] `docs`: Cập nhật tài liệu kỹ thuật
  - [ ] `refactor`: Tái cấu trúc không đổi tính năng

---

## Definition of Done Checklist

- [ ] **Acceptance Criteria:** Đáp ứng đầy đủ tiêu chí chấp nhận của task.
- [ ] **Unit Tests:** Đã viết unit test cho logic thuần (Cosine, BLOB, JSON parsing, Time resolver, Resurfacing) và toàn bộ bài test đều PASS.
- [ ] **Lệnh kiểm thử & Build thực tế:**
  - Android: `./gradlew lint testDebugUnitTest assembleDebug` đã chạy và PASS (dán output bên dưới).
  - Backend (nếu có sửa): `./gradlew test` và Spectral lint đã PASS.
- [ ] **Bảo mật & Quyền riêng tư:**
  - [ ] Không có bất kỳ API key, secret, password hay token nào trong code hay commit.
  - [ ] Không ghi log ảnh, không log câu hỏi tìm kiếm, không log `ocr_text`.
- [ ] **Kiểm chứng Thiết bị Thật:** Đã kiểm thử chạy thành công ít nhất 1 lần trên điện thoại thật (bắt buộc đối với MediaStore, Notification, hoặc luồng demo).
- [ ] **Tài liệu Kỹ thuật (Docs Updated / ADR Needed?):**
  - [ ] Đã cập nhật hợp đồng `openapi.yaml` (nếu có thay đổi API).
  - [ ] Đã cập nhật tài liệu kiến trúc tương ứng (`docs/architecture/` hoặc `docs/deployment/`).
  - [ ] Đã ghi chú vào ADR hoặc `docs/decisions.md` (nếu có quyết định kiến trúc mới).

---

## Verification Evidence (Bằng chứng Kiểm thử)

```text
<!-- Dán terminal output thực tế của lệnh kiểm thử/build tại đây -->
```
