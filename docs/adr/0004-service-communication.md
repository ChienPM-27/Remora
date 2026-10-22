---
title: "ADR 0004: Cơ chế Giao tiếp Liên Dịch vụ (Synchronous HTTP vs Message Queue/gRPC)"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["docs/architecture/01-context-and-containers.md", "services/gateway"]
---

# 0004. Lựa chọn Cơ chế Giao tiếp Liên Dịch vụ: Synchronous HTTP/REST vs Message Queue / gRPC

* **Trạng thái (Status):** Accepted
* **Chủ sở hữu (Owner):** Lead
* **Ngày quyết định (Date):** 2026-10-21
* **Tài liệu liên quan:** [`docs/architecture/01-context-and-containers.md`](file:///d:/MyProject/Remora/docs/architecture/01-context-and-containers.md), [`docs/architecture/02-services-catalog.md`](file:///d:/MyProject/Remora/docs/architecture/02-services-catalog.md)

---

## 1. Bối cảnh & Vấn đề (Context)

Hệ thống backend Remora bao gồm `gateway` tiếp nhận yêu cầu từ client và `ai-service` thực thi các cuộc gọi tới Gemini API. Nhóm cần xác định giao thức và cơ chế truyền thông nội bộ giữa các microservices (Service-to-Service Communication).
Ràng buộc:
- Thời gian thi đấu 24 giờ.
- Tài nguyên máy chủ ảo có hạn (AWS EC2 `t3.small`: 2 vCPU, 2GB RAM).
- Luồng trích xuất thẻ học và Resurfacing đòi hỏi phản hồi có kết quả để client cập nhật trạng thái thẻ từ `PENDING` sang `DONE`.

---

## 2. Các phương án xem xét (Options Considered)

### Phương án A: Đồng bộ qua HTTP/1.1 REST (JSON over HTTP) — **CHOSEN FOR MVP**
* **Mô tả:** `gateway` gọi trực tiếp sang `ai-service` thông qua HTTP POST request (sử dụng Ktor HTTP Client nội bộ), payload được tuần tự hóa dạng JSON qua mạng Docker riêng (`http://ai-service:8082`).
* **Ưu điểm (Pros):**
  * **Cực kỳ trực quan & dễ kiểm thử:** Lập trình viên có thể dùng `curl`, Postman hoặc unit test để bắn trực tiếp request vào từng service riêng lẻ nhằm kiểm tra tính năng.
  * **Không tốn thêm tài nguyên phụ:** Không cần cài đặt và duy trì các tiến trình broker (như RabbitMQ hay Redis) vốn tiêu tốn từ 200MB - 500MB RAM quý giá của máy ảo.
  * **Thống nhất định dạng:** Toàn bộ hệ sinh thái từ Android đến Backend đều dùng chung chuẩn JSON Schema.
  * Thích hợp hoàn hảo cho mô hình Request-Response có timeout của các API phân tích AI.
* **Nhược điểm (Cons):**
  * Ghép nối đồng bộ (Temporal Coupling): Nếu `ai-service` quá tải hoặc tạm thời bị treo, `gateway` sẽ phải giữ kết nối và chờ đợi cho đến khi hết timeout.
  * Không tự động phân phối tải hoặc buffer hàng đợi khi lưu lượng tăng đột biến như Message Queue.

### Phương án B: Bất đồng bộ qua Hàng đợi thông điệp (Message Queue - RabbitMQ / Redis)
* **Mô tả:** `gateway` đẩy job vào queue; `ai-service` là worker lắng nghe queue để xử lý và bắn kết quả lại qua callback hoặc WebSocket/polling.
* **Ưu điểm (Pros):**
  * Khả năng san phẳng đỉnh lưu lượng (Peak shaving), chịu tải cao cực tốt.
  * Tách rời hoàn toàn về mặt thời gian giữa hai dịch vụ.
* **Nhược điểm (Cons):**
  * Làm phức tạp hóa luồng client-server: Client không thể nhận kết quả ngay trong 1 request mà phải duy trì WebSocket hoặc polling liên tục.
  * Rủi ro cài đặt cấu hình broker trong 24h; tốn thêm RAM trên máy chủ.

### Phương án C: gRPC / Protocol Buffers
* **Mô tả:** Định nghĩa dịch vụ bằng file `.proto`, sinh code client/server cho Kotlin và giao tiếp qua HTTP/2 binary.
* **Ưu điểm (Pros):** Tốc độ tuần tự hóa nhị phân cực nhanh, type-safe tuyệt đối.
* **Nhược điểm (Cons):** Phức tạp khi cấu hình Gradle build protoc plugin trong thời gian ngắn; khó debug nhanh bằng mắt thường so với JSON.

---

## 3. Quyết định (Decision)

Lựa chọn chính thức: **Phương án A (Synchronous HTTP/1.1 REST)** cho toàn bộ giao tiếp giữa `gateway` và `ai-service` trong phạm vi **MVP 24h**.

Các nguyên tắc áp dụng:
1. Mọi request nội bộ bắt buộc phải có timeout nghiêm ngặt (tối đa 15 giây cho extraction, 10 giây cho resurfacing).
2. Header truy vết `X-Request-Id` và định danh `X-User-Id` phải được chuyển tiếp xuyên suốt từ `gateway` sang `ai-service`.
3. Bảo mật mạng nội bộ: `ai-service` kiểm tra header nội bộ `X-Internal-Secret` để ngăn chặn request trái phép.

---

## 4. Hệ quả & Tác động (Consequences)

* **Tích cực (Positive):**
  * Giảm thiểu tối đa số lượng thành phần phải cài đặt trên Docker Compose (chỉ có 3 container: `gateway`, `ai-service`, `postgres`).
  * Giữ mức sử dụng RAM toàn hệ thống dưới 1GB, đảm bảo máy ảo `t3.small` hoạt động mát mẻ, không bao giờ bị đứng máy.
* **Tiêu cực & Hạn chế (Negative):**
  * Cần xử lý cẩn thận các trường hợp timeout và lỗi mạng tại `gateway` để trả về mã lỗi HTTP chuẩn RFC 9457 cho client xử lý retry.
* **Rủi ro & Giảm thiểu (Risks & Mitigations):**
  * *Rủi ro:* `ai-service` mất 10-15s để xử lý ảnh nặng khiến `gateway` bị nghẽn connection pool.
  * *Giảm thiểu:* Cấu hình Ktor Netty client pool hợp lý và áp dụng timeout có giới hạn.

---

## 5. Điều kiện kích hoạt đánh giá lại (Revisit Trigger)

Nhóm sẽ cân nhắc bổ sung Message Queue (Redis Streams hoặc RabbitMQ) khi:
1. Hệ thống chuyển sang giai đoạn thương mại hóa có tính năng xử lý hàng loạt trong nền (Bulk background ingestion của hàng trăm sinh viên cùng lúc).
2. Hoặc khi thời gian chờ trung bình tại `gateway` vượt quá 5 giây dưới tải thực tế > 100 requests/phút.
