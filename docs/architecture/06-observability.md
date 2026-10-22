---
title: "Remora Observability & Monitoring Architecture"
status: PLANNED
owner: "QD"
last_verified: 2026-10-21
related_code: ["services/gateway", "services/ai-service"]
---

# 06. Khả năng Quan sát & Giám sát Hệ thống (Observability)

Tài liệu này xác định các tiêu chuẩn ghi log có cấu trúc (Structured Logging), cơ chế truyền vết phân tán bằng `X-Request-Id`, các điểm kiểm tra sức khỏe hệ thống và định hướng theo dõi số liệu hiệu năng.

---

## 1. Tiêu chuẩn Ghi Log có Cấu trúc (JSON Structured Logging) `[PLANNED]`

Mọi microservice trong hệ sinh thái Remora (`gateway`, `ai-service`) bắt buộc phải xuất log ra `stdout` dưới định dạng JSON với các trường chuẩn hóa sau:

```json
{
  "timestamp": "2026-10-21T14:30:00.123Z",
  "level": "INFO",
  "service": "gateway",
  "request_id": "req_8f1a2c3d-e4b5-4a6c-9d8e-7f0a1b2c3d4e",
  "user_id": "usr_demo_student_01",
  "route": "/v1/ai/extract-card",
  "status": 200,
  "latency_ms": 1420,
  "message": "Card extraction completed successfully"
}
```

### 1.1 Bảng đặc tả các trường log bắt buộc

| Trường | Kiểu | Ví dụ | Ý nghĩa |
| --- | --- | --- | --- |
| `timestamp` | String (ISO 8601) | `2026-10-21T14:30:00.123Z` | Thời điểm ghi log tính bằng UTC |
| `level` | String | `DEBUG`, `INFO`, `WARN`, `ERROR` | Mức độ nghiêm trọng của log |
| `service` | String | `gateway`, `ai-service` | Tên của microservice phát sinh log |
| `request_id` | String | `req_8f1a2c3d...` | Mã định danh duy nhất xuyên suốt request |
| `user_id` | String | `usr_demo_student_01` (hoặc `anonymous`) | Định danh người dùng đã xác thực |
| `route` | String | `/v1/ai/resurface-batch` | Endpoint HTTP đang được xử lý |
| `status` | Integer | `200`, `429`, `504` | Mã trạng thái HTTP trả về |
| `latency_ms` | Integer | `350` | Thời gian xử lý từ lúc nhận đến lúc trả kết quả |
| `message` | String | `Batch resurfacing evaluated` | Thông điệp kỹ thuật ngắn gọn |

### 1.2 Cam kết Quyền riêng tư trong Nhật ký (Privacy Rules for Logging)
> [!CAUTION]
> **QUY TẮC BẤT KHẢ XÂM PHẠM:**
> - Tuyệt đối **KHÔNG ghi nội dung văn bản `ocr_text`** vào log.
> - Tuyệt đối **KHÔNG ghi nội dung câu hỏi tìm kiếm** của sinh viên vào log.
> - Tuyệt đối **KHÔNG ghi payload file ảnh hoặc chuỗi base64** vào log.
> - Khi gặp lỗi ngoại lệ, chỉ ghi tên lỗi (Exception Class) và thông điệp lỗi hệ thống; loại bỏ mọi dữ liệu người dùng khỏi stack trace.

---

## 2. Truy vết Phân tán bằng Request ID (`X-Request-Id`) `[PLANNED]`

Để dễ dàng truy vết và điều tra lỗi xuyên suốt giữa Client, Gateway và AI Service:
1. **Khởi tạo:**
   - Nếu Android Client đã gắn header `X-Request-Id` (dạng UUID v4), Gateway sẽ tiếp nhận và tái sử dụng mã này.
   - Nếu client chưa gửi, Gateway tự động sinh một mã mới: `req_<UUIDv4>`.
2. **Lan truyền (Propagation):**
   - Gateway chuyển tiếp header `X-Request-Id` qua cuộc gọi nội bộ sang `ai-service`.
   - Cả Gateway và AI Service đều đính kèm `X-Request-Id` vào mọi dòng log liên quan đến request đó.
3. **Phản hồi:**
   - Gateway luôn đính kèm header `X-Request-Id: req_...` vào HTTP Response trả về cho Android Client. Khi app gặp lỗi, client có thể hiển thị mã này để lập trình viên tra cứu nhanh trong log.

---

## 3. Điểm Kiểm tra Sức khỏe (Health Probes) `[PLANNED]`

Mỗi service cung cấp 2 endpoint chuẩn mực để Kubernetes/Docker hoặc công cụ giám sát kiểm tra:

### 3.1 Liveness Probe (`GET /v1/healthz`)
- **Mục đích:** Xác định tiến trình dịch vụ có còn sống hay không.
- **Phản hồi:** `200 OK` kèm `{"status":"UP"}`.
- **Hành động khi lỗi:** Docker Compose tự động khởi động lại container (restart).

### 3.2 Readiness Probe (`GET /v1/readyz`)
- **Mục đích:** Xác định dịch vụ đã sẵn sàng tiếp nhận lưu lượng hay chưa (các phụ thuộc mạng, DB, API key đã sẵn sàng).
- **Phản hồi tại Gateway:** Kiểm tra kết nối tới PostgreSQL và `ai-service`. Trả về `200 OK` nếu tất cả sẵn sàng; trả về `503 Service Unavailable` nếu một phụ thuộc bị gián đoạn.
- **Phản hồi tại AI Service:** Kiểm tra biến môi trường `GEMINI_API_KEY` đã được nạp hay chưa.

---

## 4. Định hướng Giám sát Số liệu (Metrics & OpenTelemetry) `[PLANNED]`

* **Trong MVP 24h:** Nhóm không cài đặt các hệ thống giám sát phức tạp (như Prometheus, Grafana, Jaeger) để tránh tiêu tốn CPU và RAM của máy ảo `t3.small`. Mọi số liệu đo lường được tổng hợp trực tiếp từ bảng `audit_access_logs` trong PostgreSQL.
* **Giai đoạn Post-MVP:** Tích hợp bộ thu thập OpenTelemetry SDK (OTel) vào Ktor để xuất số liệu thời gian thực (Trace & Metrics) lên AWS CloudWatch hoặc Datadog.
