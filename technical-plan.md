---
title: Remora Technical Plan (reference)
status: reference
owner: TODO
last_verified: 2026-10-21
related_code: []
---

# Remora Technical Plan

> **Chỉ để tham khảo.** Việc cần làm lấy từ `tasks.md` của Spec Kit (nguồn sự thật duy nhất cho tiến độ). Backend (gateway, auth, ai-service) được mô tả trong `docs/architecture/`; bảng dưới chỉ ghi phần ảnh hưởng trực tiếp tới tiến độ 24h. Bản này kế thừa file Excel gốc của senior dev và đã sửa theo review, xem mục “Thay đổi so với bản gốc”.

## 1. Quy ước

- **Ưu tiên:** `P0` = demo hỏng nếu thiếu; `P1` = nên có, cắt được khi trễ; `P2` = sau MVP hoặc nếu dư giờ.
- **Owner (đề xuất theo vai trò, chưa gán người):** `Lead`, `CAP` (Capture & Data), `AI` (AI Pipeline), `UI` (Resurfacing & UI), `QD` (Quality & Demo). Chia lại theo số người thực tế.
- **Phụ thuộc:** liệt kê ID công việc phải xong (hoặc có bản chạy được) trước.
- Ước lượng giờ cho từng việc được đặt trong `tasks.md` khi chạy Spec Kit, không đặt ở đây.
- Mốc giờ là mục tiêu hoàn thành, tính từ lúc bắt đầu 24h.

## 2. Đường găng

```
18 gateway/proxy passthrough → 9 schema → 8 card extraction → 10 embedding → 11 BLOB → 12 search
                                                                  └→ 14 candidates → 15 resurfacing → 35 UI Resurfacing
Song song: 4 → 5 → 31 (capture + intent) → 6 → 7;  33 nhập ảnh demo;  34 UI chính;  26/28 dữ liệu và dự phòng demo
```

Chậm ở bất kỳ mắt xích nào trên đường găng là chậm demo. Kiểm tra cuối mỗi mốc giờ theo cột “Tiêu chí hoàn thành”.

## 3. Cut line (khi trễ)

Nếu trễ từ 2 giờ trở lên tại mốc h12, cắt **từ dưới lên**:

1. Cắt đầu tiên: 17, 25, 29 (phần ngoài sơ đồ), 30, 40, Crashlytics.
2. Tiếp theo: 36, 37, 38, 39 (auth client, quota, compose, deploy cloud). Phương án thay thế: một proxy passthrough dùng khóa theo thiết bị.
3. Giảm phạm vi nhưng giữ lại: 13 chỉ bản regex; 9 chỉ giữ fallback tối thiểu; 34 chỉ gallery, chi tiết thẻ, tìm kiếm; 24 chỉ một smoke test; 19 chỉ đếm request.
4. **Không cắt:** 4, 5, 31, 32, 6, 7, 8, 10, 11, 12, 14, 15, 33, 35, 26, 28.

## 4. Quyết định (đã chốt sau Gate 0)

| Quyết định | Giá trị | Trạng thái |
| --- | --- | --- |
| Stack backend | Kotlin + Ktor, Gradle build riêng dưới `services/`, monorepo | Chốt |
| Topology | `gateway` (kèm auth tối thiểu) + `ai-service`; `core-service` sau MVP; backend không lưu ảnh và `ocr_text` | Chốt |
| Database backend | PostgreSQL cho tài khoản và quota; init SQL, không Flyway ở MVP | Chốt |
| Cloud | EC2 `t3.small` (x86) với Docker Compose | Chốt tạm, ADR 0003 xác nhận |
| Xác thực | Self-issued JWT; MVP dùng tài khoản demo, đăng nhập đầy đủ là P1 (36) | Chốt |
| Hợp đồng chuẩn | `group`: SIMILAR / COMPLEMENTARY / ACTIONABLE; `relation`: DUPLICATE / SUPPLEMENT / EXPLANATION / CONTRADICTION; thêm `content_type` vào JSON trích xuất | Chốt |
| OCR và trạng thái | Gemini OCR trong một lần gọi, không ML Kit; `status` Room: PENDING / DONE / FAILED; cần cập nhật `:core:model` | Chốt |
| DTO | OpenAPI là hợp đồng duy nhất, không dùng chung `:core:model` trực tiếp (kiểm tra loại module) | Chốt |
| Embedding dimension | Chốt một giá trị một lần, đặt trong cấu hình | `[DECISION-NEEDED]` |

