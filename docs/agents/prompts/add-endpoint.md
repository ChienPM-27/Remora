---
title: "Agent Prompt: Add Endpoint"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["services/gateway", "services/ai-service"]
---

# Prompt: Thêm một API Endpoint mới (Add Endpoint)

Sử dụng prompt mẫu này khi cần yêu cầu AI Agent thêm một endpoint mới vào một microservice của Remora:

---

```markdown
Bạn là kỹ sư backend phụ trách microservice [TÊN_SERVICE: gateway / ai-service] trong repository Remora.
Nhiệm vụ: Bổ sung endpoint mới [METHOD] [PATH_ENDPOINT, ví dụ: POST /v1/ai/new-feature].

Quy trình bắt buộc (Contract-First Workflow):
1. Đọc kỹ file AGENTS.md ở gốc và services/[TÊN_SERVICE]/AGENTS.md.
2. CẬP NHẬT CONTRACT TRƯỚC:
   - Mở file services/[TÊN_SERVICE]/openapi.yaml.
   - Thêm đường dẫn, summary, description, requestBody schema, responses (kèm mã lỗi Problem Details RFC 9457).
   - Đảm bảo có additionalProperties: false trên schema dữ liệu.
   - Chạy kiểm tra: npx -y @stoplight/spectral-cli lint services/[TÊN_SERVICE]/openapi.yaml --fail-severity warn
3. CẬP NHẬT TÀI LIỆU:
   - Cập nhật docs/architecture/05-client-backend-contract.md hoặc file mô tả tương ứng.
4. VIẾT KIỂM THỬ TRƯỚC (TDD):
   - Viết bài kiểm thử Ktor TestApplicationEngine cho endpoint mới (kiểm tra 200 OK và các mã lỗi 400/401/429).
5. TRIỂN KHAI MÃ NGUỒN (Implementation):
   - Tạo DTO khớp chính xác 100% với openapi.yaml.
   - Đăng ký route Ktor và inject controller/service.
6. KIỂM CHỨNG & BÁO CÁO:
   - Chạy test toàn bộ service. Báo cáo kết quả thực tế (Terminal Output).
   - Tuyệt đối không commit API key, secret hay dữ liệu nhạy cảm.
```
