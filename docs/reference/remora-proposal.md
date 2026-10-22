---
title: "Remora Proposal (Reference)"
status: reference
owner: "Lead"
last_verified: 2026-10-21
related_code: ["AGENTS.md"]
---

# Remora (Screenshot Memory Project)
> **Your Second Brain for Everything You Capture.**  
> Đội thi: Remora · Hackathon 24h – Vòng sơ loại

Biến những screenshot bị lãng quên thành tri thức có thể tìm kiếm, kết nối và hành động.

## 1. Tóm tắt ý tưởng
Remora (Screenshot Memory) là ứng dụng Android dành cho học sinh, sinh viên, sử dụng AI để tự động hiểu, tổ chức và truy xuất nội dung từ screenshot bài giảng, slide, đề bài và tài liệu học tập. Thay vì để hàng nghìn ảnh nằm im trong thư viện, ứng dụng đọc nội dung từng ảnh, tóm tắt, gắn thẻ (môn học, chủ đề), ghi lại lý do người dùng lưu ảnh ngay lúc chụp (user intent), cho phép tìm lại bằng ngôn ngữ tự nhiên và chủ động gợi lại những screenshot đã lưu có liên quan mỗi khi người dùng chụp ảnh mới.

## 2. Vấn đề cần giải quyết
- Học sinh, sinh viên thường chụp màn hình để lưu bài giảng, slide, đề bài, code, tài liệu tham khảo.
- Screenshot tích tụ thành hàng trăm, hàng nghìn ảnh nhưng thiếu phân loại và ghi chú.
- Người dùng dễ quên nội dung hoặc lý do lưu; tìm lại thủ công mất nhiều thời gian, nhất là khi ôn thi.
- Hệ quả: screenshot thành "kho bị lãng quên" – nội dung vẫn còn đó nhưng khó tìm lại khi cần.

## 3. Giải pháp đề xuất (4 bước)
1. **Capture – Thu thập:** Tự động phát hiện screenshot mới trên Android (MediaStore ContentObserver) và hiện thông báo để người dùng chọn Lưu (kèm nhập ghi chú user intent), Bỏ qua, Tắt thông báo hoặc Tạm dừng.
2. **Understand – Thấu hiểu:** AI đọc chữ trong ảnh và ghi chú, tóm tắt nội dung, phân loại theo môn học và chủ đề, gắn thẻ và tạo thành một thẻ nhớ hoàn chỉnh.
3. **Retrieve – Truy xuất:** Tìm lại bằng ngôn ngữ tự nhiên, kể cả khi không nhớ từ khóa chính xác (hỗ trợ mốc thời gian mơ hồ như "hôm bữa chụp", "tuần trước").
4. **Reconnect – Kết nối (Memory Resurfacing):** Khi lưu screenshot mới, hệ thống tìm các screenshot đã lưu có liên quan và chia thành 3 nhóm gợi ý (Tương đồng, Bổ trợ, Có giá trị sử dụng), gửi thông báo gợi ý xem lại kèm lý do.

## 4. Tính năng tạo dấu ấn: Memory Resurfacing
- Tầng 1: Lọc top 10-20 ứng viên gần nhất bằng Vector Cosine Similarity.
- Tầng 2: Dùng LLM so sánh ảnh mới với các ứng viên và phân loại vào 3 nhóm:
  - **Tương đồng (SIMILAR):** Nội dung gần nhất với ảnh mới (cùng môn, cùng dạng bài).
  - **Bổ trợ (COMPLEMENTARY):** Cùng chủ đề nhưng khác vai trò (ví dụ slide lý thuyết bổ trợ cho đề bài tập).
  - **Có giá trị sử dụng (ACTIONABLE):** Có thể áp dụng trực tiếp (ví dụ bài giải mẫu tương tự).
- LLM trả về: quan hệ (`DUPLICATE | SUPPLEMENT | EXPLANATION | CONTRADICTION`), thông tin mới, lợi ích mang lại, lý do gợi ý và độ tin cậy.

## 5. Công nghệ dự kiến & Kịch bản Demo
- Android Client (Kotlin, Jetpack Compose, Room SQLite, WorkManager).
- AI: Google Gemini Flash (Vision + OCR + JSON) và Gemini Embedding.
- Kịch bản demo: 30-50 screenshots bài học đã nạp sẵn; chụp 1 slide mới -> nhập intent "Ôn thi giữa kỳ" -> AI tạo thẻ nhớ -> Nhận thông báo gợi ý Resurfacing 3 nhóm -> Tìm kiếm bằng câu hỏi mơ hồ "cái slide về phân phối chuẩn hôm bữa chụp".
