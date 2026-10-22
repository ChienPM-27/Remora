# Quickstart Validation Guide & Demo Runbook: Remora MVP

**Feature Branch**: `001-remora-mvp`  
**Date**: 2026-10-22  
**Status**: APPROVED  
**Related Documents**: [spec.md](./spec.md), [data-model.md](./data-model.md), [runbook-ec2.md](../../docs/deployment/runbook-ec2.md)

---

## 1. Prerequisites & Environment Setup

### 1.1 Developer Machine Prerequisites
- **Operating System**: Windows 11, macOS, or Ubuntu Linux.
- **Java Development Kit**: JDK 17 or JDK 21 (Temurin / Zulu).
- **Android SDK**: Build Tools 34.0.0, Platform SDK 34 (`minSdk = 28`).
- **Container Runtime**: Docker Desktop or Docker Engine with Docker Compose v2.
- **Node.js**: v18+ (for running validation scripts in `scripts/`).

### 1.2 Local Secrets & Properties Configuration
Create or configure `local.properties` in the project root (**never committed to git**):
```properties
sdk.dir=C:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk
# For Android Emulator:
PROXY_BASE_URL=http://10.0.2.2:8080/v1
# For Real Phone via Wi-Fi or EC2 Staging:
# PROXY_BASE_URL=http://192.168.1.50:8080/v1
# PROXY_BASE_URL=https://demo.remora.study/v1
```

Backend environment file `services/.env` (**never committed to git**):
```env
PORT=8080
JWT_SECRET=remora_super_secret_jwt_key_hackathon_2026
GEMINI_API_KEY=AIzaSy...
GEMINI_VISION_MODEL=gemini-3.5-flash-lite
GEMINI_VISION_FALLBACK_MODEL=gemini-3.5-flash
GEMINI_EMBEDDING_MODEL=gemini-embedding-2
DATABASE_URL=jdbc:postgresql://postgres:5432/remora_db
DB_USER=remora_user
DB_PASSWORD=remora_pass_secure
INTERNAL_SERVICE_KEY=remora_internal_mesh_key_2026
```

---

## 2. Quick Validation Commands

### 2.1 Backend Services Validation
Run backend containers locally via Docker Compose:
```bash
cd services
docker compose up -d --build
```

Verify service health and authentication:
```bash
# 1. Health check
curl -i http://localhost:8080/v1/health

# 2. Login demo to acquire JWT token
curl -i -X POST http://localhost:8080/v1/auth/login-demo \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_student_01","password":"password123"}'
```

### 2.2 Android Client Build & Unit Tests Validation
Execute pure JVM logic unit tests and assemble the debug APK:
```powershell
./gradlew testDebugUnitTest assembleDebug
```
*Expected Outcome*:
- `testDebugUnitTest`: All VectorMath, BLOB encode/decode, Resurfacing grouping, and TimeResolver tests pass with 0 failures.
- `assembleDebug`: `app-debug.apk` built successfully at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 3. End-to-End Demo Runbook (24h Hackathon Presentation)

### Step 1: Pre-seed Demo Knowledge Library
1. Install `app-debug.apk` onto the demo Android phone.
2. Launch Remora; the app automatically logs in with `demo_student_01` and seeds 30–50 canonical cards covering Mathematics (Pythagoras, Thales, Calculus), Physics (Newton's Laws, Optics), Chemistry, and Computer Science (Binary Search, QuickSort).
3. Confirm that all pre-seeded cards show status `DONE` with vector embeddings pre-calculated.

### Step 2: Live Screenshot Capture & Intent Attachment
1. Open a web browser or slide viewer displaying a lecture slide on **"Định lý Thales"** (or use `scripts/demo-images/02_math_thales.png`).
2. Take a screenshot using physical buttons ($Power + Volume Down$).
3. **Verify within 1.5s**: Remora system notification appears with actions:
   - **Save** (Lưu)
   - **Skip** (Bỏ qua)
   - **Mute 1h** (Tắt thông báo 1h)
   - **Pause** (Tạm dừng)
4. Tap **Save** and enter user intent: *"Công thức Thales ôn thi giữa kỳ"*.
5. **Verify within 300ms**: Notification dismisses immediately; card is created in local gallery as `PENDING`.

### Step 3: AI Processing & Resurfacing Notification
1. WorkManager transmits compressed image to `/v1/ai/extract-card`.
2. AI extraction updates the card to `DONE`, populating title *"Định lý Thales trong tam giác"*, summary, subject *"Hình học"*, and `THEORY`.
3. Memory Resurfacing runs:
   - Stage 1 selects top-15 historical cards.
   - Stage 2 evaluates candidates via single batch LLM call.
4. **Resurfacing Result Alert**:
   - Under **COMPLEMENTARY**: surfaces *"Hệ quả định lý Thales"* (Relation: `EXPLANATION`, Reason: *"Cung cấp các công thức mở rộng cho bài tập hình học tam giác"*).
   - Under **ACTIONABLE**: surfaces *"Bài tập tính cạnh tam giác bằng Thales"* (Relation: `SUPPLEMENT`, Reason: *"Áp dụng trực tiếp công thức Thales vừa chụp"*).
   - Both cards satisfy `confidence > 0.80`.

### Step 4: Semantic Search with Fuzzy Time Expression
1. Navigate to the **Search** tab.
2. Enter conversational Vietnamese query: *"cái slide về phân phối chuẩn hôm bữa chụp"*.
3. **Verify**:
   - The time resolver translates *"hôm bữa"* to $[now - 21\text{ days}, now]$.
   - Search ranks the **Gaussian Normal Distribution** slide as the **#1 result**.
   - Original student intent note is prominently highlighted.
   - Below-threshold irrelevant cards are omitted (no noise).

### Step 5: Negative Case & Boundary Verification
1. Capture an unrelated slide on **"English Irregular Verbs"**.
2. Tap Save without intent.
3. **Verify Resurfacing Negative Case**: Because no genuine pedagogical link exists with the math/science library, the system returns an empty list (`[]`). No irrelevant connections are forced.
