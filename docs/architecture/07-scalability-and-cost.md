---
title: "Remora Scalability Triggers & AI Cost Control Architecture"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["docs/adr/0001-microservices-vs-modular-monolith.md", "docs/adr/0003-cloud-deployment-ec2-vs-azure.md"]
---

# 07. Khả năng Mở rộng & Kiểm soát Chi phí AI (Scalability & Cost)

Tài liệu này xác định các ngưỡng kích hoạt mở rộng kiến trúc (Scaling Triggers), biện pháp kiểm soát chi phí token AI và bảng ước tính ngân sách vận hành cho Remora.

---

## 1. Các Ngưỡng Kích hoạt Mở rộng & Phân tách (Scalability Triggers) `[PLANNED]`

Mọi con số về người dùng và ngưỡng tải dưới đây mang tính **ước lượng kỹ thuật** để định hướng nâng cấp hạ tầng:

| Chỉ số / Điều kiện | Ngưỡng kích hoạt | Hành động kỹ thuật tương ứng |
| --- | --- | --- |
| **RAM Sử dụng trên EC2** | Duy trì `> 80%` (> 1.6GB / 2GB) trong 10 phút | Nâng cấp EC2 Instance từ `t3.small` (2GB RAM) lên `t3.medium` (4GB RAM) hoặc tách CSDL PostgreSQL ra AWS RDS `[VERIFY]`. |
| **CPU Sử dụng trên EC2** | Duy trì `> 75%` liên tục | Tối ưu hóa connection pool của Ktor Netty, xem xét mở thêm instance phía sau AWS Application Load Balancer. |
| **Độ trễ trung bình tại Gateway** | `> 3.000ms` (3 giây) cho các tác vụ không phải AI | Điều tra điểm nghẽn truy vấn PostgreSQL hoặc tăng connection pool. |
| **Quy mô Người dùng** | Ước tính vượt **~1.000 DAU** | Tách module `auth` từ `gateway` thành microservice độc lập `auth-service`, chuyển sang thuật toán khóa bất đối xứng RS256/Ed25519. |
| **Quy mô Mở rộng Đa thiết bị** | Bắt đầu hỗ trợ sync đồng bộ đám mây | Triển khai `core-service`, tích hợp công cụ migration Flyway và kích hoạt extension `pgvector` trên PostgreSQL. |

---

## 2. Các Biện pháp Kiểm soát Chi phí AI (AI Cost Controls) `[PLANNED]`

Các cuộc gọi Gemini API là nguồn chi phí biến đổi chính của hệ thống. Nhóm áp dụng 4 tầng kiểm soát chi phí:

1. **Gộp Lô Đánh giá Resurfacing (Batching Single Prompt):**
   - Thay vì gọi LLM 15 lần riêng lẻ cho 15 cặp thẻ, Remora **gộp toàn bộ tối đa 15 ứng viên vào đúng 1 prompt duy nhất** gửi tới Gemini trong Stage 2.
   - **Cách tính ước lượng tiết kiệm token:**
     - *Nếu gọi 15 lần riêng lẻ:* Mỗi lượt gọi gửi lại System Instruction + dữ liệu thẻ mới (khoảng 350 tokens) x 15 = 5.250 tokens overhead + (15 x 80 tokens thẻ cũ) = ~6.450 tokens.
     - *Khi gộp 1 lần duy nhất:* System Instruction + thẻ mới chỉ gửi 1 lần (350 tokens) + 15 thẻ ứng viên (1.200 tokens) = ~1.550 tokens.
     - *Hiệu quả:* Giảm **ước tính ~75% lượng token tiêu thụ** và cắt giảm độ trễ mạng từ 15s xuống còn ~1.5s - 2.5s.
2. **Nén Ảnh tại Thiết bị trước khi Tải lên (Client-Side Compression):**
   - Ảnh màn hình 4K gốc (3MB - 8MB) được nén trên điện thoại về chuẩn JPEG chất lượng 80-85% (dung lượng ~300KB - 500KB) trước khi gửi qua API.
   - Giảm dung lượng truyền tải mạng và tiết kiệm chi phí băng thông egress.
3. **Khử Trùng lặp bằng Mã Băm (Deduplication by SHA-256 Hash):**
   - Trước khi gọi AI, client kiểm tra hash của ảnh trong Room SQLite. Nếu ảnh đã được phân tích trước đó, ứng dụng tái sử dụng thẻ nhớ cũ, **không gọi backend thêm lần nào**.
