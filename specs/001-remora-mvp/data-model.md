# Data Model Specification: Remora MVP

**Feature Branch**: `001-remora-mvp`  
**Date**: 2026-10-22  
**Status**: APPROVED  
**Related Documents**: [AGENTS.md](../../AGENTS.md), [03-data-ownership.md](../../docs/architecture/03-data-ownership.md), [spec.md](./spec.md)

---

## 1. Overview & Data Ownership Boundaries

Remora implements a **privacy-first data model** with strict boundary separation:
- **Client (Android Room SQLite)** owns all user learning materials, screenshots, summaries, OCR texts, vector embeddings, and resurfacing links.
- **Backend (PostgreSQL)** owns only user credentials, rate-limiting quotas, and technical audit logs. **Zero images, OCR texts, or personal notes are stored on the backend.**

---

## 2. On-Device Data Model (Android Room SQLite)

### 2.1 Table: `memory_card`

Represents an individual captured and processed study asset.

```sql
CREATE TABLE IF NOT EXISTS `memory_card` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `image_uri` TEXT NOT NULL,
    `created_at` INTEGER NOT NULL,
    `user_intent` TEXT,
    `title` TEXT NOT NULL DEFAULT '',
    `summary` TEXT NOT NULL DEFAULT '',
    `tags_json` TEXT NOT NULL DEFAULT '{"subject":"chưa phân loại","topics":[]}',
    `content_type` TEXT NOT NULL DEFAULT 'UNKNOWN',
    `ocr_text` TEXT NOT NULL DEFAULT '',
    `embedding` BLOB,
    `status` TEXT NOT NULL DEFAULT 'PENDING',
    `attempt_count` INTEGER NOT NULL DEFAULT 0,
    `error_code` TEXT
);

CREATE INDEX IF NOT EXISTS `index_memory_card_created_at` ON `memory_card` (`created_at`);
CREATE INDEX IF NOT EXISTS `index_memory_card_status` ON `memory_card` (`status`);
```

#### Field Specifications:
| Field | SQLite Type | Kotlin Type | Constraints / Rules |
| --- | --- | --- | --- |
| `id` | `INTEGER` | `Long` | Primary Key, Auto-increment. |
| `image_uri` | `TEXT` | `String` | Non-null. Local Android URI (`content://media/...` or `file://...`). |
| `created_at` | `INTEGER` | `Long` | Non-null. Epoch timestamp in milliseconds. |
| `user_intent` | `TEXT` | `String?` | Optional student note captured at save time. |
| `title` | `TEXT` | `String` | AI-generated title, maximum 80 characters. Default `""` while pending. |
| `summary` | `TEXT` | `String` | AI-generated summary (2–4 sentences). Preserves formulas and numbers. |
| `tags_json` | `TEXT` | `String` | JSON string. v1 (legacy): `{ "subject": String, "topics": List<String> }`. v2 adds optional `"course"`, `"chapter"`, `"concepts": List<String>` and a `"version"` marker; decoder (`CardTagsCodec`) reads both, never fabricates missing levels, and falls back to `"chưa phân loại"` on malformed JSON. |
| `content_type` | `TEXT` | `ContentType` | Enum string: `THEORY`, `PROBLEM`, `SOLUTION`, `CODE`, `NOTES`, `UNKNOWN`. |
| `ocr_text` | `TEXT` | `String` | Verbatim text transcribed from image. Never sent to logs. |
| `embedding` | `BLOB` | `ByteArray?` | Exactly 3,072 bytes (768 Float32 values, little-endian, L2-normalized). |
| `status` | `TEXT` | `ProcessingStatus` | Enum string: `PENDING`, `DONE`, `FAILED`. |
| `attempt_count` | `INTEGER` | `Int` | Number of AI extraction attempts (max 3 before setting `FAILED`). |
| `error_code` | `TEXT` | `String?` | Standard error code on failure: `TIMEOUT`, `QUOTA_EXCEEDED`, `NETWORK_OFFLINE`, `SCHEMA_ERROR`. |

---

### 2.2 Table: `resurfacing_result`