## 5. Danh sách công việc theo mốc

### h0–2: Khởi động

| ID | Hạng mục và cách làm | Công nghệ | Tiêu chí hoàn thành | Ưu | Phụ thuộc | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Khởi tạo quy trình theo spec: constitution → specify → plan → tasks → implement; mỗi task có tiêu chí đo được; chốt Must/Should/Could | GitHub Spec Kit, GitHub repo | Spec, plan, task list được review | P0 | none | Lead |
| 2 | Board Kanban, nhánh theo tính năng, PR nhỏ có checklist | GitHub Projects, Git, PR | Mọi thay đổi đi qua PR; board có owner và trạng thái | P1 | 1 | Lead |
| 3 | CI trên mỗi PR: lint, unit test, build APK, lưu APK làm artifact | GitHub Actions, Gradle, Android Lint | CI xanh; tải được APK từ artifact | P0 | none | QD |
| 18 | `gateway` + `ai-service` (Ktor, xem `docs/architecture/`) giữ khóa Gemini. **MVP: passthrough trước**; rate limit, log và quota làm ở 37. Khi phát triển chạy local bằng `docker compose`, app gọi qua IP LAN hoặc `adb reverse`; deploy lên EC2 ở mục 39 | Kotlin + Ktor, Docker | Không có khóa bí mật trong APK hoặc repo; một request Gemini qua backend local thành công | P0 | none | AI |

### h2–6: Capture và lưu

| ID | Hạng mục và cách làm | Công nghệ | Tiêu chí hoàn thành | Ưu | Phụ thuộc | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| 4 | Phát hiện screenshot mới: theo dõi MediaStore, kiểm tra MIME, thư mục và URI, quyền đọc ảnh. **Rủi ro:** ContentObserver chỉ sống khi tiến trình còn sống và nhiều hãng máy kill app nền; đánh giá content-URI trigger của WorkManager hoặc foreground service; test trên đúng máy demo | Kotlin, MediaStore, ContentObserver, WorkManager | Screenshot mới được phát hiện trên máy thật, kể cả khi app ở nền | P0 | none | CAP |
| 5 | Thông báo hành động nhanh: Lưu / Bỏ qua / Tắt / Tạm dừng; lưu cài đặt; không gửi ảnh lên AI nếu chưa bấm Lưu | Android Notification APIs, Compose | Các hành động đúng; trạng thái giữ sau khi mở lại app | P0 | 4 | CAP |
| 31 | **(Mới)** Nhập user intent khi bấm Lưu (ô nhập trong thông báo hoặc trong app); lưu vào `user_intent` | Notification RemoteInput hoặc Compose | Ghi chú được lưu cùng thẻ nhớ và hiển thị ở kết quả | P0 | 5, 6 | CAP |
| 32 | **(Mới, tách từ 21)** Màn hình consent và mô tả dữ liệu rời thiết bị (ảnh gửi AI sau khi Lưu, không lưu ảnh ở backend nếu theo mặc định) | Compose | Hiện ở lần chạy đầu; người dùng đồng ý trước khi bật capture | P0 | 5 | UI |
| 6 | Entity `memory_card` với `PENDING / DONE / FAILED`; ghi bản ghi ngay để UI hiện trước khi AI xong. Thêm `content_type`, `attempt_count`, `error_code` | Room, Coroutines | Lưu ảnh tạo bản ghi pending; lỗi lưu cùng trạng thái | P0 | none | CAP |

### h6–12: AI và Search

