-- Remora Gateway Database Initialization (PostgreSQL 16)
-- Schema for authentication, user quotas, and technical audit logs

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
    resurface_batch_limit INTEGER NOT NULL DEFAULT 50,
    resurface_used_today INTEGER NOT NULL DEFAULT 0,
    embedding_limit INTEGER NOT NULL DEFAULT 300,
    embedding_used_today INTEGER NOT NULL DEFAULT 0,
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

-- Seed demo user account for Hackathon (password: password123)
-- SHA-256 of "password123": ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f
INSERT INTO remora_auth.users (id, username, password_hash, display_name, role, status)
VALUES (
    'usr_demo_student_01',
    'demo_student_01',
    'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f',
    'Sinh viên Demo (Remora)',
    'STUDENT',
    'ACTIVE'
) ON CONFLICT (username) DO NOTHING;

INSERT INTO remora_auth.user_quotas (user_id, daily_card_limit, used_today, resurface_batch_limit, resurface_used_today, embedding_limit, embedding_used_today)
VALUES (
    'usr_demo_student_01',
    50,
    0,
    50,
    0,
    300,
    0
) ON CONFLICT (user_id) DO NOTHING;
