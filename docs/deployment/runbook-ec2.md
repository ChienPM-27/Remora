---
title: "Remora AWS EC2 Deployment Runbook"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["docs/adr/0003-cloud-deployment-ec2-vs-azure.md", "deployment/environments.md"]
---

# Runbook Triển khai AWS EC2 cho Remora Backend

Tài liệu hướng dẫn từng bước (Step-by-Step Runbook) để thiết lập, triển khai, kiểm thử và xử lý sự cố cho Remora Backend trên máy ảo AWS EC2 `t3.small` phục vụ ngày thi đấu Hackathon 24h.

---

## 1. Yêu cầu Tiên quyết (Prerequisites) `[PLANNED]`

1. **Tài khoản AWS:** Có quyền tạo EC2 Instance tại region gần Việt Nam (khuyến nghị `ap-southeast-1` Singapore để có độ trễ < 40ms `[VERIFY]`).
2. **Cấu hình Máy ảo EC2:**
   - **Loại Instance:** `t3.small` (x86, 2 vCPU, 2GB RAM).
   - **Hệ điều hành:** Ubuntu Server 24.04 LTS (HVM), SSD Volume Type.
   - **Ổ cứng (EBS):** 20GB gp3 General Purpose SSD.
   - **Ước tính chi phí `[VERIFY]`:** ~$0.0208/giờ (khoảng $0.50 cho 24h thi đấu) `[VERIFY]`.
3. **Cấu hình Security Group (Inbound Rules Bảo Mật):**
   - Port `22` (SSH): **Chỉ cho phép dải IP của nhóm dev** (`<TEAM_PUBLIC_IP>/32`), tuyệt đối không mở `0.0.0.0/0`.
   - Port `80` (HTTP): Mở `0.0.0.0/0` (Dùng cho Caddy HTTP-01 challenge cấp chứng chỉ SSL và redirect sang 443).
   - Port `443` (HTTPS): Mở `0.0.0.0/0` (Lưu lượng chính thức từ Android client).
   - **Các port nội bộ (`5432` PostgreSQL, `8080` Gateway, `8082` AI Service): TUYỆT ĐỐI ĐÓNG KHÔNG MỞ RA INTERNET**. Chúng chỉ liên lạc nội bộ qua Docker network `remora-net`.
4. **Elastic IP:** Tạo và gán 1 địa chỉ IPv4 tĩnh (Elastic IP) cho máy ảo EC2 để tránh bị đổi IP khi restart instance.
5. **Tên miền & Bản ghi DNS A:**
   - Đăng ký một tên miền hoặc subdomain (ví dụ `<DOMAIN>`, hoặc dịch vụ miễn phí DuckDNS `<SUBDOMAIN>.duckdns.org`).
   - Tạo bản ghi **DNS `A` Record**: Trỏ `<DOMAIN>` về địa chỉ Elastic IP của máy ảo EC2 (TTL 60 - 300 giây).
   - Kiểm tra phân giải DNS trước khi chạy Caddy: `nslookup <DOMAIN>`.

---

## 2. Quy trình Cài đặt & Triển khai Từng bước (Step-by-Step Deployment)

> **Lưu ý kiểm tra:** Các lệnh thao tác trên server từ xa dưới đây được đánh dấu `[NOT VERIFIED]` đối với hạ tầng thực tế. Nhóm chạy trực tiếp khi nhận máy ảo.

### Bước 2.1: Kết nối SSH & Cài đặt Docker
```bash
# Kết nối SSH vào máy ảo
ssh -i "remora-key.pem" ubuntu@<ELASTIC_IP>

# Cập nhật hệ điều hành & cài đặt Docker + Docker Compose
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Cấp quyền cho user ubuntu chạy docker không cần sudo
sudo usermod -aG docker ubuntu
newgrp docker
```

### Bước 2.2: Thiết lập Swap Memory (Chống tràn RAM)
Để đảm bảo máy ảo 2GB RAM không bị sập khi Ktor JVM hoặc Docker build chạy tải nặng:
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Bước 2.3: Sao chép Mã nguồn & Thiết lập Bí mật
```bash
# Tạo thư mục ứng dụng
sudo mkdir -p /opt/remora
sudo chown -R ubuntu:ubuntu /opt/remora
cd /opt/remora

# Clone repo mã nguồn
git clone https://github.com/YourTeam/Remora.git .

# Tạo file cấu hình môi trường bảo mật
cat << 'EOF' > .env
JWT_SECRET=c8f1e0d3a9b74c2e5f8a1d0b3c6e9f2a5d8b1c4e7a0d3f6b9c2e5a8d1f4b7c0e
DB_USER=remora_admin
DB_PASSWORD=SecurePassword2026_Hackathon!
INTERNAL_SERVICE_KEY=InternalSecretToken_Remora_2026
GEMINI_API_KEY=AIzaSy_REPLACE_WITH_ACTUAL_GEMINI_API_KEY
EOF

chmod 600 .env
```

