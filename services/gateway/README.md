# Gateway Service (`gateway`)

Cổng tiếp nhận duy nhất cho toàn bộ hệ thống Remora, chịu trách nhiệm xác thực danh tính, kiểm soát lưu lượng, bảo vệ hạn mức AI và định tuyến ngược an toàn tới các dịch vụ nội bộ.

* **Chủ sở hữu (Owner):** Lead / AI
* **Trạng thái:** `[PLANNED]`
* **Thư mục mã nguồn:** `services/gateway/`
* **Hợp đồng API:** [`openapi.yaml`](file:///d:/MyProject/Remora/services/gateway/openapi.yaml)

---

## 1. Trách nhiệm & Ranh giới (Responsibilities & Non-Responsibilities)

### Trách nhiệm (Responsibilities):
* Tiếp nhận toàn bộ lưu lượng công cộng từ Android Client (qua Caddy TLS Terminator).
* Tích hợp module xác thực tối thiểu (`/v1/auth/login-demo`), cấp phát Self-issued JWT (HMAC-SHA256).
* Kiểm tra chữ ký, thời hạn hiệu lực của Bearer Token trên mọi route `/v1/ai/*`.
* Giới hạn lưu lượng (Rate limiting: 60 req/phút/user) và kiểm soát hạn mức (Quota guard: 50 cards/ngày).
* Tự động sinh hoặc chuyển tiếp mã truy vết `X-Request-Id` và gắn định danh `X-User-Id` sang các service nội bộ.
* Ghi log truy cập có cấu trúc (Structured Access Logs) chuẩn JSON.

### Không chịu trách nhiệm (Non-Responsibilities):
* **Không gọi trực tiếp Google Gemini API** (việc này thuộc trách nhiệm độc quyền của `ai-service`).
* **Không nắm giữ `GEMINI_API_KEY`** (bảo vệ zero-trust).
* **Không lưu trữ file ảnh, văn bản OCR** hay dữ liệu học tập của sinh viên.
* **Không thực hiện tính toán Cosine similarity** (thực hiện on-device trên Android).

---

## 2. Bảng biến môi trường (Environment Variables)

| Tên biến | Bắt buộc | Ví dụ giá trị | Là bí mật? | Mục đích |
| --- | --- | --- | --- | --- |
| `PORT` | Có | `8080` | Không | Cổng HTTP mà gateway lắng nghe |
| `JWT_SECRET` | Có | `c8f1e0d3...long_random_string` | **Có** | Khóa bí mật dùng để ký và giải mã JWT HMAC-SHA256 |
| `DATABASE_URL` | Có | `jdbc:postgresql://postgres:5432/remora_db` | Không | Địa chỉ kết nối PostgreSQL |
| `DB_USER` | Có | `remora_user` | Không | Tên tài khoản DB |
| `DB_PASSWORD` | Có | `remora_secret_pass` | **Có** | Mật khẩu tài khoản DB |
| `AI_SERVICE_URL` | Có | `http://ai-service:8082` | Không | Địa chỉ URL nội bộ của `ai-service` |
| `INTERNAL_SERVICE_KEY` | Có | `internal_secret_token_123` | **Có** | Khóa chia sẻ nội bộ gửi kèm trong header `X-Internal-Secret` |
| `RATE_LIMIT_PER_MINUTE` | Không | `60` | Không | Giới hạn số request tối đa/phút mỗi user |

---

## 3. Hướng dẫn Chạy & Kiểm thử Cục bộ (Run Locally & Test)

### Chạy trực tiếp bằng Gradle:
```bash
cd services
./gradlew :gateway:run
```

### Chạy qua Docker Compose:
```bash
docker compose up -d gateway postgres
```

### Kiểm thử nhanh bằng `curl`:
```bash
# 1. Kiểm tra Liveness
curl http://localhost:8080/v1/healthz

# 2. Đăng nhập lấy token demo
curl -X POST http://localhost:8080/v1/auth/login-demo \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_student_01","password":"password123"}'
```

---

## 4. Điểm giám sát Sức khỏe (Health Endpoints)

* `GET /v1/healthz`: Trả về `{"status":"UP"}` nếu tiến trình Ktor Netty đang chạy bình thường.
* `GET /v1/readyz`: Trả về `{"status":"READY", "database":"CONNECTED", "ai_service":"REACHABLE"}`. Nếu PostgreSQL hoặc `ai-service` không kết nối được, trả về mã HTTP `503 Service Unavailable`.

---

## 5. Chế độ Suy thoái khi Gặp Lỗi (Degradation Modes)

* **Khi PostgreSQL gặp sự cố:** Endpoint xác thực tạm ngưng; đối với các request đã có token hợp lệ, gateway áp dụng fallback in-memory rate limiter và cho phép request đi tiếp nếu không thể cập nhật quota.
* **Khi `ai-service` phản hồi chậm hoặc timeout (>15s):** Gateway ngắt kết nối an toàn và trả về mã lỗi chuẩn RFC 9457 `504 Gateway Timeout` để Android WorkManager lập lịch retry backoff.
