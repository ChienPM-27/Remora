---
title: "Remora CI/CD Pipeline & Automated Verification"
status: PLANNED
owner: "QD"
last_verified: 2026-10-21
related_code: [".github/workflows/ci.yml", "services/gateway", "services/ai-service"]
---

# Quy trình Tích hợp & Triển khai Liên tục (CI/CD Pipeline)

Tài liệu này xác định các giai đoạn kiểm thử tự động trên GitHub Actions cho mọi Pull Request và commit vào nhánh `main`.

---

## 1. Các Giai đoạn Kiểm thử trong Pipeline (Pipeline Stages) `[PLANNED]`

Mỗi khi có PR tạo ra hoặc commit vào `main`, GitHub Actions tự động kích hoạt 3 giai đoạn:

```
[ Giai đoạn 1: Doc & Contract Checks ]
  ├── 1.1 Kiểm tra tính hợp lệ của Front-matter trong docs
  ├── 1.2 Kiểm định hợp đồng OpenAPI 3.1 bằng Stoplight Spectral
  └── 1.3 Kiểm tra liên kết Markdown nội bộ (Link Checker)
          ↓
[ Giai đoạn 2: Android Build & Tests ]
  ├── 2.1 Android Lint (`./gradlew lint`)
  ├── 2.2 Unit Tests (`./gradlew testDebugUnitTest`)
  ├── 2.3 Biên dịch APK (`./gradlew assembleDebug`)
  └── 2.4 Tải APK Artifact lên GitHub Actions
          ↓
[ Giai đoạn 3: Backend Verification ]
  ├── 3.1 Unit Tests các Ktor microservices (`./gradlew test`)
  └── 3.2 Đóng gói Docker Images & Push lên Container Registry (chỉ trên nhánh `main`)
```

---

## 2. Tiêu chí Bắt buộc đối với Mọi PR (Required PR Checks)

1. **Nhánh `main` luôn xanh (Keep Main Green):** Bất kỳ PR nào có 1 trong các bước kiểm tra trên thất bại đều bị chặn gộp (Branch Protection Rule).
2. **Lưu giữ APK Artifact:** Bản build APK debug được tự động lưu trữ trên GitHub Actions trong 7 ngày để ban giám khảo và nhóm có thể tải về cài đặt trên máy thật bất kỳ lúc nào.
3. **Kiểm tra Hợp đồng Tự động:** Bất kỳ thay đổi nào làm gãy chuẩn OpenAPI 3.1 hoặc thiếu Problem Details RFC 9457 sẽ bị Spectral CLI đánh fail ngay tại Giai đoạn 1.

---

## 3. Lệnh Tự Kiểm tra Cục bộ trước khi Push (Local Pre-push Checks)

Thành viên trong nhóm hoặc coding agent có thể chạy các lệnh này cục bộ:

```bash
# 1. Kiểm tra OpenAPI contracts
npx -y @stoplight/spectral-cli lint "services/gateway/openapi.yaml" "services/ai-service/openapi.yaml" --fail-severity warn

# 2. Kiểm tra tài liệu và front-matter
node scripts/check-docs.js

# 3. Kiểm tra Android build & test (:core:model:test trong giai đoạn đầu)
./gradlew :core:model:test
```

---

## 4. Đóng gói & Đẩy Docker Image (Container Delivery) `[PLANNED]`

Khi code được merge vào nhánh `main`, pipeline CI/CD thực hiện tự động đóng gói và đẩy container image lên GitHub Container Registry (GHCR) để máy chủ EC2 có thể kéo về triển khai (`docker compose pull`):

```bash
# Đăng nhập GHCR
echo $CR_PAT | docker login ghcr.io -u <GITHUB_USERNAME> --password-stdin

# Build & Tag Docker Images
docker build -t ghcr.io/chienpm-27/remora/gateway:latest -f services/gateway/Dockerfile .
docker build -t ghcr.io/chienpm-27/remora/ai-service:latest -f services/ai-service/Dockerfile .

# Push Images lên Registry
docker push ghcr.io/chienpm-27/remora/gateway:latest
docker push ghcr.io/chienpm-27/remora/ai-service:latest
```