### Bước 2.4: Cấu hình Caddyfile (Tự động cấp TLS)
Tạo tệp `Caddyfile` tại `/opt/remora/Caddyfile`:
```caddy
<DOMAIN> {
    reverse_proxy gateway:8080 {
        header_up Host {host}
        header_up X-Real-IP {remote}
        header_up X-Forwarded-For {remote}
        header_up X-Forwarded-Proto {scheme}
    }

    log {
        output file /var/log/caddy/access.log {
            roll_size 10mb
            roll_keep 3
        }
    }
}
```

### Bước 2.5: Khởi động Hệ thống
> **Quy tắc:** Pull image đã build sẵn từ registry, không build trực tiếp trên máy ảo.
```bash
# Đăng nhập vào Registry (nếu dùng private repo)
docker login ghcr.io -u <GITHUB_USERNAME>

# Kéo image và khởi chạy toàn bộ container
docker compose pull
docker compose up -d

# Kiểm tra trạng thái các container
docker compose ps
```

---

## 3. Lệnh Kiểm thử Khói sau Triển khai (Post-Deploy Smoke Tests)

> **Trạng thái kiểm tra trên môi trường thực:** `[NOT VERIFIED]` *(Các lệnh dưới đây thực hiện trực tiếp sau khi khởi động máy ảo Staging EC2)*.

```bash
# 1. Kiểm tra Chứng chỉ SSL & TLS Handshake của Caddy
curl -vI https://<DOMAIN>
# Kỳ vọng trong output TLS:
# - "SSL certificate verify ok"
# - "issuer: C=US, O=Let's Encrypt, CN=R3" (hoặc ZeroSSL)
# - HTTP/2 200 hoặc 404 (nếu gọi root)

# 2. Kiểm tra Gateway liveness qua domain chính thức
curl -i https://<DOMAIN>/v1/healthz
# Kỳ vọng: HTTP/2 200 OK, {"status":"UP"}

# 3. Kiểm tra Gateway readiness (kết nối DB và AI Service)
curl -i https://<DOMAIN>/v1/readyz
# Kỳ vọng: HTTP/2 200 OK, {"status":"READY","database":"CONNECTED","ai_service":"REACHABLE"}

# 4. Thử nghiệm đăng nhập tài khoản demo
curl -X POST https://<DOMAIN>/v1/auth/login-demo \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_student_01","password":"password123"}'
# Kỳ vọng: HTTP/2 200 OK, trả về access_token JWT
```

---

## 4. Mốc Kiểm thử Toàn trình Trước h18 (Staging E2E Dry Run Milestone) `[NOT VERIFIED]`

> [!IMPORTANT]
> **MỐC BẮT BUỘC (DEADLINE: TRƯỚC H18 CỦA HACKATHON):**
> Trước mốc h18 (trước khi đóng băng tính năng h22), cả nhóm phải thực hiện **01 lần chạy thử nghiệm toàn trình (Dry Run)** trên đúng môi trường Staging EC2:
> 1. Cài đặt APK build Release/Staging lên đúng điện thoại thật dùng để thuyết trình.
> 2. Chụp 1 ảnh bài giảng mới trên điện thoại -> Nhập ghi chú user intent -> Bấm Lưu.
> 3. Kiểm tra thẻ nhớ xuất hiện trên máy và được AI phân tích thành công qua domain HTTPS `<DOMAIN>`.
> 4. Xác nhận nhận được thông báo gợi nhớ Resurfacing 3 nhóm.
> 5. Thử nghiệm tìm kiếm bằng ngôn ngữ tự nhiên.
> *Nếu có bất kỳ lỗi nào về DNS, chứng chỉ TLS hoặc Quota, nhóm còn 4 giờ trước mốc h22 để khắc phục.*

---