Represents an identified pedagogical connection between a newly saved card and a historical card.

```sql
CREATE TABLE IF NOT EXISTS `resurfacing_result` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `new_card_id` INTEGER NOT NULL,
    `candidate_card_id` INTEGER NOT NULL,
    `group` TEXT NOT NULL,
    `relation` TEXT NOT NULL,
    `new_info` TEXT NOT NULL DEFAULT '',
    `benefit` TEXT NOT NULL DEFAULT '',
    `reason` TEXT NOT NULL DEFAULT '',
    `confidence` REAL NOT NULL,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`new_card_id`) REFERENCES `memory_card` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`candidate_card_id`) REFERENCES `memory_card` (`id`) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_resurfacing_result_new_card_id` ON `resurfacing_result` (`new_card_id`);
CREATE INDEX IF NOT EXISTS `index_resurfacing_result_candidate_card_id` ON `resurfacing_result` (`candidate_card_id`);
```

#### Field Specifications:
| Field | SQLite Type | Kotlin Type | Constraints / Rules |
| --- | --- | --- | --- |
| `id` | `INTEGER` | `Long` | Primary Key, Auto-increment. |
| `new_card_id` | `INTEGER` | `Long` | Foreign key referencing the newly saved `memory_card(id)`. |
| `candidate_card_id` | `INTEGER` | `Long` | Foreign key referencing the historical `memory_card(id)`. |
| `group` | `TEXT` | `ResurfaceGroup` | Enum string: `SIMILAR`, `COMPLEMENTARY`, `ACTIONABLE`. |
| `relation` | `TEXT` | `ResurfaceRelation` | Enum string: `DUPLICATE`, `SUPPLEMENT`, `EXPLANATION`, `CONTRADICTION`. |
| `new_info` | `TEXT` | `String` | Highlights what is new between the two cards. |
| `benefit` | `TEXT` | `String` | Direct learning payoff for connecting these concepts. |
| `reason` | `TEXT` | `String` | Student-facing explanation string. |
| `confidence` | `REAL` | `Float` | Model confidence score. Must satisfy `confidence > 0.80`. |
| `created_at` | `INTEGER` | `Long` | Epoch timestamp in milliseconds. |

---

### 2.3 Table: `app_setting`

Stores persistent device settings for screenshot capture and notifications.

```sql
CREATE TABLE IF NOT EXISTS `app_setting` (
    `id` INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
    `notifications_enabled` INTEGER NOT NULL DEFAULT 1,
    `paused` INTEGER NOT NULL DEFAULT 0,
    `pause_until` INTEGER NOT NULL DEFAULT 0
);
```

---

## 3. Backend Data Model (PostgreSQL 16)

The backend database contains 3 operational tables initialized via `services/gateway/init.sql`.

```sql
CREATE SCHEMA IF NOT EXISTS remora_auth;

