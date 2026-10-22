---
title: "ADR 0003: Lựa chọn Nền tảng Đám mây (AWS EC2 vs Microsoft Azure)"
status: ACCEPTED
owner: "Lead"
last_verified: 2026-10-22
related_code: ["AGENTS.md", "docs/deployment/runbook-ec2.md", "docs/decisions.md"]
---

# 0003. Lựa chọn Nền tảng Đám mây: AWS EC2 vs Microsoft Azure cho Bản Demo 24h

* **Trạng thái (Status):** Accepted
* **Lựa chọn chính thức (Chosen):** AWS EC2 `t3.medium` (x86) cho Demo; `t3.small` là dự phòng
* **Chủ sở hữu (Owner):** Lead
* **Ngày quyết định (Date):** 2026-10-21 (Cập nhật: 2026-10-22)
* **Tài liệu liên quan:** [`AGENTS.md`](../../AGENTS.md), [`01-context-and-containers.md`](../architecture/01-context-and-containers.md), [`runbook-ec2.md`](../deployment/runbook-ec2.md), [`decisions.md`](../decisions.md)

---

## 1. Bối cảnh & Vấn đề (Context)

Remora cần một máy chủ đám mây công khai để triển khai API Gateway và AI Service trong 24h Hackathon. Ban giám khảo và nhóm phát triển sẽ cài đặt ứng dụng Android trên máy thật và gửi request thực tế lên máy chủ này.
Yêu cầu:
- **Thời gian thiết lập (Setup time):** Phải hoàn thành và chạy được trong vòng 30-45 phút.
- **Tính ổn định & Có thể dự đoán (Predictability):** Tránh cold-start, đảm bảo độ trễ thấp khi ban giám khảo bấm nút trên điện thoại.
- **Chi phí (Cost):** Tối ưu cho 24h thi đấu và duy trì thử nghiệm 1 tháng.
- **Quản lý mạng & TLS:** Hỗ trợ cấu hình tên miền và HTTPS tự động (bắt buộc cho Android).

---

## 2. So sánh Toàn diện các Phương án (Options Comparison)

### Phương án A: AWS EC2 `t3.small` (x86, 2 vCPU, 2GB RAM, Ubuntu 24.04 LTS) — **CHOSEN**
* **Mô hình triển khai:** Một máy ảo đơn lẻ (Single VM) chạy Docker Compose quản lý 4 container (`caddy`, `gateway`, `ai-service`, `postgres`).
* **Thời gian thiết lập:** Khoảng 20-30 phút (tạo EC2 Instance, gán Elastic IP, cài Docker, chạy `docker compose up`).
* **Ước tính chi phí `[VERIFY]`:**
  * *Chi phí 24 giờ Hackathon:* Khoảng **~$0.50 - $0.70 USD** (giá on-demand `t3.small` us-east-1 xấp xỉ $0.0208/giờ `[VERIFY]`).
  * *Chi phí 1 tháng duy trì:* Khoảng **~$15.00 - $18.00 USD/tháng** (bao gồm 20GB gp3 EBS storage `[VERIFY]`).
* **Quản trị TLS & Tên miền:** Cực kỳ thuận tiện; sử dụng container Caddy 2 tự động lấy chứng chỉ Let's Encrypt qua HTTP-01 challenge trỏ về Elastic IP / DuckDNS.
* **Quản trị bí mật (Secrets):** Lưu trong file `.env` trên máy ảo (quyền `chmod 600`), inject vào container qua Docker Compose.
* **Logging & Monitoring:** Log JSON lưu cục bộ qua Docker logging driver; tra cứu tức thì bằng `docker compose logs -f`.
* **Cơ chế Rollback:** Rollback cực nhanh bằng `git checkout <tag> && docker compose up -d --build` (mất < 60 giây).
* **Ưu điểm (Pros):**
  * Quen thuộc với hầu hết thành viên nhóm, tài liệu cộng đồng phong phú.
  * Toàn quyền kiểm soát hệ thống tệp và cấu hình mạng (Security Groups mở port 80, 443, 22).
  * Không có hiện tượng "ngủ đông" (Zero Cold-Start), đảm bảo phản hồi tức thì khi giám khảo chấm thi.
* **Nhược điểm (Cons):**
  * Cần tự giám sát RAM để tránh hiện tượng OOM nếu JVM Ktor sử dụng vượt quá 2GB.

---

### Phương án B: Microsoft Azure Virtual Machine `Standard_B2s` (x86, 2 vCPU, 4GB RAM) hoặc Azure Container Apps (ACA)
* **Mô hình triển khai:** Máy ảo Azure Linux hoặc dịch vụ Serverless Containers (ACA).
* **Thời gian thiết lập:** Khoảng 45-60 phút. Cần cấu hình Azure Resource Group, Virtual Network, Network Security Group (NSG) hoặc Container Apps Environment.
* **Ước tính chi phí `[VERIFY]`:**
  * *VM Standard_B2s:* Xấp xỉ $0.0416/giờ (~$30/tháng) `[VERIFY]`. Có thể tận dụng Azure for Students $100 credit nếu tài khoản trường hợp lệ.
  * *Azure Container Apps:* Tính theo vCPU/giây và RAM/giây; có rủi ro phát sinh chi phí nếu không giới hạn replica tối thiểu về 0 (nhưng nếu về 0 thì bị cold-start 10-20 giây lúc giám khảo chấm).