4. **Hạn mức Cấu hình Linh hoạt qua Biến Môi trường (Configurable Limits) `[PLANNED]`:**
   Mọi hạn mức vận hành không bị hardcode mà được cấu hình qua biến môi trường để tinh chỉnh theo từng giai đoạn:
   - `DEMO_DAILY_QUOTA`: Hạn ngạch phân tích thẻ/ngày (Mặc định `50` cho tài khoản demo, `500` cho tài khoản pre-seed).
   - `LOGIN_RATE_LIMIT_PER_MIN`: Giới hạn lượt đăng nhập demo (Mặc định `5` req/min/IP cho staging, nâng lên `25` khi dùng 4G Hotspot chung).
   - `OCR_EMBED_MAX_CHARS`: Giới hạn ký tự OCR tối đa khi ghép chuỗi vector embedding (Mặc định `1000` ký tự).
   - `RESURFACE_MIN_CONFIDENCE`: Ngưỡng độ tin cậy tối thiểu để lưu gợi nhớ Resurfacing (Mặc định `0.70`).

---

## 3. Bảng Ước tính Chi phí Vận hành (Cost Estimation)

> [!NOTE]
> Mọi đơn giá đám mây và hạn mức dưới đây được đánh dấu `[VERIFY]` để xác minh lại theo bảng giá cập nhật nhất của nhà cung cấp.

### 3.1 Chi phí Hạ tầng Đám mây (AWS EC2 t3.small tại `ap-southeast-1` Singapore) `[VERIFY]`

| Hạng mục | Quy cách | Chi phí 24h Hackathon `[VERIFY]` | Chi phí 1 tháng duy trì `[VERIFY]` |
| --- | --- | --- | --- |
| **EC2 Instance `t3.small`** | 2 vCPU, 2GB RAM (On-demand) | ~$0.55 USD ($0.023/giờ) | ~$16.50 USD |
| **EBS Storage (gp3)** | 20 GB Storage | ~$0.08 USD | ~$1.80 USD |
| **Elastic IP** | 1 IPv4 Address | Miễn phí khi gắn với running instance | Miễn phí |
| **Băng thông Mạng (Data Transfer)** | Dưới 10 GB trong demo | ~$0.10 USD | ~$1.00 USD |
| **Tổng cộng Hạ tầng** | - | **< $1.00 USD** | **~$19.30 USD/tháng** |

---

### 3.2 Chi phí Google Gemini API & Đánh giá Gói Dịch vụ `[VERIFY]`

* **Chính sách Free Tier & Rủi ro Quyền riêng tư `[VERIFY]`:**
  - Google AI Studio cung cấp gói Free Tier (15 RPM / 1.500 RPD) `[VERIFY]`. Tuy nhiên, theo điều khoản dịch vụ của Google, dữ liệu gửi qua Free Tier có thể được sử dụng để cải thiện sản phẩm, đồng thời giới hạn 15 RPM dễ gây nghẽn tốc độ (429) khi demo đông người.
* **Khuyến nghị Bật Billing (Paid Tier - Pay-as-you-go) `[VERIFY]`:**
  - Nhóm **khuyến nghị kích hoạt Billing chính thức** khi demo với ảnh bài giảng thật của sinh viên: Dữ liệu hoàn toàn không bị dùng để train model, và hạn mức RPM/TPM được nâng cao vượt trội.
  - *Chi phí thực tế ước tính:*
    - `gemini-3.5-flash`: Xấp xỉ $0.075 / 1 triệu input tokens, $0.30 / 1 triệu output tokens `[VERIFY]`. 50 thẻ demo ước tính tốn **~$0.03 - $0.05 USD**.
    - `gemini-embedding-2`: Xấp xỉ $0.02 / 1 triệu tokens `[VERIFY]`. 50 thẻ demo ước tính tốn **~$0.002 USD**.
* **Tổng ngân sách dự kiến:** Nếu dùng Paid Tier, toàn bộ chi phí AI cho 24h chỉ xấp xỉ **~$0.05 - $0.10 USD**, cộng với chi phí máy ảo EC2 (~$0.80 USD), tổng chi phí toàn dự án ước tính kiểm soát an toàn dưới **$2.00 USD**.