| ID | Hạng mục và cách làm | Công nghệ | Tiêu chí hoàn thành | Ưu | Phụ thuộc | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| 7 | Hàng đợi AI nền: ràng buộc mạng, retry backoff có giới hạn số lần | WorkManager, Coroutines | Mất mạng không làm mất yêu cầu pending; 429 được xếp lại | P0 | 6, 18 | CAP |
| 8 | Trích xuất thẻ nhớ: gửi ảnh chỉ sau khi Lưu, nhận JSON gồm `title`, `summary`, `tags`, `content_type`, `ocr_text`; validate đầu ra. Model ID: `gemini-3.5-flash` (đã kiểm tra có trong danh sách của Google ngày 2026-10-21; **xác nhận lại bằng một request thật**) | Gemini API, JSON schema | Ảnh thành thẻ nhớ đủ trường; JSON lỗi có fallback | P0 | 9, 18 | AI |
| 9 | Chuẩn hóa prompt và schema: kiểu dữ liệu bắt buộc, xử lý trường thiếu hoặc sai; lưu ảnh với tag “chưa phân loại” khi cần; prompt có version | JSON schema, kotlinx.serialization hoặc Moshi (chọn một) | Parser có unit test cho JSON hợp lệ, thiếu trường, sai định dạng | P0 | none | AI |
| 10 | Sinh embedding từ `user_intent`, tags, summary, `ocr_text` (cắt độ dài cố định); chuẩn hóa L2 trước khi lưu. Model: `gemini-embedding-2` (**xác minh**, kiểm tra output dimension và task type). Không benchmark nhiều model trong 24h; để model ID trong cấu hình để đổi nhanh | Gemini Embedding API | Embedding được sinh, chuẩn hóa và lưu cùng thẻ | P0 | 8, 18 | AI |
| 11 | Lưu vector: float32 little-endian liên tục vào BLOB; encode/decode; kiểm tra kích thước khi giải mã | Room, ByteBuffer | Round-trip không sai lệch; có unit test | P0 | 6, 10 | CAP |
| 12 | Tìm bằng ngữ nghĩa: embed truy vấn, cosine với vector đã lưu, ngưỡng relevance (trả “không tìm thấy” thay vì kết quả rác), làm nổi bật `user_intent` | Kotlin, Room | Truy vấn mẫu như “slide phân phối chuẩn hôm bữa chụp” ra đúng ảnh trong bộ demo | P0 | 10, 11, 13 | AI |
| 13 | **(Nâng lên P0)** Tách mốc thời gian mơ hồ. **Bản rẻ trước:** regex cho “hôm qua”, “tuần trước”, “hôm bữa” (mặc định 2–3 tuần); lọc `created_at` trước cosine. Bản dùng LLM chỉ là P1 | Kotlin Date/Time | Các câu truy vấn thời gian trong test case ra đúng khoảng ngày; chốt timezone | P0 | 6 | CAP |
| 33 | **(Mới)** Nhập ảnh có sẵn từ thư viện và xử lý trước toàn bộ bộ demo qua đúng pipeline lưu ảnh. Lưu ý rate limit khi xử lý hàng loạt | MediaStore, WorkManager | 30–50 ảnh demo đã thành thẻ nhớ, có embedding | P0 | 6, 7, 8, 10 | CAP |
| 34 | **(Mới)** Giao diện chính: gallery có trạng thái pending, chi tiết thẻ nhớ, màn tìm kiếm | Jetpack Compose | Luồng Lưu → thẻ nhớ → tìm kiếm chạy trên máy thật | P0 | 6 | UI |

### h12–18: Resurfacing

