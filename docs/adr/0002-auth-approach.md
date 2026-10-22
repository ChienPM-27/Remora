---
title: "ADR 0002: Phương án Xác thực & Quản trị Danh tính (Self-issued JWT vs Managed IdP)"
status: PLANNED
owner: "Lead"
last_verified: 2026-10-21
related_code: ["AGENTS.md", "docs/architecture/04-auth-and-security.md"]
---

# 0002. Lựa chọn Phương án Xác thực & Quản trị Danh tính: Self-issued JWT vs Managed IdP

* **Trạng thái (Status):** Accepted
* **Chủ sở hữu (Owner):** Lead
* **Ngày quyết định (Date):** 2026-10-21
* **Tài liệu liên quan:** [`AGENTS.md`](file:///d:/MyProject/Remora/AGENTS.md), [`docs/architecture/02-services-catalog.md`](file:///d:/MyProject/Remora/docs/architecture/02-services-catalog.md)

---

## 1. Bối cảnh & Vấn đề (Context)

Backend Remora yêu cầu mọi request từ ứng dụng Android gửi tới API Gateway phải được xác thực danh tính để:
1. Gắn định danh người dùng (`user_id`) vào ngữ cảnh request.
2. Kiểm soát hạn mức số lần gọi AI (AI Quota) và ngăn chặn tấn công spam/DDoS làm cạn kiệt ngân sách Gemini API.
3. Chuẩn bị sẵn cơ chế bảo mật khi mở rộng tính năng đồng bộ đám mây sau này.

Trong giới hạn 24h Hackathon, việc tích hợp giải pháp xác thực bên ngoài (Managed IdP như Firebase/Auth0) tiềm ẩn rủi ro về thời gian cấu hình console (SHA-1 fingerprint, OAuth client IDs, bundle IDs) và rủi ro mạng chập chờn khi chấm thi.

---

## 2. Các phương án xem xét (Options Considered)

### Phương án A: Self-issued JWT với Tài khoản Demo định sẵn — **CHOSEN FOR MVP**
* **Mô tả:** 
  * `gateway` tích hợp sẵn module xác thực tối thiểu (`/v1/auth/login-demo`).
  * Hệ thống khởi tạo sẵn một số tài khoản demo (ví dụ: `demo_student_01`, `demo_student_02`) trong PostgreSQL với hạn mức định lượng.
  * Khi client đăng nhập, `gateway` phát hành mã JWT tự ký (Self-issued JWT) được mã hóa bằng thuật toán đối xứng HMAC-SHA256 với secret key (`JWT_SECRET`).
  * Trong mỗi request tiếp theo, `gateway` tự giải mã và kiểm tra chữ ký token cục bộ trong bộ nhớ (In-memory verification), không cần gọi ra bất kỳ dịch vụ ngoài nào.
* **Ưu điểm (Pros):**
  * **Tự chủ 100%:** Hoàn toàn không phụ thuộc vào internet quốc tế hay hạ tầng của bên thứ ba trong ngày thi đấu.
  * **Tốc độ xác thực cực nhanh:** Xác thực chữ ký HMAC-SHA256 trên gateway chỉ mất < 0.5ms.
  * **Tiết kiệm thời gian thiết lập:** Không mất 1-2 tiếng để tạo project Firebase, cấu hình `google-services.json`, cấp quyền OAuth Google Play Services.
  * Cực kỳ thân thiện cho kịch bản demo: Giám khảo cầm máy bấm 1 chạm là vào ngay tài khoản demo có sẵn dữ liệu.
* **Nhược điểm (Cons):**
  * Chưa hỗ trợ người dùng tự đăng ký bằng tài khoản Google cá nhân trên máy thật.
  * Việc thu hồi token (Revocation) cần lưu danh sách đen (Blacklist) nếu muốn hủy token trước hạn.

### Phương án B: Managed IdP theo Cloud Provider (AWS Cognito / Microsoft Entra External ID / Firebase)
* **Mô tả:** Sử dụng dịch vụ định danh do đám mây quản lý (AWS Cognito User Pools nếu dùng AWS EC2, hoặc Microsoft Entra External ID nếu dùng Azure, hoặc Firebase Auth). Client đăng nhập với SDK hoặc Hosted UI, nhận ID/Access Token được ký bằng khóa bất đối xứng (RS256/ES256) và gửi lên `gateway` để xác thực qua JWKS endpoint.
* **Ưu điểm (Pros):**
  * Đầy đủ tính năng doanh nghiệp: Đăng nhập mạng xã hội (Google, Apple), xác thực 2 bước (MFA), phục hồi tài khoản, tuân thủ tiêu chuẩn OAuth2 / OIDC.
  * Tự động quản lý vòng đời token và khóa ký không cần dev tự viết.
* **Nhược điểm (Cons):**
  * `[VERIFY]` Cần xác minh độ trễ và chi phí Free Tier: AWS Cognito cho phép 50.000 MAU miễn phí, Microsoft Entra External ID cho phép 50.000 MAU miễn phí; tuy nhiên cấu hình User Pool Client, App Integration, Callback URLs trên AWS Console mất tối thiểu 1-2 giờ.
  * Rủi ro thời gian cấu hình trong 24h rất lớn (dễ gặp lỗi cấu hình redirect URI, SHA-1 fingerprint khi build APK debug/release trên các máy tính khác nhau trong nhóm).
  * `gateway` phải gọi ra JWKS endpoint để lấy public key định kỳ, có thể gây trễ (cold-start) trong lúc demo nếu mạng hội trường thi đấu bị nghẽn.

### Phương án C: Định danh theo Khóa thiết bị (Device-bound Public Key)
* **Mô tả:** Thiết bị Android tự sinh cặp khóa RSA/ECDSA trong Android Keystore và gửi public key lên backend để đăng ký thiết bị.
* **Ưu điểm (Pros):** Không cần nhớ mật khẩu.
* **Nhược điểm (Cons):** Phức tạp khi muốn đăng nhập trên thiết bị khác và khó quản lý phân quyền trong 24h.

---

## 3. Quyết định (Decision)

Lựa chọn chính thức: **Phương án A (Self-issued JWT với tài khoản demo)** cho giai đoạn **MVP 24h Hackathon**.

Cụ thể:
1. `gateway` tích hợp sẵn module xác thực tối thiểu (`/v1/auth/login-demo`), nhận username/password của tài khoản demo và trả về `access_token` (thời hạn 24 giờ).
2. **Cơ chế khóa đối xứng (Symmetric HMAC-SHA256):** Do `auth` và `gateway` nằm chung trong một tiến trình Ktor duy nhất trong MVP, cả hai **dùng chung một chuỗi bí mật `JWT_SECRET`** (lấy từ biến môi trường). Module auth dùng secret này để ký token, và filter của gateway dùng đúng secret này để giải mã và kiểm tra chữ ký cục bộ siêu tốc (< 0.5ms).
3. Token chứa payload tối thiểu:
   ```json
   {
     "sub": "usr_demo_student_01",
     "name": "Demo Student",
     "role": "STUDENT",
     "exp": 1729600000
   }
   ```
4. Phương án B (Managed IdP như AWS Cognito hoặc Microsoft Entra External ID) được đưa vào quy hoạch cho giai đoạn **Post-MVP**.

---

## 4. Hệ quả & Tác động (Consequences)

* **Tích cực (Positive):**
  * Loại bỏ hoàn toàn 1 điểm nghẽn rủi ro (external failure point) trong lúc thuyết trình demo.
  * Đơn giản hóa toàn bộ client SDK phía Android (chỉ cần lưu chuỗi token vào EncryptedSharedPreferences / Android Keystore và gắn vào header OkHttp Interceptor).
* **Tiêu cực & Hạn chế (Negative):**
  * Chỉ dùng cho mục đích kiểm thử và demo cuộc thi; không phát hành công khai ra cộng đồng người dùng đại trà với cơ chế này.
* **Rủi ro & Giảm thiểu (Risks & Mitigations):**
  * *Rủi ro:* `JWT_SECRET` bị lộ.
  * *Giảm thiểu:* `JWT_SECRET` được truyền qua biến môi trường Docker Compose, không bao giờ được hardcode trong code.

---

## 5. Điều kiện kích hoạt đánh giá lại (Revisit Trigger)

Nhóm sẽ họp đánh giá lại và chuyển đổi kiến trúc xác thực khi một trong các điều kiện sau xảy ra:
1. **Tách `auth` thành microservice riêng biệt (`auth-service`):** Ngay khi `auth` được tách khỏi `gateway` thành tiến trình độc lập, **bắt buộc phải chuyển từ HMAC sang thuật toán khóa bất đối xứng (Asymmetric Key: RS256 hoặc Ed25519)**. Khi đó, `auth-service` giữ Private Key để ký token, còn `gateway` chỉ giữ Public Key (hoặc tải qua JWKS nội bộ) để xác thực chữ ký, triệt tiêu hoàn toàn rủi ro chia sẻ Private Key giữa các services.
2. **Kích hoạt triển khai Managed IdP (Post-MVP):** Khi cuộc thi kết thúc và nhóm bắt đầu giai đoạn thử nghiệm Pilot mở rộng với sinh viên thực tế:
   - `[VERIFY]` Nếu triển khai trên **AWS**: Tích hợp **AWS Cognito User Pools** (xác minh cấu hình Cognito Domain, OAuth2 Google Identity Provider, quota free-tier 50.000 MAU).
   - `[VERIFY]` Nếu triển khai trên **Azure**: Tích hợp **Microsoft Entra External ID** (xác minh cấu hình App Registrations, User Flows, quota free-tier 50.000 MAU).
   - `[VERIFY]` Đánh giá Firebase Authentication như giải pháp thay thế đa nền tảng tối giản cho mobile.
