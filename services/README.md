# Remora Backend Services

Cụm microservices phục vụ ứng dụng Remora bao gồm:
- **`gateway`**: Cổng API công khai, xác thực JWT, bảo vệ rate limit theo IP, quản lý 3 bộ đếm quota theo ngày (`extract_card`, `resurface_batch`, `embeddings`), gắn `X-Request-Id` và chuyển tiếp yêu cầu sang `ai-service`.
- **`ai-service`**: Dịch vụ xử lý trí tuệ nhân tạo, tương tác trực tiếp với Google Gemini API (`gemini-3.5-flash-lite`, `gemini-embedding-2`).
- **`caddy`**: Reverse proxy tự động cấp và gia hạn chứng chỉ SSL/TLS miễn phí qua Let's Encrypt cho tên miền chính thức.

*Lưu ý:* Trong MVP 24h, PostgreSQL được loại bỏ; hạn ngạch và tài khoản demo được quản trị in-memory và tự động làm mới hàng ngày.

---

## 1. Chạy Cục bộ (Local Development) & Hướng dẫn ADB Reverse

Khi phát triển hoặc kiểm thử trên máy cá nhân, file `docker-compose.override.yml` sẽ tự động mở cổng Gateway tại `127.0.0.1:8080`.

### Bước 1: Khởi động backend local
```bash
cd services
docker compose up -d
```

### Bước 2: Kết nối từ Thiết bị Android

#### Trường hợp A: Sử dụng Android Studio Emulator
- Máy ảo Android truy cập thẳng vào máy host thông qua địa chỉ mạng đặc thù:
  `http://10.0.2.2:8080/v1`
- Cấu hình trong `local.properties`:
  ```properties
  PROXY_BASE_URL=http://10.0.2.2:8080/v1
  ```

#### Trường hợp B: Sử dụng Điện thoại Android Thật (Cắm cáp USB)
- Bật **USB Debugging** trên điện thoại.
- Chạy lệnh chuyển tiếp cổng qua ADB:
  ```bash
  adb reverse tcp:8080 tcp:8080
  ```
  *(Lệnh này ánh xạ cổng 8080 trên điện thoại về thẳng cổng 8080 của máy tính).*
- Cấu hình trong `local.properties`:
  ```properties
  PROXY_BASE_URL=http://localhost:8080/v1
  ```
- Kiểm tra kết nối từ điện thoại:
  `curl http://localhost:8080/v1/healthz` -> `{"status":"UP"}`

---

## 2. Hướng dẫn Triển khai Production & Cấu hình DNS Let's Encrypt

### Bước 1: Cấu hình Bản ghi DNS
1. Truy cập trang quản lý DNS (Cloudflare, Namecheap, Route53, v.v.).
2. Thêm bản ghi **A Record**:
   - **Type**: `A`
   - **Name**: `api` (hoặc subdomain mong muốn, ví dụ `api.remora.study`)
   - **IPv4 Address**: `<PUBLIC_IP_EC2>` (địa chỉ IP Public của máy ảo EC2)
   - **TTL**: Auto hoặc 300 giây.
   - **Proxy Status**: **DNS Only** (nếu dùng Cloudflare, hãy tắt proxy đám mây cam để Caddy hoàn thành xác thực ACME HTTP-01).

### Bước 2: Mở Cổng Tường lửa (AWS Security Group)
- Port `80` (HTTP): Inbound `0.0.0.0/0` (bắt buộc để Let's Encrypt ACME challenge).
- Port `443` (HTTPS): Inbound `0.0.0.0/0` (lưu lượng bảo mật từ app Android).
- Port `8080`, `8082`, `5432`: **Đóng tuyệt đối** không mở ra ngoài Internet.

### Bước 3: Khởi chạy trên Server Production
Tạo file `.env` trên server với các biến môi trường:
```bash
APP_DOMAIN=api.remora.study
ACME_EMAIL=admin@remora.study
JWT_SECRET=super_secret_jwt_key_at_least_32_chars
INTERNAL_SERVICE_KEY=strong_internal_service_key_remora
GEMINI_API_KEY=AIzaSy...
POSTGRES_PASSWORD=strong_postgres_password_123
DEMO_USERNAME=demo_student_01
DEMO_PASSWORD=password123
```

Chạy Docker Compose (bỏ qua file override của local bằng cách chỉ định rõ `-f docker-compose.yml`):
```bash
docker compose -f docker-compose.yml up -d
```

Caddy sẽ tự động:
1. Lắng nghe trên cổng 80 & 443.
2. Tương tác với Let's Encrypt để lấy chứng chỉ TLS hợp lệ cho `api.remora.study`.
3. Tự động chuyển hướng toàn bộ yêu cầu HTTP sang HTTPS.
4. Chuyển tiếp lưu lượng an toàn tới `gateway:8080`.