| ID | Hạng mục và cách làm | Công nghệ | Tiêu chí hoàn thành | Ưu | Phụ thuộc | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| 14 | Lọc ứng viên: cosine top N (mặc định 10–20, cấu hình được) trước khi gọi LLM | Kotlin, Room | Danh sách ứng viên xếp hạng; N cấu hình được | P0 | 10, 11 | AI |
| 15 | Đánh giá quan hệ, chia 3 nhóm Tương đồng / Bổ trợ / Có giá trị sử dụng (`SIMILAR / COMPLEMENTARY / ACTIONABLE`); quan hệ `DUPLICATE / SUPPLEMENT / EXPLANATION / CONTRADICTION`; **một** lần gọi cho cả lô; trả `reason`, `confidence`, `relation`; chỉ nhận `candidate_id` có trong đầu vào; bảng `resurfacing_result` (gộp từ mục 16) dùng enum cho `group` và `relation`, có migration và DAO test | Gemini API, JSON schema, Room | Mỗi nhóm 0–2 ảnh có lý do; kết quả lưu; danh sách rỗng hợp lệ | P0 | 14, 8, 18 | AI |
| 35 | **(Mới)** Giao diện Resurfacing: màn hiển thị 3 nhóm kèm lý do, và thông báo gợi ý | Compose, Notification | Ảnh mới → thông báo → mở ra 3 nhóm có lý do | P0 | 15 | UI |
| 19 | Giảm chi phí: bỏ ảnh trùng bằng hash, nén ảnh trước khi gửi, không gọi lại khi đã có kết quả; đo số request mỗi ảnh | Hash, nén ảnh, cache Room | Có số đo request mỗi ảnh và tỷ lệ cache hit | P1 | 8 | AI |

### h18–22: Hoàn thiện

| ID | Hạng mục và cách làm | Công nghệ | Tiêu chí hoàn thành | Ưu | Phụ thuộc | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| 20 | Xử lý lỗi: 429 xếp hàng, mất mạng giữ pending, JSON sai dùng fallback, ảnh lỗi thành `FAILED` có nút thử lại; **giới hạn số lần thử** | WorkManager, HTTP | Test được 429, timeout, mất mạng, JSON lỗi, retry | P1 | 7 | CAP |
| 22 | Log từng bước (capture, analyze, embed, search, resurfacing): độ trễ, trạng thái, request id; không ghi ảnh hay `ocr_text` | Timber, structured log tại proxy | Truy vết lỗi theo id | P1 | 18 | QD |
| 23 | Unit test cho logic thuần: cosine, BLOB, parse JSON, bộ lọc thời gian, phân nhóm Resurfacing. **Viết cùng lúc viết code từ h2**, ở mốc này chỉ rà soát và đảm bảo chạy trong CI | JUnit, Kotlin test | Bộ test chạy trong CI | P1 | 3 | QD |
| 24 | Một UI smoke test cho luồng Lưu → thẻ nhớ → tìm kiếm; phần còn lại kiểm thử tay trên máy thật; quay hoặc chụp bằng chứng | Compose UI Test, máy thật | Kết quả test và bằng chứng quay màn hình | P1 | 34 | QD |
| 25 | Đo hiệu năng vector search với bộ giả lập 1.000 và 5.000 ảnh; chỉ tối ưu khi đo thấy chậm | Android Profiler | Báo cáo latency | P2 | 12 | QD |
| 21b | **(Tách từ 21)** Lọc dữ liệu nhạy cảm (số thẻ, mật khẩu, OTP) trong `ocr_text` trước khi lưu và embed. **Chưa có trong MVP**, nêu là hướng phát triển | Regex, bộ lọc | Có mô tả, chưa bắt buộc triển khai | P2 | 8 | AI |
| 36 | **(Mới, `[DECISION-NEEDED]`)** Xác thực phía app: đăng nhập, lưu token an toàn (Keystore), xử lý 401 và làm mới token. MVP có thể dùng tài khoản demo hoặc khóa theo thiết bị | Android Keystore, OAuth/JWT theo quyết định | App gọi gateway có token hợp lệ; 401 được xử lý | P1 | 18 | UI |
| 37 | **(Mới)** Gateway: rate limit mỗi người dùng, quota AI, request id, log truy cập | Theo stack backend | 429 kèm `Retry-After` khi vượt hạn mức; request id xuyên suốt | P1 | 18, 36 | AI |
| 38 | **(Mới)** Chạy toàn bộ service MVP và database bằng một lệnh `docker compose up` | Docker Compose | Một lệnh khởi động đủ service; health check xanh | P1 | 18 | AI |
| 39 | **(Mới)** Triển khai backend lên cloud được chọn kèm smoke test và rollback | EC2 hoặc Azure, theo ADR 0003 | App trên máy thật gọi được backend đã deploy | P1 | 38 | Lead |
| 40 | **(Mới)** Sinh docs kiến trúc bằng `PROMPT_build_architecture_docs.md`, chạy song song, không chặn code | Antigravity + prompt | Docs theo từng phase đã được duyệt | P1 | none | Lead |

