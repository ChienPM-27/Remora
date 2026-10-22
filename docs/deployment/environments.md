---
title: "Remora Deployment Environments & Configuration Guide"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["services/gateway", "services/ai-service", "docs/adr/0003-cloud-deployment-ec2-vs-azure.md"]
---

# Hướng dẫn Môi trường Triển khai (Deployment Environments)

Tài liệu này định nghĩa cấu hình chi tiết cho 2 môi trường hoạt động chính của Remora: **Local Development** (máy lập trình viên) và **Staging / Demo** (máy ảo AWS EC2 `t3.medium` cho demo hoặc `t3.small`).

---

## 1. Ma trận Môi trường (Environments Matrix) `[PLANNED]`

| Tiêu chí | Local Development | Staging / Demo (AWS EC2) |
| --- | --- | --- |
| **Mục đích** | Viết code, debug, chạy unit test và smoke test | Môi trường public phục vụ chấm thi Hackathon 24h |
| **Hạ tầng** | Laptop / PC cá nhân (Docker Desktop / Podman) | AWS EC2 `t3.medium` (Khuyến nghị demo: 2 vCPU, 4GB RAM) hoặc `t3.small` (2 vCPU, 2GB RAM + 2GB Swap) |
| **Giao thức Ingress** | HTTP (`http://localhost:8080`) | HTTPS TLS 1.3 qua Caddy (`https://<DOMAIN>` hoặc DuckDNS) |
| **CSDL** | PostgreSQL 16 (Docker container cục bộ) | PostgreSQL 16 (Docker container trên EC2) |
| **Dữ liệu mẫu** | Nạp sẵn tài khoản `demo_student_01` qua `init.sql` | Nạp sẵn tài khoản `demo_student_01` qua `init.sql` |
| **Bảo mật Secret** | File `.env.local` (được gitignore) | File `/opt/remora/.env` trên server (`chmod 600`) |

---

## 2. Đặc tả Docker Compose (`docker-compose.yml`) `[PLANNED]`

Tệp cấu hình chạy toàn bộ các dịch vụ MVP bằng một lệnh duy nhất:

```yaml
version: '3.8'

networks:
  remora-net:
    driver: bridge

volumes:
  pgdata:
    driver: local

services:
  caddy:
    image: caddy:2-alpine
    container_name: remora-caddy
    restart: unless-stopped
    mem_limit: 128m
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    networks:
      - remora-net
    depends_on:
      - gateway

  gateway:
    image: ${CONTAINER_REGISTRY:-remora}/gateway:${IMAGE_TAG:-latest}
    container_name: remora-gateway
    restart: unless-stopped
    mem_limit: 384m # Phù hợp cho cả t3.small (384m) hoặc nâng lên 512m trên t3.medium
    environment:
      - PORT=8080
      - JWT_SECRET=${JWT_SECRET}
      - DATABASE_URL=jdbc:postgresql://postgres:5432/remora_db
      - DB_USER=${DB_USER}
      - DB_PASSWORD=${DB_PASSWORD}
      - AI_SERVICE_URL=http://ai-service:8082
      - INTERNAL_SERVICE_KEY=${INTERNAL_SERVICE_KEY}
      - DEMO_DAILY_QUOTA=${DEMO_DAILY_QUOTA:-50}
      - LOGIN_RATE_LIMIT_PER_MIN=${LOGIN_RATE_LIMIT_PER_MIN:-5}
      - OCR_EMBED_MAX_CHARS=${OCR_EMBED_MAX_CHARS:-1000}
      - RESURFACE_MIN_CONFIDENCE=${RESURFACE_MIN_CONFIDENCE:-0.70}
      - JAVA_OPTS=-Xmx256m -Xms128m
    networks:
      - remora-net
    depends_on:
      postgres:
        condition: service_healthy

  ai-service:
    image: ${CONTAINER_REGISTRY:-remora}/ai-service:${IMAGE_TAG:-latest}
    container_name: remora-ai-service
    restart: unless-stopped
    mem_limit: 512m # Phù hợp cho t3.small (512m) hoặc nâng lên 768m trên t3.medium
    environment:
      - PORT=8082
      - GEMINI_API_KEY=${GEMINI_API_KEY}
      - GEMINI_VISION_MODEL=gemini-3.5-flash
      - GEMINI_EMBEDDING_MODEL=gemini-embedding-2
      - INTERNAL_SERVICE_KEY=${INTERNAL_SERVICE_KEY}
      - JAVA_OPTS=-Xmx384m -Xms128m
    networks:
      - remora-net

  postgres:
    image: postgres:16-alpine
    container_name: remora-postgres
    restart: unless-stopped
    mem_limit: 256m # Phù hợp cho t3.small (256m) hoặc 384m trên t3.medium
    environment:
      - POSTGRES_DB=remora_db
      - POSTGRES_USER=${DB_USER}
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro
    networks:
      - remora-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d remora_db"]
      interval: 5s
      timeout: 5s
      retries: 5
```

