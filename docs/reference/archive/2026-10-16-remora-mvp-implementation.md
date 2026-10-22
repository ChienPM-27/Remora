---
title: "Remora MVP Implementation Plan (Superpowers Archive)"
status: SUPERSEDED
owner: "Lead"
last_verified: 2026-10-22
related_code: ["core/model"]
---

# Remora MVP Implementation Plan [SUPERSEDED]

> [!WARNING]
> **SUPERSEDED ARCHIVE DOCUMENT - KHÔNG CÒN HIỆU LỰC THI HÀNH**
> Tài liệu kế hoạch này được lưu trữ phục vụ mục đích tham khảo lịch sử. Toàn bộ kế hoạch và kiến trúc đã được chuẩn hóa trong `docs/architecture/`, `docs/adr/` và `AGENTS.md`.
>
> **Điểm mâu thuẫn chính với các quyết định kiến trúc đã chốt:**
> 1. **Kiến trúc Backend & Bảo mật Secret:** Kế hoạch cũ thiết kế client độc lập (Standalone) gọi trực tiếp Gemini API với API key trên thiết bị. Quyết định đã chốt (ADR 0001, ADR 0004, `docs/architecture/04-auth-and-security.md`, AGENTS.md): Kiến trúc microservices (`gateway` + `ai-service`) viết bằng Ktor trên EC2, Android client không bao giờ nắm giữ Gemini API key trực tiếp.
> 2. **AI Models & Pipeline OCR:** Kế hoạch cũ dùng `gemini-2.5-flash` + `text-embedding-004` (hoặc ML Kit on-device). Quyết định đã chốt (AGENTS.md Section 3): `gemini-3.5-flash` xử lý cả Vision và OCR trong 1 request duy nhất (loại bỏ hoàn toàn ML Kit), embedding dùng `gemini-embedding-2`.
> 3. **Dependency Injection:** Kế hoạch cũ giả định dùng Hilt/Dagger. Quyết định đã chốt: Không dùng DI framework cho MVP (tránh speculative abstraction), sử dụng Manual DI / Service Locator nhẹ để tối ưu thời gian build và độ ổn định.
> 4. **Quản lý Kế hoạch:** Spec Kit (`AGENTS.md` + `tasks.md`) là single source of truth cho execution planning thay vì các file plan riêng biệt.

> **For agentic workers:** ARCHIVED REFERENCE ONLY. Do not execute tasks directly from this file.

**Goal:** Xây dựng ứng dụng Android Remora (Second Brain for Screenshots) hoàn chỉnh trong phạm vi Hackathon 24h, tự động bắt screenshot bài giảng, dùng Gemini trích xuất kiến thức có cấu trúc, tìm kiếm ngữ nghĩa và chủ động gợi nhớ kiến thức liên quan qua thuật toán Memory Resurfacing 2 tầng.

**Architecture:** Ứng dụng Android độc lập (Standalone Client) theo mô hình Clean Architecture / MVI (đã thay thế bởi mô hình Client + Backend Proxy trong ADR 0001). MediaStore ContentObserver chạy ngầm phát hiện ảnh mới và kích hoạt Android Notification với Action Quick Intent; Room SQLite lưu trữ metadata và vector embedding; tính toán Cosine Similarity trực tiếp trên bộ nhớ và phân tích quan hệ tri thức 2 tầng.

**Tech Stack:** Kotlin 2.0+, Jetpack Compose, Material 3, Android SDK 34 (Android 14), Room SQLite, Kotlinx Coroutines & Serialization, OkHttp 4 / Retrofit 2, Gemini API, Coil Compose.

**Spec:** Tham khảo [remora-proposal.md](../remora-proposal.md) và [technical-plan.md](../technical-plan.md).

---

## Global Constraints

- **Platform Target:** Android 13+ (API 33+) và Android 14 (API 34).
- **Offline / Standalone Resilience:** Không phụ thuộc server backend trung gian lúc chấm thi; toàn bộ data và vector được lưu trong Room Database trên máy.
- **AI Response Contract:** Tất cả lệnh gọi Gemini Vision & LLM phải sử dụng `response_mime_type = "application/json"` với schema định sẵn để chống lỗi parse JSON làm crash app.
- **Latency Budget:** Thời gian phân tích ảnh mới tối đa 3-5 giây; thời gian gợi ý Memory Resurfacing tối đa 2-3 giây; thời gian tìm kiếm Semantic Search < 500ms.
- **Demo Ready:** Có sẵn cơ chế Seed 30-50 screenshots mẫu kèm vector và nhãn môn học để demo mượt mà ngay cả khi không kịp chụp và phân tích 50 ảnh trực tiếp trong 3 phút thuyết trình.