CREATE TABLE IF NOT EXISTS remora_auth.users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(20) DEFAULT 'STUDENT',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS remora_auth.user_quotas (
    user_id VARCHAR(64) PRIMARY KEY REFERENCES remora_auth.users(id) ON DELETE CASCADE,
    daily_card_limit INTEGER NOT NULL DEFAULT 50,
    used_today INTEGER NOT NULL DEFAULT 0,
    resurface_batch_limit INTEGER NOT NULL DEFAULT 100,
    resurface_used_today INTEGER NOT NULL DEFAULT 0,
    last_reset_date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE IF NOT EXISTS remora_auth.audit_access_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    route VARCHAR(100) NOT NULL,
    status_code INTEGER NOT NULL,
    latency_ms INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_request_id ON remora_auth.audit_access_logs(request_id);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON remora_auth.audit_access_logs(user_id);
```

---

## 4. Enums & Domain Types

### 4.1 ContentType
```kotlin
enum class ContentType {
    THEORY,      // Lý thuyết, định nghĩa, định lý
    PROBLEM,     // Đề bài tập, câu hỏi trắc nghiệm
    SOLUTION,    // Lời giải bài tập, đáp án
    CODE,        // Đoạn mã nguồn, cú pháp lập trình
    NOTES,       // Ghi chép tự do của học viên
    UNKNOWN      // Chưa xác định được định dạng
}
```

### 4.2 ProcessingStatus
```kotlin
enum class ProcessingStatus {
    PENDING,     // Thẻ mới tạo, đang chờ xử lý AI ngầm
    DONE,        // Đã trích xuất thông tin và vector embedding thành công
    FAILED       // Quá số lần thử lại hoặc gặp lỗi không thể phục hồi
}
```

### 4.3 ResurfaceGroup
```kotlin
enum class ResurfaceGroup {
    SIMILAR,         // Tương đồng (ôn tập củng cố)
    COMPLEMENTARY,   // Bổ trợ (lý thuyết - bài tập, liên môn)
    ACTIONABLE       // Cần hành động (phát hiện mâu thuẫn, bài tập cần làm)
}
```

### 4.4 ResurfaceRelation
```kotlin
enum class ResurfaceRelation {
    DUPLICATE,       // Trùng lặp nội dung
    SUPPLEMENT,      // Bổ sung kiến thức còn thiếu
    EXPLANATION,     // Giải thích lý do hoặc chứng minh
    CONTRADICTION    // Mâu thuẫn giả định hoặc đối nghịch
}
```

---

## 5. Domain Validation & Compatibility Rules

### 5.1 Resurfacing Group-Relation Compatibility Matrix
Every resurfacing link MUST adhere to this exact grouping rule. Any candidate returning an incompatible relation is rejected.

| Resurface Group | Permitted Relations | Rejection Behavior |
| --- | --- | --- |
| `SIMILAR` | `DUPLICATE`, `SUPPLEMENT` | Drop candidate if relation is `EXPLANATION` or `CONTRADICTION` |
| `COMPLEMENTARY` | `EXPLANATION`, `SUPPLEMENT` | Drop candidate if relation is `DUPLICATE` or `CONTRADICTION` |
| `ACTIONABLE` | `SUPPLEMENT`, `CONTRADICTION` | Drop candidate if relation is `DUPLICATE` or `EXPLANATION` |

### 5.2 Resurfacing Output Invariants
1. **Integer Candidate ID Check:** `candidate_id` MUST be an integer matching an ID present in the request's candidate pool. Any returned ID not in the input is discarded.
2. **Confidence Threshold:** `confidence > 0.80` (strictly greater than). Any candidate with `confidence <= 0.80` is filtered out.
3. **Group Quota Limit:** At most **2 items per group** (maximum 6 resurfaced links per new card).
4. **Negative Cases:** If no genuine educational link exists, the output array is empty (`"results": []`).

### 5.3 Vector BLOB Encoding & Decoding Invariants
1. **Vector Dimension:** Must contain exactly 768 elements (`FloatArray(768)`).
2. **Byte Length:** Exactly $768 \times 4 = 3,072$ bytes.
3. **Byte Order:** Little-Endian IEEE 754 Float32.
4. **L2 Normalization:** Prior to saving, $\sqrt{\sum_{i=0}^{767} v_i^2} = 1.0 \pm 10^{-5}$.
5. **Similarity Evaluation:** Dot product $\sum_{i=0}^{767} a_i \times b_i$ yields the exact Cosine Similarity.

## Approved AI Pipeline Decisions and Planning Updates (2026-10-24)

These decisions supersede any conflicting earlier wording in this artifact:

- Search phrase "tuần trước" means the rolling window from 14 days before now through 7 days before now, not the previous calendar week. Add fixed-clock tests for the boundaries.
- When a screenshot hash matches an existing card, ask the user whether to reuse/link to the existing card or save a separate card. Never silently deduplicate or silently create a duplicate.
- Prefer a versioned JSON decoder and lazy normalization for legacy tags JSON. Use Room migration only if a concrete requirement cannot be met safely without a database schema change.
- SQLite FTS remains conditional. Implement it only if a controlled, versioned benchmark demonstrates meaningful benefit over semantic-only retrieval without unacceptable latency, reliability, or privacy regressions.
- These planning updates do not authorize product-code changes. Product-code implementation remains blocked until the user approves the final implementation plan.