### h22–24 và sau cuộc thi

| ID | Hạng mục và cách làm | Công nghệ | Tiêu chí hoàn thành | Ưu | Phụ thuộc | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| 26 | Bộ dữ liệu demo: 30–50 screenshot của 3–4 môn, có bộ “đề bài – lý thuyết – bài giải” cùng chủ đề; gán nhãn; 5–10 truy vấn mẫu. **Chuẩn bị song song từ h0**, nhập vào app ở mục 33 | Dữ liệu được phép sử dụng | Bộ demo tái lập được; truy vấn và kết quả mong đợi được ghi lại | P0 | none | QD |
| 27 | Đo Recall@k và độ chính xác của 3 nhóm: so sánh (a) cosine, (b) cosine + user intent, (c) cosine + user intent + LLM | Nhãn ground-truth | Bảng kết quả định lượng và ví dụ đúng/sai | P1 | 26, 15 | QD |
| 28 | Phương án dự phòng: video demo, APK dự phòng, kết quả Resurfacing đã cache, máy dự phòng có APK và dữ liệu, hotspot; tập demo ít nhất 2 lần | Video, APK, cache | Có phương án cho mạng, API, thông báo, thiết bị lỗi | P0 | 15, 33 | QD |
| 29 | Sơ đồ kiến trúc MVP và ngưỡng chuyển giai đoạn | Mermaid | Sơ đồ và trigger được ghi trong `docs/architecture/` | P1 | none | Lead |
| 17 | Tìm từ khóa chính xác (tên môn, mã môn) bằng FTS, kết hợp semantic khi hữu ích; có benchmark so với semantic-only | SQLite FTS4/FTS5, Room | Tìm đúng mã môn và tên môn | P2 | 12 | CAP |
| 30 | Pilot với 10–20 sinh viên trong 2–4 tuần, có form đồng ý; theo dõi số ảnh lưu, tỷ lệ mở gợi ý, đánh giá hữu ích | Form phản hồi, event metrics | Kế hoạch pilot, form đồng ý, báo cáo phản hồi | P2 | none | Lead |

## 6. Technology reference

| Lớp | Công nghệ | Vai trò | Trạng thái |
| --- | --- | --- | --- |
| Android UI | Kotlin + Jetpack Compose | Ứng dụng và giao diện | MVP |
| Capture | MediaStore + ContentObserver; content-URI trigger hoặc foreground service nếu bị kill | Phát hiện screenshot | MVP, kiểm thử trên máy thật |
| Nền | WorkManager + Coroutines | AI nền, retry, pending | MVP |
| CSDL cục bộ | Room (SQLite) | `memory_card`, `resurfacing_result`, `app_setting` | MVP |
| Vision, OCR, tóm tắt | `gemini-3.5-flash` | Phân tích ảnh, trả JSON | `[VERIFY]` bằng request thật |
| Embedding | `gemini-embedding-2` | Vector cho nội dung và truy vấn | `[VERIFY]` dimension, task type |
| Vector | Cosine + float32 BLOB | Top-k trên thiết bị | MVP |
| Gateway / AI service | Kotlin + Ktor (`gateway`, `ai-service`), Docker Compose, EC2 `t3.small` (x86) | Giữ khóa, rate limit, log, xác thực | Đã chốt (ADR 0003 xác nhận cloud) |
| Backend DB | PostgreSQL (tài khoản, quota), init SQL, không migration tool ở MVP | Dữ liệu tài khoản và mức sử dụng | Đã chốt |
| Logging | Timber + log có cấu trúc tại backend | Log theo id, không ghi nội dung | MVP |
| Crash monitoring | Firebase Crashlytics | Theo dõi crash | Tùy chọn |
| Test | JUnit (+ MockK nếu cần), Compose UI Test | Logic thuần và smoke test | MVP |
| CI/CD | GitHub Actions + Gradle + Android Lint | Lint, test, APK | MVP |
| Planning | Spec Kit + GitHub Projects | Spec, task, Kanban | MVP |
| Backend nhiều thiết bị | PostgreSQL + pgvector + OpenAPI | Đồng bộ đa thiết bị | Roadmap |

