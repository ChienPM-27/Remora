---
title: "Agent Prompt: Add Service"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["docs/architecture/02-services-catalog.md"]
---

# Prompt: Thêm một Microservice mới (Add Service)

Sử dụng prompt mẫu này khi cần yêu cầu AI Agent bổ sung một microservice mới vào kiến trúc hệ thống (ví dụ: `auth-service` hoặc `core-service`):

---

```markdown
Bạn là kiến trúc sư phần mềm trong repository Remora.
Nhiệm vụ: Tạo mới microservice [TÊN_SERVICE mới, ví dụ: core-service].

Quy trình bắt buộc (Architecture-First Workflow):
1. VIẾT ADR TRƯỚC:
   - Tạo hồ sơ quyết định kiến trúc docs/adr/XXXX-[TÊN_SERVICE].md theo mẫu docs/adr/0000-template.md.
   - Nêu rõ lý do tách service, ranh giới dữ liệu, cổng mạng và điều kiện kích hoạt.
2. CẬP NHẬT SERVICE CATALOG:
   - Cập nhật docs/architecture/02-services-catalog.md và sơ đồ Container trong docs/architecture/01-context-and-containers.md.
3. TẠO HỢP ĐỒNG API (Contract-First):
   - Tạo thư mục services/[TÊN_SERVICE]/.
   - Viết services/[TÊN_SERVICE]/openapi.yaml chuẩn OpenAPI 3.1, Problem Details RFC 9457.
   - Chạy kiểm tra: npx -y @stoplight/spectral-cli lint services/[TÊN_SERVICE]/openapi.yaml
4. TÀI LIỆU DỊCH VỤ:
   - Tạo services/[TÊN_SERVICE]/README.md theo mẫu chuẩn.
   - Tạo services/[TÊN_SERVICE]/AGENTS.md quy định ranh giới cho coding agent.
5. CẤU HÌNH DOCKER & HẠ TẦNG:
   - Tạo Dockerfile cho service mới trong services/[TÊN_SERVICE]/Dockerfile.
   - Cập nhật docs/deployment/environments.md và docker-compose.yml (đặt mem_limit và network remora-net).
```