---

## Review Focus

1. **Quyền truy cập ảnh trên Android 13/14 (`READ_MEDIA_IMAGES`):** Hệ điều hành Android mới giới hạn quyền truy cập ảnh; ứng dụng phải có UI hướng dẫn cấp quyền rõ ràng và fallback nếu người dùng từ chối.
2. **Notification Direct Reply (User Intent):** Người dùng nhập lý do lưu ("Ôn thi cuối kỳ") ngay trên thanh thông báo; hệ thống phải lưu đúng intent này vào thẻ nhớ tương ứng.
3. **Mạng chập chờn / Quota Gemini API:** Khi mất mạng hoặc hết quota, app không được crash, phải chuyển trạng thái `status = FAILED` hoặc `QUEUED_OFFLINE` và hiển thị thông báo thân thiện.
4. **Phân tích thời gian mơ hồ trong tìm kiếm:** Câu truy vấn như "hôm bữa chụp", "tuần trước" phải được tách thành khoảng thời gian lọc `timestamp` trước khi so khớp vector.
5. **Gộp batch prompt cho Stage 2 Resurfacing:** Không được gọi LLM lặp lại 10 lần cho 10 ứng viên; bắt buộc phải gộp danh sách ứng viên vào 1 prompt duy nhất để phản hồi trong 1.5 giây.

---

## Task Breakdown