## 5. Quy trình Vận hành Demo & Khắc phục Quota (Demo Operations Guide) `[PLANNED]`

### 5.1 Phân tách Tài khoản Pre-seed và Tài khoản Chấm thi
Để đảm bảo không bị cạn quota trong lúc ban giám khảo chấm:
* **Tài khoản Pre-seed (`demo_admin_seed`):** Cấp hạn mức lớn (`PRESEED_DAILY_QUOTA = 500 thẻ`), chỉ dùng để nhóm nạp trước 30-50 ảnh bài giảng mẫu và vector vào database vào lúc h10-h14.
* **Tài khoản Chấm thi (`demo_student_01`):** Cấp quota 50 thẻ, dành riêng cho ban giám khảo cầm máy bấm thử nghiệm trực tiếp.

### 5.2 Lệnh Khẩn cấp Reset Quota trong lúc Demo
Nếu trong quá trình tập dượt thuyết trình tài khoản demo bị chạm trần quota:
```bash
# Chạy trực tiếp trên server EC2 để reset hạn mức về 0
docker exec -i remora-postgres psql -U remora_admin -d remora_db -c \
  "UPDATE user_quotas SET used_today = 0, resurface_used_today = 0 WHERE user_id = 'usr_demo_student_01';"
```

### 5.3 Rủi ro Giới hạn Đăng nhập theo IP khi Dùng Chung 4G Hotspot
* **Hiện tượng:** Khi ban giám khảo và nhiều máy cùng kết nối vào 1 điện thoại phát 4G Hotspot, toàn bộ các thiết bị này đều có chung một địa chỉ Public IP ngoại mạng.
* **Rủi ro:** Nếu đặt `LOGIN_RATE_LIMIT_PER_MIN` quá thấp (ví dụ 5 req/min), việc 2-3 người cùng bấm đăng nhập demo trong vòng 1 phút sẽ kích hoạt mã lỗi `429 Too Many Requests`.
* **Khuyến nghị vận hành:** Trong môi trường demo, nhóm cấu hình biến môi trường `LOGIN_RATE_LIMIT_PER_MIN=25` để đảm bảo trải nghiệm mượt mà cho nhiều thiết bị dùng chung Hotspot.

---

## 6. Quy trình Khôi phục & Rollback (Rollback Procedure)

Khi phát hiện bản cập nhật mới bị lỗi hoặc làm hỏng API trước giờ chấm thi:
```bash
cd /opt/remora

# 1. Quay trở lại commit hoặc tag ổn định gần nhất
git checkout <COMMIT_SHA_HOAC_TAG_ON_DINH>

# 2. Khởi động lại các container với image ổn định
docker compose pull
docker compose up -d gateway ai-service

# 3. Kiểm tra lại health check
curl -f https://<DOMAIN>/v1/healthz
```
*Thời gian thực hiện rollback trung bình: < 90 giây.*

---

## 7. Kịch bản Dự phòng Khẩn cấp khi Mạng / API Gặp Sự cố (Fallback Contingency)

Trong cuộc thi Hackathon, mạng hội trường hoặc đường truyền quốc tế đi Google Gemini có thể gặp trục trặc bất ngờ. Nhóm áp dụng kịch bản dự phòng 3 lớp sau:

1. **Sự cố Mạng Wifi Hội trường bị nghẽn:**
   - *Biện pháp:* Đội thi chuẩn bị sẵn **02 điện thoại phát 4G/5G Hotspot** độc lập của 2 nhà mạng khác nhau (Viettel và Vinaphone). Điện thoại demo kết nối trực tiếp vào 4G Hotspot.
2. **Sự cố Gemini API bị Timeout hoặc Quota Cloud bị treo:**
   - *Biện pháp:* Dữ liệu của 30-50 ảnh bài giảng mẫu đã được xử lý trước (Pre-processed) và nạp sẵn trong Room SQLite trên máy demo. Giao diện Resurfacing đã có sẵn kết quả mẫu trong bộ đệm (cache), đảm bảo demo không bao giờ phụ thuộc hoàn toàn vào mạng thời gian thực.
3. **Sự cố Máy ảo Cloud EC2 bị ngắt kết nối:**
   - *Biện pháp:* Laptop của Lead đã chuẩn bị sẵn Docker Compose chạy cục bộ (`http://192.168.x.x:8080`). Chỉ cần đổi IP trong file cấu hình Android debug là tiếp tục demo bình thường.
