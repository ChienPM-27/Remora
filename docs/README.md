---
title: "Remora Documentation Index & Reading Guide"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["AGENTS.md"]
---

# Remora Documentation

Tài liệu kỹ thuật chuẩn hóa cho hệ thống **Remora** (Android Client + Microservice Backend & Auth Gateway).

## 1. Status Legend (Quy ước trạng thái)

Mọi tài liệu và mục con trong tài liệu đều được gắn nhãn trạng thái minh bạch:
- `[IMPLEMENTED]`: Đã được triển khai thực tế trong mã nguồn (có kèm đường dẫn file xác thực).
- `[PLANNED]`: Thiết kế dự kiến, chưa viết mã nguồn trong MVP hiện tại.
- `[DECISION-NEEDED]`: Vấn đề mở đang chờ thảo luận hoặc quyết định thêm.

## 2. Reading Order (Thứ tự đọc tài liệu)

Dành cho thành viên nhóm phát triển và coding agents (Antigravity, Claude Code, Cursor):

1. **Bắt đầu tại đây:** [`AGENTS.md`](../AGENTS.md) — Quy tắc làm việc, ràng buộc 24h Hackathon, hợp đồng dữ liệu cốt lõi, bảo mật và quyền riêng tư.
2. **Tổng quan hệ thống:** [`docs/architecture/00-overview.md`](architecture/00-overview.md) — Mục tiêu, phạm vi MVP vs Target, các nguyên tắc bất khả xâm phạm.
3. **Mô hình kiến trúc & phân ranh giới:** [`docs/architecture/01-context-and-containers.md`](architecture/01-context-and-containers.md) — Sơ đồ C4 Context & Container (Mermaid).
4. **Danh mục dịch vụ:** [`docs/architecture/02-services-catalog.md`](architecture/02-services-catalog.md) — Danh sách microservices, quyền sở hữu, cổng mạng và phạm vi.
5. **Các quyết định kiến trúc quan trọng (ADRs):**
   - [`docs/adr/0001-microservices-vs-modular-monolith.md`](adr/0001-microservices-vs-modular-monolith.md)
   - [`docs/adr/0002-auth-approach.md`](adr/0002-auth-approach.md)
   - [`docs/adr/0004-service-communication.md`](adr/0004-service-communication.md)
   - [`docs/adr/0005-data-ownership-and-database.md`](adr/0005-data-ownership-and-database.md)
6. **Hợp đồng & Triển khai (Phase 2 & 3):**
   - Data ownership, Auth & Security, Client-Backend Contract tại `docs/architecture/`.
   - OpenAPI specifications tại `services/<service>/openapi.yaml`.
   - Deployment environments và Runbook tại `docs/deployment/`.

## 3. Cấu trúc thư mục tài liệu

```
docs/
  README.md                      # Mục lục và hướng dẫn đọc
  architecture/                  # Thiết kế kiến trúc chi tiết
    00-overview.md
    01-context-and-containers.md
    02-services-catalog.md
    03-data-ownership.md
    04-auth-and-security.md
    05-client-backend-contract.md
    06-observability.md
    07-scalability-and-cost.md
  adr/                           # Hồ sơ quyết định kiến trúc (ADRs)
    0000-template.md
    0001-microservices-vs-modular-monolith.md
    0002-auth-approach.md
    0003-cloud-deployment-ec2-vs-azure.md
    0004-service-communication.md
    0005-data-ownership-and-database.md
  deployment/                    # Hướng dẫn môi trường & runbook triển khai
    environments.md
    runbook-ec2.md
    ci-cd.md                     # (Phase 4)
  engineering/                   # Quy chuẩn kỹ thuật & kiểm thử (Phase 4)
    conventions.md
    testing-strategy.md
    definition-of-done.md
  agents/                        # Hướng dẫn làm việc với AI agents (Phase 4)
    working-with-agents.md
    prompts/
  reference/                     # Tài liệu tham chiếu ngữ cảnh (kế hoạch gốc)
    technical-plan.md
    remora-proposal.md
```
