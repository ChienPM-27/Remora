---
title: "ADR 0001: Microservices Tinh gọn vs Modular Monolith"
status: ACCEPTED
owner: "Lead"
last_verified: 2026-10-22
related_code: ["AGENTS.md", "docs/architecture/01-context-and-containers.md"]
---

# 0001. Lựa chọn Kiến trúc Backend: Microservices Tinh gọn vs Modular Monolith

* **Trạng thái (Status):** Accepted
* **Chủ sở hữu (Owner):** Lead
* **Ngày quyết định (Date):** 2026-10-21
* **Tài liệu liên quan:** [`AGENTS.md`](../../AGENTS.md), [`01-context-and-containers.md`](../architecture/01-context-and-containers.md)

---

## 1. Bối cảnh & Vấn đề (Context)

Remora là ứng dụng Android với mô hình AI-native yêu cầu xử lý thị giác (Vision OCR, trích xuất cấu trúc thẻ học) và sinh vector embeddings qua Google Gemini API. Ứng dụng client trên điện thoại không được phép nắm giữ khóa bí mật `GEMINI_API_KEY`.
Yêu cầu đề tài là xây dựng hệ thống backend microservices kèm API Gateway xác thực. Tuy nhiên, thời gian thi đấu Hackathon chỉ vỏn vẹn **24 giờ**, với mục tiêu cao nhất là demo mượt mà trên thiết bị thật trước ban giám khảo. Nhóm cần quyết định cấu trúc backend để vừa đáp ứng đúng tiêu chí kiến trúc microservices phân ranh giới rõ ràng, vừa không làm nổ tung độ phức tạp vận hành và triển khai trong 24h.

---

## 2. Các phương án xem xét (Options Considered)

### Phương án A: Modular Monolith (Một ứng dụng Ktor duy nhất)
* **Mô tả:** Gom toàn bộ logic xác thực, proxy, rate limiting và gọi Gemini API vào chung một ứng dụng Ktor duy nhất, chia theo các module gói nội bộ (`package auth`, `package gateway`, `package ai`).
* **Ưu điểm (Pros):**
  * Rất nhanh khi khởi tạo ban đầu, chỉ cần 1 Dockerfile duy nhất.
  * Không có độ trễ mạng nội bộ (in-process calls).
  * Tiết kiệm RAM tối đa trên máy ảo cloud (chỉ cần 1 JVM runtime).
* **Nhược điểm (Cons):**
  * Không thể hiện rõ ranh giới mạng cô lập bảo mật cho `GEMINI_API_KEY` (khóa nằm chung bộ nhớ với gateway công cộng).
  * Khó mở rộng độc lập khi muốn scale riêng phần xử lý AI nặng.
  * Không đúng với định hướng kiến trúc microservices và hợp đồng giao tiếp giữa các dịch vụ.

### Phương án B: Lightweight Microservices (Gateway + AI-Service riêng biệt) — **CHOSEN**
* **Mô tả:** Tách thành 2 microservices độc lập chạy trên Docker Compose:
  1. `gateway`: Đóng vai trò Reverse Proxy, Public Entry, xác thực JWT, kiểm soát quota và rate limit.
  2. `ai-service`: Nằm hoàn toàn trong mạng nội bộ riêng tư (Private Docker Network), giữ `GEMINI_API_KEY`, chuyên trách giao tiếp Gemini và chuẩn hóa JSON Schema.
  *(Dịch vụ `core-service` phục vụ sync dữ liệu được quy hoạch ở giai đoạn Post-MVP).*
* **Ưu điểm (Pros):**
  * **Bảo mật tối đa (Zero-Trust ranh giới ngoài):** `GEMINI_API_KEY` chỉ tồn tại trong `ai-service`; `gateway` mở ra ngoài Internet hoàn toàn không biết API key này.
  * **Độc lập triển khai:** Khi cần cập nhật prompt template của `ai-service` hoặc thay đổi model Gemini, có thể build và restart riêng container `ai-service` mà không làm ngắt kết nối hay mất phiên đăng nhập của `gateway`.
  * **Đúng chuẩn hợp đồng:** Ép buộc các service phải giao tiếp qua HTTP API contract chuẩn mực (OpenAPI 3.1).
* **Nhược điểm (Cons):**
  * Tốn thêm tài nguyên bộ nhớ (chạy 2 tiến trình JVM Ktor trên 1 máy ảo).
  * Thêm chi phí độ trễ mạng nội bộ (khoảng ~1-3ms giữa 2 container trong cùng máy chủ).

---

## 3. Quyết định (Decision)

Lựa chọn chính thức: **Phương án B (Lightweight Microservices)** gồm `gateway` và `ai-service`, phối hợp cùng CSDL `postgres` được đóng gói trong cùng một file `docker-compose.yml`.

Lý do:
1. Đảm bảo đúng kiến trúc Microservices + Auth Gateway theo yêu cầu đề bài.
2. Cách ly bảo mật tuyệt đối cho `GEMINI_API_KEY` trong mạng nội bộ, đúng quy tắc trong [`AGENTS.md`](../../AGENTS.md).
3. Kiến trúc đủ gọn (chỉ 2 service chính) để hoàn thành và vận hành trơn tru trong 24h trên máy ảo demo EC2 `t3.medium` (hoặc `t3.small` dự phòng).

---

## 4. Hệ quả & Tác động (Consequences)

* **Tích cực (Positive):**
  * Ranh giới phân chia công việc cực kỳ rõ ràng: Dev phụ trách AI tập trung vào `ai-service` (prompt, schema, batching); Dev phụ trách hạ tầng tập trung vào `gateway` (auth, rate limit, quota).
  * Đáp ứng hoàn hảo tiêu chí đánh giá kiến trúc của ban giám khảo.
* **Tiêu cực & Hạn chế (Negative):**
  * Cần cấu hình JVM options hợp lý (`gateway`: `-Xmx256m`, `ai-service`: `-Xmx384m`) để hệ thống hoạt động ổn định và an toàn trên EC2 `t3.medium` (hoặc `t3.small` dự phòng có 2GB swap).
* **Rủi ro & Giảm thiểu (Risks & Mitigations):**
  * *Rủi ro:* Cấu hình mạng Docker Compose bị lỗi khiến `gateway` không gọi được `ai-service`.
  * *Giảm thiểu:* Dùng chung bridge network `remora-net` với DNS nội bộ theo tên service (`http://ai-service:8082`).

---

## 5. Điều kiện kích hoạt đánh giá lại (Revisit Trigger)

Nhóm sẽ họp đánh giá lại để xem xét gộp hoặc tách thêm service khi:
1. Máy ảo EC2 bị cạn bộ nhớ RAM (Memory usage > 85%) do chi phí của 2 JVM runtime.
2. Hoặc khi chuyển sang giai đoạn thương mại hóa có quy mô ước tính vượt ~5.000 DAU, cần tách thêm `auth-service` độc lập và đưa `core-service` vào hoạt động.