### Task 1: Khởi Tạo Dự Án & Cấu Trúc Gradle Android (Project Scaffolding)

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/src/main/java/com/remora/app/RemoraApp.kt`

**Interfaces:**
- Consumes: N/A
- Produces: `com.remora.app.RemoraApp : Application` class; Android project buildable với dependencies: Jetpack Compose, Room, OkHttp, Coroutines, Serialization, Coil.

- [ ] **Step 1: Tạo cấu hình root `settings.gradle.kts` và `build.gradle.kts`**
- [ ] **Step 2: Tạo cấu hình module `app/build.gradle.kts` với Jetpack Compose, Room, Kotlin Serialization**
- [ ] **Step 3: Khởi tạo `AndroidManifest.xml` khai báo các quyền:**
  - `android.permission.READ_MEDIA_IMAGES`
  - `android.permission.POST_NOTIFICATIONS`
  - `android.permission.INTERNET`
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
- [ ] **Step 4: Tạo Application class `RemoraApp.kt` khởi tạo dependency injection / singleton**
- [ ] **Step 5: Commit scaffolding**

---

### Task 2: Core Data Models & Room Database Layer

**Files:**
- Create: `app/src/main/java/com/remora/app/data/model/ScreenshotEntity.kt`
- Create: `app/src/main/java/com/remora/app/data/model/MemoryRelationship.kt`
- Create: `app/src/main/java/com/remora/app/data/db/ScreenshotDao.kt`
- Create: `app/src/main/java/com/remora/app/data/db/AppDatabase.kt`
- Create: `app/src/main/java/com/remora/app/data/db/Converters.kt`
- Test: `app/src/test/java/com/remora/app/data/VectorMathTest.kt`

**Interfaces:**
- Consumes: N/A
- Produces: 
  - `ScreenshotEntity`: Thực thể lưu trữ URI ảnh, ngày tạo, ghi chú người dùng, kết quả OCR, tóm tắt, môn học, tag, vector embedding (ByteArray) và trạng thái.
  - `ScreenshotDao`: Thao tác CRUD ảnh, truy vấn theo khoảng thời gian (`capturedAt BETWEEN :start AND :end`), lấy danh sách tất cả vector để so khớp.
  - `VectorMath.cosineSimilarity(v1: FloatArray, v2: FloatArray): Float`
  - `VectorMath.toByteArray(floats: FloatArray): ByteArray`
  - `VectorMath.toFloatArray(bytes: ByteArray): FloatArray`

- [ ] **Step 1: Viết test cho `VectorMathTest.kt` kiểm tra tính toán Cosine Similarity và chuyển đổi Byte/Float**
- [ ] **Step 2: Chạy test để xác nhận FAIL (chưa có code)**
- [ ] **Step 3: Cài đặt `VectorMath.kt` với thuật toán Cosine Similarity chuẩn và chuyển đổi ByteArray**
- [ ] **Step 4: Chạy test `VectorMathTest.kt` để xác nhận PASS**
- [ ] **Step 5: Cài đặt `ScreenshotEntity.kt`, `Converters.kt`, `ScreenshotDao.kt`, `AppDatabase.kt`**
- [ ] **Step 6: Commit Data Layer**

---

### Task 3: Gemini Vision & Text Embedding Client

**Files:**
- Create: `app/src/main/java/com/remora/app/network/GeminiApiClient.kt`
- Create: `app/src/main/java/com/remora/app/network/dto/GeminiVisionResponse.kt`
- Create: `app/src/main/java/com/remora/app/network/dto/GeminiEmbeddingResponse.kt`
- Create: `app/src/main/java/com/remora/app/network/dto/GeminiResurfaceResponse.kt`
- Test: `app/src/test/java/com/remora/app/network/GeminiJsonParseTest.kt`

**Interfaces:**
- Consumes: API Key từ cấu hình, dữ liệu ảnh dạng Base64 hoặc ByteStream.
- Produces:
  - `GeminiApiClient.analyzeScreenshot(imageBytes: ByteArray, userIntent: String?): GeminiVisionResponse`
  - `GeminiApiClient.generateEmbedding(text: String): FloatArray`
  - `GeminiApiClient.evaluateRelationships(newCardSummary: String, candidates: List<CandidateCard>): GeminiResurfaceResponse`

- [ ] **Step 1: Viết test `GeminiJsonParseTest.kt` kiểm tra parse response JSON của Gemini Vision, Embedding và Resurface**
- [ ] **Step 2: Chạy test xác nhận FAIL**
- [ ] **Step 3: Định nghĩa các Data Transfer Object (DTO) với Kotlinx Serialization**
- [ ] **Step 4: Cài đặt `GeminiApiClient.kt` sử dụng OkHttp gọi Gemini API (v1beta) với `response_mime_type = "application/json"`**
- [ ] **Step 5: Chạy test `GeminiJsonParseTest.kt` xác nhận PASS**
- [ ] **Step 6: Commit AI Network Layer**

---

### Task 4: MediaStore ContentObserver & Notification Service (Background Capture)

**Files:**
- Create: `app/src/main/java/com/remora/app/service/ScreenshotObserver.kt`
- Create: `app/src/main/java/com/remora/app/service/ScreenshotCaptureService.kt`
- Create: `app/src/main/java/com/remora/app/service/NotificationHelper.kt`
- Create: `app/src/main/java/com/remora/app/receiver/NotificationActionReceiver.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `ScreenshotDao`, `ScreenshotCaptureService`.
- Produces:
  - `ScreenshotObserver`: Lắng nghe URI `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, phát hiện khi có ảnh mới nằm trong thư mục `Screenshots` hoặc có tên chứa `Screenshot`.
  - `NotificationHelper.showNewScreenshotNotification(context: Context, imageUri: Uri, screenshotId: Long)`: Bắn thông báo với các Action: "Lưu" (kèm `RemoteInput`), "Bỏ qua", "Tắt".
  - `NotificationActionReceiver`: Xử lý khi người dùng ấn "Lưu" -> Nhận text User Intent -> Kích hoạt WorkManager hoặc Coroutine phân tích AI.

- [ ] **Step 1: Cài đặt `NotificationHelper.kt` tạo Notification Channel và Notification với Action Buttons + RemoteInput**
- [ ] **Step 2: Cài đặt `ScreenshotObserver.kt` kiểm tra đường dẫn thư mục và debounce tránh bắt trùng lặp ảnh**
- [ ] **Step 3: Cài đặt `ScreenshotCaptureService.kt` chạy dạng Foreground Service bảo vệ tiến trình không bị Android kill**
- [ ] **Step 4: Cài đặt `NotificationActionReceiver.kt` lưu entity vào Room với trạng thái `PENDING` và kích hoạt xử lý AI**
- [ ] **Step 5: Commit Background Capture Service**

---

### Task 5: Core Intelligence Engine (Semantic Search & Memory Resurfacing)

**Files:**
- Create: `app/src/main/java/com/remora/app/domain/TimeQueryParser.kt`
- Create: `app/src/main/java/com/remora/app/domain/SemanticSearchEngine.kt`
- Create: `app/src/main/java/com/remora/app/domain/MemoryResurfacingEngine.kt`
- Test: `app/src/test/java/com/remora/app/domain/TimeQueryParserTest.kt`
- Test: `app/src/test/java/com/remora/app/domain/MemoryResurfacingTest.kt`

**Interfaces:**
- Consumes: `ScreenshotDao`, `GeminiApiClient`, `VectorMath`.
- Produces:
  - `TimeQueryParser.parse(query: String): TimeRangeFilter?` (bóc tách "hôm bữa", "hôm qua", "tuần trước", "tháng trước").
  - `SemanticSearchEngine.search(query: String): List<SearchResultItem>`
  - `MemoryResurfacingEngine.resurface(newScreenshotId: Long): ResurfaceResult` (gồm 3 nhóm: `similar`, `complementary`, `actionable`).

- [ ] **Step 1: Viết test `TimeQueryParserTest.kt` kiểm tra phân tích mốc thời gian tiếng Việt**
- [ ] **Step 2: Chạy test xác nhận FAIL**
- [ ] **Step 3: Cài đặt `TimeQueryParser.kt`**
- [ ] **Step 4: Chạy test `TimeQueryParserTest.kt` xác nhận PASS**
- [ ] **Step 5: Viết test `MemoryResurfacingTest.kt` kiểm tra thuật toán 2 tầng (Top K vector + LLM ranking)**
- [ ] **Step 6: Cài đặt `SemanticSearchEngine.kt` và `MemoryResurfacingEngine.kt`**
- [ ] **Step 7: Chạy test xác nhận PASS**
- [ ] **Step 8: Commit Intelligence Engine**

---

### Task 6: Jetpack Compose UI (Screens & Navigation)

**Files:**
- Create: `app/src/main/java/com/remora/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/remora/app/ui/MainActivity.kt`
- Create: `app/src/main/java/com/remora/app/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/com/remora/app/ui/screens/SearchScreen.kt`
- Create: `app/src/main/java/com/remora/app/ui/screens/DetailScreen.kt`
- Create: `app/src/main/java/com/remora/app/ui/components/ScreenshotCard.kt`
- Create: `app/src/main/java/com/remora/app/ui/components/ResurfacedGroupView.kt`

**Interfaces:**
- Consumes: `HomeViewModel`, `SearchViewModel`, `DetailViewModel`.
- Produces: Giao diện người dùng Material 3 hiện đại:
  - Màn hình chính (Lưới thẻ nhớ, hiển thị trạng thái xử lý AI, phân loại môn học).
  - Màn hình tìm kiếm (Thanh search tự nhiên, chips gợi ý mốc thời gian, kết quả có highlight ghi chú).
  - Màn hình chi tiết (Xem ảnh, tóm tắt, tag môn học, copy text OCR, và khối "Gợi ý tri thức liên quan" 3 nhóm).

- [ ] **Step 1: Xây dựng Theme Material 3 và Navigation Component**
- [ ] **Step 2: Cài đặt component `ScreenshotCard.kt` hiển thị thumbnail (Coil), badge môn học, ngày chụp và ghi chú**
- [ ] **Step 3: Cài đặt `HomeScreen.kt` với Pull-to-refresh và lọc theo môn học**
- [ ] **Step 4: Cài đặt `SearchScreen.kt` với kết quả thời gian thực và highlight text**
- [ ] **Step 5: Cài đặt `DetailScreen.kt` với `ResurfacedGroupView.kt` hiển thị 3 nhóm kết nối tri thức**
- [ ] **Step 6: Commit UI Layer**

---

### Task 7: Demo Data Seeder & Script Kiểm Thử Nhanh

**Files:**
- Create: `app/src/main/java/com/remora/app/data/seed/DemoDataSeeder.kt`
- Create: `scripts/test_gemini_vision.py`
- Create: `scripts/test_memory_resurface.py`

**Interfaces:**
- Consumes: Danh sách 30-50 bài giảng mẫu (Xác suất thống kê, Giải tích, Đề bài thi, Lập trình).
- Produces: 
  - `DemoDataSeeder.seed(database: AppDatabase)`: Nạp sẵn kho dữ liệu vào máy chỉ bằng 1 nút bấm trong màn hình Cài đặt, đảm bảo demo tự tin trước BGK dù mạng có lag.
  - Python scripts kiểm tra độc lập API key, prompt vision và logic 2-stage resurfacing trước khi build APK.

- [ ] **Step 1: Viết script `test_gemini_vision.py` kiểm tra kết nối Gemini Flash và output JSON**
- [ ] **Step 2: Viết script `test_memory_resurface.py` kiểm tra prompt phân loại 3 nhóm liên kết**
- [ ] **Step 3: Cài đặt `DemoDataSeeder.kt` nhúng dữ liệu seed sẵn vào app**
- [ ] **Step 4: Commit Seeder & Scripts**

---

## Plan Review & Handoff

Kế hoạch này bao phủ 100% các yêu cầu trong tài liệu đặc tả của Remora (Hackathon 24h), chia nhỏ từng module có kiểm thử và tính độc lập cao.
