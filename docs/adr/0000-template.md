# 0000. Tiêu đề quyết định kiến trúc (ADR Title)

* **Trạng thái (Status):** Proposed | Accepted | Superseded | Deprecated
* **Chủ sở hữu (Owner):** [Tên vai trò / người chịu trách nhiệm, ví dụ: Lead, AI]
* **Ngày quyết định (Date):** YYYY-MM-DD
* **Tài liệu / Mã nguồn liên quan:** `[Đường dẫn file liên quan]`

---

## 1. Bối cảnh & Vấn đề (Context)

Mô tả ngắn gọn bối cảnh kỹ thuật, yêu cầu nghiệp vụ và lý do nhóm phải đưa ra quyết định này. Nêu rõ các ràng buộc chính (ví dụ: thời gian 24h Hackathon, ngân sách cloud, giới hạn thiết bị, bảo mật).

---

## 2. Các phương án xem xét (Options Considered)

Phải đưa ra ít nhất 02 phương án kiến trúc khả thi để cân nhắc:

### Phương án A: [Tên phương án A]
* **Mô tả:** Cách thức tiếp cận và triển khai kỹ thuật.
* **Ưu điểm (Pros):**
  * Ưu điểm 1
  * Ưu điểm 2
* **Nhược điểm (Cons):**
  * Nhược điểm 1
  * Nhược điểm 2

### Phương án B: [Tên phương án B]
* **Mô tả:** Cách thức tiếp cận và triển khai kỹ thuật.
* **Ưu điểm (Pros):**
  * Ưu điểm 1
  * Ưu điểm 2
* **Nhược điểm (Cons):**
  * Nhược điểm 1
  * Nhược điểm 2

---

## 3. Quyết định (Decision)

Lựa chọn chính thức: **Phương án [A/B]** được chọn làm kiến trúc chuẩn cho Remora.
Giải thích lý do lựa chọn phương án này dựa trên phân tích đánh đổi (trade-offs) và tính tương thích với mục tiêu dự án.

---

## 4. Hệ quả & Tác động (Consequences)

* **Tích cực (Positive):** Những lợi ích kỹ thuật và tiến độ đạt được ngay lập tức.
* **Tiêu cực & Hạn chế (Negative):** Những gánh nặng kỹ thuật hoặc tính năng bị hoãn lại.
* **Rủi ro & Giảm thiểu (Risks & Mitigations):** Các rủi ro tiềm ẩn và kế hoạch phòng ngừa cụ thể.

---

## 5. Điều kiện kích hoạt đánh giá lại (Revisit Trigger)

Một điều kiện đo lường được bằng số liệu hoặc sự kiện cụ thể (measurable condition) mà khi đạt tới, nhóm bắt buộc phải họp lại để đánh giá lại quyết định này và có thể thay đổi kiến trúc.
*(Ví dụ: "Khi số lượng người dùng vượt quá 1.000 DAU", "Khi chi phí cloud vượt quá $50/tháng", hoặc "Khi chuyển sang giai đoạn Post-MVP có tính năng sync đa thiết bị").*