## 7. Lộ trình 24 giờ

| Giờ | Mốc | Công việc chính (ID) | Tiêu chí hoàn thành |
| --- | --- | --- | --- |
| h0–2 | Khởi động | 1, 2, 3, 18 | CI xanh; một request Gemini qua proxy thành công |
| h2–6 | Capture và lưu | 4, 5, 31, 32, 6 | Screenshot → thông báo → nhập ghi chú → Room pending |
| h6–12 | AI và Search | 7, 8, 9, 10, 11, 12, 13, 33, 34 | Thẻ nhớ đủ trường; truy vấn mẫu ra đúng ảnh; bộ demo đã xử lý |
| h12–18 | Resurfacing | 14, 15, 35, 19 | Ảnh mới → thông báo → 3 nhóm có lý do; kết quả lưu |
| h18–22 | Hoàn thiện | 20, 22, 23, 24, 36–39; **đóng băng tính năng lúc h22** | Luồng chính ổn định; test chạy; không mở rộng phạm vi |
| h22–24 | Demo | 26, 27, 28, 29 | Tập demo ít nhất 2 lần; có video, APK và máy dự phòng; có số liệu đo |

## 8. Thay đổi so với bản gốc

**Thêm mới (thiếu trong bản gốc):** 31 (user intent), 32 (consent), 33 (nhập ảnh demo), 34 và 35 (giao diện), 36 (xác thực app), 37 (quota gateway), 38 (docker compose), 39 (deploy), 40 (docs bằng agent).

**Sửa:**
- Mục 8 và 10: ghi model ID đã kiểm tra, bỏ việc “benchmark chọn embedding” trong 24h.
- Mục 13: nâng lên P0 và làm bản regex trước vì kịch bản demo dùng “hôm bữa chụp”.
- Mục 4: thêm rủi ro capture nền.
- Mục 18: MVP là passthrough, các phần còn lại chuyển sang 37.
- Mục 21: tách thành 32 (MVP) và 21b (sau MVP).
- Mục 23: viết cùng code từ h2, không dồn vào h18–22.

**Gộp hoặc hạ:** 16 gộp vào 15; 2 hạ xuống P1; 19 chỉ đo số request; 24 chỉ một smoke test; 25 hạ xuống P2.

**Bổ sung cấu trúc:** cột Owner (theo vai trò) và Phụ thuộc, đường găng, cut line, danh sách quyết định cần chốt.

**Lưu ý:** số mục P0 tăng (19 → 22) vì các việc quan trọng cho demo đã bị bỏ sót; cut line ở mục 3 mới là cơ chế kiểm soát phạm vi.

**Cập nhật sau Gate 0:** thêm `content_type` vào JSON trích xuất và bảng `memory_card`; chuẩn hóa tên enum `group` và `relation`; bỏ bước ML Kit OCR; mục 18 chuyển sang `gateway` + `ai-service` bằng Ktor; ghi các quyết định đã chốt ở mục 4.

## 9. Thông tin đã xác minh

- Ngày 2026-10-21, theo trang deprecations của Google AI (`ai.google.dev/gemini-api/docs/deprecations`): `gemini-2.5-flash` có lịch ngừng 2026-10-16, thay thế là `gemini-3.5-flash`; `gemini-embedding-001` có lịch ngừng 2026-07-14, thay thế là `gemini-embedding-2`. Các trang của Google Cloud (Vertex) liệt kê khác nhau, nên xác nhận lại bằng một request thật trên đúng nền tảng nhóm dùng.
- Tên tham số thinking level của Gemini 3 và việc `gemini-embedding-2` hỗ trợ output dimension, task type chưa được kiểm tra: `[VERIFY]` trước khi dùng.