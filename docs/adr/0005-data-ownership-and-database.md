---
title: "ADR 0005: Phân định Quyền Sở hữu Dữ liệu & Cơ sở Dữ liệu (PostgreSQL Init SQL vs Flyway)"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["AGENTS.md", "docs/architecture/03-data-ownership.md"]
---

# 0005. Phân định Quyền Sở hữu Dữ liệu & Cơ sở Dữ liệu: PostgreSQL Init SQL vs Flyway Migration

* **Trạng thái (Status):** Accepted
* **Chủ sở hữu (Owner):** Lead
* **Ngày quyết định (Date):** 2026-10-21
* **Tài liệu liên quan:** [`AGENTS.md`](../../AGENTS.md), [`00-overview.md`](../architecture/00-overview.md), [`02-services-catalog.md`](../architecture/02-services-catalog.md)

---

## 1. Bối cảnh & Vấn đề (Context)

Một trong những cam kết cốt lõi của Remora là **Bảo vệ quyền riêng tư học tập của sinh viên** (Privacy First). Đồng thời, nhóm phải đối mặt với áp lực thời gian 24 giờ của Hackathon, cần quyết định:
1. Dữ liệu nào thuộc quyền sở hữu của thiết bị di động (Client-owned), dữ liệu nào thuộc quyền sở hữu của máy chủ backend (Server-owned)?
2. Phương pháp quản lý và khởi tạo lược đồ cơ sở dữ liệu trên PostgreSQL backend (dùng kịch bản SQL ban đầu `init.sql` hay cài đặt công cụ migration chuyên nghiệp như Flyway/Liquibase)?

---

## 2. Các phương án xem xét (Options Considered)

### Phương án A: PostgreSQL khởi tạo bằng SQL Scripts (`init.sql`) — **CHOSEN FOR MVP**
* **Mô tả:**
  * **Phân định quyền sở hữu dữ liệu:** Thiết bị di động của sinh viên sở hữu 100% nội dung học tập: file ảnh screenshot, văn bản `ocr_text`, tóm tắt, nhãn môn học và vector embeddings (lưu trữ hoàn toàn trong Room SQLite cục bộ). Backend **tuyệt đối không lưu trữ ảnh, file nhị phân hay nội dung `ocr_text`**.
  * **Dữ liệu tại Backend:** Chỉ lưu trữ thông tin tài khoản demo (`users`), hạn ngạch sử dụng AI (`user_quotas`), và nhật ký audit (`audit_logs`).
  * **Cơ chế khởi tạo:** Một file kịch bản SQL duy nhất (`init.sql`) được mount vào thư mục `/docker-entrypoint-initdb.d/` của container `postgres:16-alpine`. Khi Docker Compose khởi động lần đầu, PostgreSQL tự động chạy file này để tạo bảng và nạp dữ liệu mẫu demo. **Không sử dụng công cụ migration (Flyway/Liquibase) trong giai đoạn 24h MVP**.
* **Ưu điểm (Pros):**
  * **Bảo vệ quyền riêng tư tuyệt đối:** Không có nguy cơ rò rỉ dữ liệu đề thi/bài giải của sinh viên từ phía máy chủ; server không tốn dung lượng lưu trữ ổ đĩa để chứa file ảnh.
  * **Tốc độ triển khai cực nhanh:** Tiết kiệm khoảng 45 phút thiết lập plugin Gradle Flyway, cấu hình versioning và sửa lỗi checksum khi làm gấp.
  * Khởi tạo CSDL tức thì trong 1 lệnh `docker compose up`.
* **Nhược điểm (Cons):**
  * Thiếu khả năng tự động cập nhật lược đồ (Schema Migration) khi có thay đổi cấu trúc bảng mà không muốn xóa volume cơ sở dữ liệu.

### Phương án B: Lưu trữ tập trung toàn bộ dữ liệu trên Cloud (Centralized Cloud DB)
* **Mô tả:** Lưu trữ toàn bộ ảnh trên S3/Cloud Storage, lưu `ocr_text` và vector lên PostgreSQL Cloud.
* **Ưu điểm (Pros):** Dễ dàng xem dữ liệu từ web dashboard.
* **Nhược điểm (Cons):** Vi phạm cam kết quyền riêng tư trong [`AGENTS.md`](../../AGENTS.md); tốn băng thông, chi phí lưu trữ lớn và tăng nguy cơ vi phạm bảo mật.

### Phương án C: Sử dụng Flyway / Liquibase ngay từ MVP
* **Mô tả:** Viết các migration scripts dạng `V1__init.sql`, `V2__add_quota.sql` và dùng Flyway để migrate khi ứng dụng Ktor khởi động.
* **Ưu điểm (Pros):** Quản lý phiên bản CSDL chặt chẽ.
* **Nhược điểm (Cons):** Không cần thiết cho một CSDL MVP chỉ có 3 bảng tĩnh; làm chậm tiến độ khởi động dự án trong 24h.

---

## 3. Quyết định (Decision)

Lựa chọn chính thức: **Phương án A (PostgreSQL khởi tạo bằng SQL Scripts `init.sql`)** cho phạm vi **MVP 24h**.

Quy tắc bất di bất dịch:
1. Backend không bao giờ lưu trữ ảnh học tập và `ocr_text` của người dùng.
2. PostgreSQL chỉ quản lý danh tính demo và hạn ngạch quota AI.
3. Không cài đặt công cụ migration trong 24h đầu.

---

## 4. Hệ quả & Tác động (Consequences)

* **Tích cực (Positive):**
  * Kiến trúc gọn gàng, khởi động Docker Compose cực kỳ đáng tin cậy và tái lập được (reproducible).
  * Điểm cộng lớn trước ban giám khảo về tiêu chí tôn trọng quyền riêng tư người dùng.
* **Tiêu cực & Hạn chế (Negative):**
  * Nếu nhóm thay đổi cấu trúc bảng của `users` hoặc `user_quotas` trong quá trình thi đấu, giải pháp đơn giản nhất là xóa Docker volume (`docker compose down -v`) và khởi chạy lại.
* **Rủi ro & Giảm thiểu (Risks & Mitigations):**
  * *Rủi ro:* Lược đồ DB bị thay đổi đột xuất gây lệch pha.
  * *Giảm thiểu:* Khóa chặt lược đồ 3 bảng của MVP (`users`, `user_quotas`, `audit_logs`) ngay từ Phase 1 và ghi rõ trong tài liệu hợp đồng.

---

## 5. Điều kiện kích hoạt đánh giá lại & Thêm Migration Tool (Revisit Trigger)

Nhóm **bắt buộc phải bổ sung công cụ Schema Migration (Flyway hoặc Liquibase)** khi:
1. **Kích hoạt triển khai `core-service` (Post-MVP):** Khi hệ thống bắt đầu hỗ trợ tính năng đồng bộ đám mây và lưu trữ bản sao thẻ nhớ/vector đa thiết bị lên PostgreSQL.
2. Khi có dữ liệu người dùng thực tế không thể xóa volume để tạo lại.
