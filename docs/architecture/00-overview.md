---
title: "Remora Architecture: Overview, Scope & Constraints"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["AGENTS.md", "core/model", "technical-plan.md"]
---

# 00. Kiến trúc Remora: Tổng quan, Phạm vi & Ràng buộc

## 1. Tầm nhìn & Mục tiêu sản phẩm (Mission & Goals)

**Remora** là ứng dụng Android đóng vai trò "Bộ não thứ hai" (Second Brain) dành cho sinh viên. Ứng dụng tự động phát hiện ảnh chụp màn hình bài giảng/tài liệu học tập, dùng trí tuệ nhân tạo (Gemini) để hiểu nội dung, tổ chức thành các thẻ nhớ tri thức (`memory_card`), hỗ trợ tìm kiếm ngôn ngữ tự nhiên và chủ động gợi nhớ lại các kiến thức liên quan trong quá khứ ngay khi một ảnh mới được lưu (thuật toán **Memory Resurfacing** 2 tầng).

```
[Chụp ảnh bài giảng] 
       ↓ (Tự động bắt qua MediaStore + ContentObserver)
[Thông báo Quick Action + Nhập User Intent] 
       ↓ (Chỉ gửi khi người dùng bấm Lưu)
[Gateway & AI-Service] 
       ↓ (Gemini 3.5 Flash: OCR + Title + Summary + Subject + Tags + ContentType)
[Lưu Room SQLite + Vector Float32] 
       ↓ (Cosine Top-15 trên máy)
[Memory Resurfacing Stage 2 (LLM Batch 1 lần)] 
       ↓
[Hiển thị 3 nhóm: SIMILAR, COMPLEMENTARY, ACTIONABLE]
```

## 2. Bối cảnh & Ràng buộc thời gian (Hackathon 24h)

- **Thời gian thi đấu:** **24 giờ**, thuyết trình và chấm điểm trực tiếp trên thiết bị Android thật trước ban giám khảo.
- **Đóng băng tính năng (Feature Freeze):** Tại mốc **h22**. Sau mốc này, toàn bộ nhóm chỉ tập trung sửa lỗi, chạy test và chuẩn bị kịch bản demo/video dự phòng.
- **Phân loại phạm vi (MoSCoW):**
  - **Must (Bắt buộc phải chạy mượt):** Tự động phát hiện screenshot; thông báo Lưu/Bỏ qua kèm nhập `user_intent`; trích xuất thẻ nhớ AI (JSON mode); tìm kiếm ngữ nghĩa theo vector; gợi nhớ Memory Resurfacing 3 nhóm (SIMILAR, COMPLEMENTARY, ACTIONABLE).
  - **Should (Làm khi Must đã xong):** Xử lý truy vấn thời gian mơ hồ (regex tiếng Việt), cơ chế retry WorkManager, màn hình cài đặt bật/tắt capture.
  - **Could (Tuyệt đối không bắt đầu khi Must chưa xong):** Tìm kiếm từ khóa FTS, nhập toàn bộ ảnh từ thư viện, đồng bộ đám mây đa thiết bị.

---

## 3. Phân tách kiến trúc: MVP Slice vs Target Architecture

Hệ thống được thiết kế theo lộ trình rõ ràng để vừa đáp ứng yêu cầu thi đấu 24h vừa đảm bảo khả năng mở rộng lâu dài:

| Tiêu chí | MVP Slice (24h Hackathon) | Target Architecture (Post-MVP) | Trạng thái |
| --- | --- | --- | --- |
| **Android Client** | Độc lập, lưu trữ toàn bộ thẻ nhớ và vector trên Room SQLite. Tìm kiếm Cosine thực thi hoàn toàn on-device. | Đồng bộ 2 chiều với backend; hỗ trợ cache offline và conflict resolution. | `[PLANNED]` (Một phần `[IMPLEMENTED]` tại `core/model`) |
| **Backend Topology** | 2 microservices tinh gọn: `gateway` (kèm xác thực tối thiểu) + `ai-service`. Không có `core-service`. | Đầy đủ microservices: `gateway`, `auth-service`, `ai-service`, `core-service` (sync & search). | `[PLANNED]` |
| **Backend Data Store** | PostgreSQL 16 (khởi tạo qua `init.sql`). Chỉ lưu tài khoản người dùng, quota và access metrics. **Tuyệt đối không lưu ảnh và `ocr_text`**. | PostgreSQL 16 tích hợp tiện ích mở rộng `pgvector`, lưu trữ bản sao thẻ nhớ để tìm kiếm và đồng bộ giữa nhiều thiết bị. | `[PLANNED]` |
| **Authentication** | Self-issued JWT (HMAC-SHA256). MVP dùng tài khoản demo cấu hình sẵn (`demo_student_01`). | Managed IdP (Firebase Auth / Supabase Auth) hỗ trợ Sign in with Google, email xác thực. | `[PLANNED]` |
| **AI Processing** | Vision OCR + trích xuất thẻ: `gemini-3.5-flash`; Embedding: `gemini-embedding-2`. Gọi thông qua `ai-service`. | Hybrid: On-device OCR (ML Kit fallback khi offline) + Cloud Gemini. | `[PLANNED]` |
| **Deployment** | Docker Compose chạy trên 1 máy ảo AWS EC2 `t3.small` (x86). | Kubernetes / AWS ECS hoặc Container Apps có auto-scaling và CDN. | `[PLANNED]` |