> [!IMPORTANT]
> **QUY TẮC ĐÓNG GÓI & PHÂN PHỐI DOCKER IMAGE:**
> - **Tuyệt đối KHÔNG chạy lệnh `docker compose build` trực tiếp trên máy ảo EC2 `t3.small`**, vì quá trình biên dịch Gradle JVM và đóng gói Docker layer tiêu tốn rất nhiều CPU và RAM (>2GB), sẽ gây nghẽn và làm treo máy chủ trong lúc thi đấu.
> - **Quy trình chuẩn:** Docker images được build trên CI (GitHub Actions) hoặc máy laptop cá nhân của dev, sau đó push lên Container Registry (Docker Hub hoặc GitHub Container Registry - GHCR). Trên máy chủ EC2 chỉ thực hiện kéo image về và chạy: `docker compose pull && docker compose up -d`.

---

## 3. Lệnh Khởi động & Kiểm tra (Execution Commands)

### 3.1 Khởi động Môi trường Cục bộ:
> **Trạng thái kiểm tra trên máy agent:** `[NOT VERIFIED]` *(Do Docker Desktop daemon trên máy cục bộ chưa bật tiến trình nền tại thời điểm kiểm tra)*.

```bash
# 1. Tạo file biến môi trường cục bộ
cp .env.example .env

# 2. Khởi động toàn bộ dịch vụ (chạy ngầm)
docker compose up -d --build

# 3. Theo dõi log thời gian thực
docker compose logs -f gateway ai-service
```

### 3.2 Kiểm tra Trạng thái Sức khỏe (Smoke Test Commands):
*(Đánh dấu `[NOT VERIFIED]` đối với instance thực tế; cú pháp lệnh đã được chuẩn hóa)*:

```bash
# Kiểm tra Gateway liveness
curl -f http://localhost:8080/v1/healthz

# Kiểm tra Gateway readiness (kết nối DB và AI service)
curl -f http://localhost:8080/v1/readyz
```

---

## 4. Quản trị Bí mật & Cấu hình (Secrets Handling) `[PLANNED]`

1. **Tuyệt đối không đưa file `.env` vào Git:** Tệp `.gitignore` tại thư mục gốc đã được cấu hình để chặn toàn bộ các mẫu `.env`, `.env.local`, `.env.staging`.
2. **File mẫu `.env.example` công khai:**
   ```bash
   # Remora Environment Variables Template
   # Tạo khóa ngẫu nhiên bằng lệnh: openssl rand -hex 32
   JWT_SECRET=<GENERATE_WITH_OPENSSL_RAND_HEX_32>
   DB_USER=remora_admin
   DB_PASSWORD=<GENERATE_WITH_OPENSSL_RAND_HEX_32>
   INTERNAL_SERVICE_KEY=<GENERATE_WITH_OPENSSL_RAND_HEX_32>
   GEMINI_API_KEY=<REPLACE_WITH_ACTUAL_GEMINI_API_KEY>
   ```
3. **Trên máy chủ AWS EC2:**
   - File cấu hình được tạo thủ công tại `/opt/remora/.env`.
   - Phân quyền bảo mật: `chmod 600 /opt/remora/.env` (chỉ user `ubuntu` được đọc).