* **Quản trị TLS & Tên miền:** Hỗ trợ Managed Certificate miễn phí trên ACA nhưng yêu cầu xác minh custom domain DNS phức tạp hơn DuckDNS.
* **Ưu điểm (Pros):** Tận dụng được credit học sinh/sinh viên nếu nhóm có sẵn.
* **Nhược điểm (Cons):** Giao diện Azure Portal chậm và phức tạp hơn khi cấu hình mạng trong thời gian ngắn; rủi ro nghẽn cold-start nếu dùng Serverless Containers.

---

## 3. Quyết định (Decision)

Lựa chọn chính thức: **Phương án A (AWS EC2 x86 chạy Docker Compose)** được chọn làm môi trường staging/demo cho Remora.

### Cập nhật (2026-10-22): Chốt `t3.medium` cho buổi Demo, `t3.small` là dự phòng
Theo quyết định chính thức tại [docs/decisions.md](../decisions.md) mục 3:
- **Cấu hình Demo chính thức:** AWS EC2 `t3.medium` (2 vCPU, 4GB RAM) được chọn cho buổi thuyết trình trước ban giám khảo. Lý do: Hệ thống gồm 4 container (`caddy`, `gateway` Ktor JVM, `ai-service` Ktor JVM, `postgres:16-alpine`), mức tiêu thụ RAM đỉnh (JVM heap + metaspace + PostgreSQL buffer cache) đạt ~1.5GB - 1.8GB, dễ gây OOM killer trên máy 2GB nếu không có đủ swap space.
- **Phương án dự phòng:** `t3.small` (2 vCPU, 2GB RAM) chỉ dùng khi cần tiết kiệm chi phí tối đa, bắt buộc phải giảm `mem_limit` (`gateway`: 384m, `ai-service`: 512m, `postgres`: 256m) và tạo 2GB Swapfile theo [runbook-ec2.md](../deployment/runbook-ec2.md).

Lý do lựa chọn:
1. Tối ưu thời gian triển khai trong 24h: Thiết lập xong trong 30 phút, ít rủi ro cấu hình mạng phức tạp.
2. Chi phí kiểm soát được: Chỉ xấp xỉ ~$1.10 - $1.30 USD cho toàn bộ ngày thi đấu trên `t3.medium`.
3. Không bị cold-start: Đảm bảo độ trễ ổn định khi trình diễn tính năng trước ban giám khảo.

---

## 4. Hệ quả & Giảm thiểu Rủi ro (Consequences & Mitigations)

* **Tích cực:** Toàn bộ stack backend có thể chạy thử nghiệm y hệt môi trường máy ảo trên máy tính cá nhân bằng Docker Compose.
* **Rủi ro bộ nhớ (Memory Constraint):** Máy ảo có 2GB RAM.
  * *Quy tắc vận hành:* **Không build Docker image trực tiếp trên VM**; chỉ build ở CI hoặc laptop rồi push image lên registry để EC2 pull về chạy.
  * *Điều kiện nâng cấp lên `t3.medium` (x86, 2 vCPU, 4GB RAM) `[VERIFY]` ($0.0416/giờ):*
    1. Nếu nhóm không kịp cấu hình Container Registry trước giờ thi và bắt buộc phải build Docker images trực tiếp trên máy chủ EC2.
    2. Hoặc nếu tổng mức chiếm dụng RAM thực tế của 4 container (`caddy`, `gateway`, `ai-service`, `postgres`) vượt quá 80% RAM (1.6GB) trong quá trình thử nghiệm trước h18.
  * *Biện pháp bổ trợ:* Cấu hình giới hạn bộ nhớ JVM trong Docker Compose: `gateway` (`-Xmx256m`), `ai-service` (`-Xmx384m`), `postgres` (`mem_limit: 384m`), `caddy` (`mem_limit: 128m`), và tạo thêm 2GB Swap space trên ổ cứng EBS để chống sập tiến trình khi tải đột biến.

---

## 5. Điều kiện kích hoạt đánh giá lại (Revisit Trigger)

Nhóm sẽ họp đánh giá lại để xem xét chuyển đổi sang Azure Container Apps, AWS ECS hoặc Kubernetes khi:
1. Dự án vượt qua giai đoạn Hackathon và triển khai thương mại có tải đồng thời ước tính > 500 requests/phút.
2. Hoặc khi nhóm nhận được gói tài trợ Cloud Credits lớn từ một nhà cung cấp cụ thể (ví dụ Google Cloud for Startups hoặc Microsoft for Startups).