---

## 4. Các nguyên tắc bất khả xâm phạm (Non-Negotiable Rules)

1. **Quyền riêng tư tuyệt đối (Privacy First):**
   - Ảnh chụp màn hình **chỉ rời khỏi thiết bị** sau khi người dùng chủ động bấm **Lưu** (Save) trên thanh thông báo hoặc ứng dụng.
   - **Bảo mật Resurfacing (Stage 2 Batch Evaluation):** Khi đánh giá các ảnh cũ liên quan, thiết bị chỉ gửi dữ liệu văn bản rút gọn: Thẻ mới gửi 5 trường (`title`, `summary`, `tags`, `content_type`, `user_intent`), và mỗi thẻ ứng viên cũ **chỉ gửi đúng 6 trường metadata văn bản:** `candidate_id`, `title`, `summary`, `tags`, `content_type`, `user_intent`. **Tuyệt đối KHÔNG gửi `ocr_text` và KHÔNG gửi file ảnh của thẻ mới hay thẻ cũ lên backend trong lượt gọi này**.
   - Backend `ai-service` chỉ giữ vai trò xử lý tính toán tạm thời (stateless). Backend **không bao giờ lưu trữ ảnh, file nhị phân hoặc `ocr_text`** của người dùng vào cơ sở dữ liệu trong MVP.
   - Nhật ký hệ thống (logs) chỉ ghi mã định danh (`request_id`, `card_id`), độ trễ (`latency_ms`), và mã trạng thái HTTP; tuyệt đối không ghi nội dung văn bản học tập nhạy cảm.

2. **Quản trị bí mật (Secrets Protection):**
   - Tuyệt đối không chứa Gemini API Key hoặc JWT Secret trong mã nguồn git, file APK Android, log hoặc bài test.
   - Client Android không bao giờ giữ API key thật; mọi cuộc gọi đều đi qua `gateway` với Bearer JWT.

3. **Tối ưu chi phí & Quota:**
   - **Gộp lô (Batching):** Giai đoạn 2 của Resurfacing bắt buộc phải gộp toàn bộ danh sách top-N ứng viên vào **đúng 1 prompt duy nhất** gửi tới Gemini; cấm gọi vòng lặp N lần.
   - Nén ảnh trước khi tải lên; kiểm tra hash trùng lặp để không gọi lại AI cho ảnh đã phân tích.

4. **Khả năng chịu lỗi (Resilience):**
   - Mọi cuộc gọi mạng từ client và nội bộ service đều phải có giới hạn thời gian (timeout).
   - Khi gặp mã lỗi `429 Too Many Requests` hoặc mất kết nối mạng, client giữ thẻ ở trạng thái `PENDING` và đưa vào hàng đợi WorkManager với exponential backoff.
   - Có giới hạn số lần thử lại tối đa (`max_attempts`); sau ngưỡng này, chuyển thẻ sang `FAILED` kèm nút "Thử lại" (Retry).
   - **UI không bao giờ bị chặn:** Thẻ được chèn vào Room SQLite dưới trạng thái `PENDING` ngay khi bấm Lưu; giao diện hiển thị ngay lập tức và tự cập nhật khi AI hoàn tất.

---

## 5. Hiện trạng mã nguồn & Ghi nhận cần cập nhật `:core:model`

* **Mã nguồn hiện có `[IMPLEMENTED]`:** Thư mục [`core/model`](file:///d:/MyProject/Remora/core/model) đã có mã nguồn Kotlin cho thực thể `Screenshot`, enum `ProcessingStatus`, `RelationshipType`, `AiContracts`, `VectorMath`.
* **Cần cập nhật trong `:core:model` `[PLANNED]`:**
  1. Đổi tên thực thể `Screenshot` thành `MemoryCard` hoặc chuẩn hóa tên bảng `memory_card` theo đúng [`AGENTS.md`](file:///d:/MyProject/Remora/AGENTS.md).
  2. Rút gọn `ProcessingStatus` về 3 trạng thái chuẩn: `PENDING | DONE | FAILED` (loại bỏ trạng thái trung gian `MLKIT_OCR_DONE` vì MVP không dùng ML Kit).
  3. Bổ sung trường `contentType: ContentType` vào kết quả trích xuất và lưu trữ.
  4. Chuẩn hóa phân loại Resurfacing theo 2 trường: `group` (`SIMILAR | COMPLEMENTARY | ACTIONABLE`) và `relation` (`DUPLICATE | SUPPLEMENT | EXPLANATION | CONTRADICTION`).
  5. Module `:core:model` là module Kotlin JVM thuần (`kotlin.jvm`), không phụ thuộc vào Android SDK. Tuy nhiên, backend services sẽ có Gradle build riêng dưới `services/` và sử dụng OpenAPI contract làm nguồn chân lý duy nhất (Contract-First), không phụ thuộc trực tiếp vào `:core:model`.
