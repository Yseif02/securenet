-- SecureNet Data Storage Layer Schema (PostgreSQL)
-- Covers all 5 data domains: users, devices, events, video, firmware

-- =====================================================================
-- User data
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    user_id       VARCHAR(255) PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS password_hashes (
    user_id       VARCHAR(255) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    token_value   VARCHAR(255) PRIMARY KEY,
    user_id       VARCHAR(255) NOT NULL,
    issued_at     TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    token_value   VARCHAR(255) PRIMARY KEY,
    revoked_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- Device state
-- =====================================================================

CREATE TABLE IF NOT EXISTS devices (
    device_id        VARCHAR(255) PRIMARY KEY,
    display_name     VARCHAR(255) NOT NULL,
    device_type      VARCHAR(50)  NOT NULL,
    owner_id         VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    registered_at    TIMESTAMP    NOT NULL,
    firmware_version VARCHAR(50)  NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(user_id)
);

-- =====================================================================
-- Security event history
-- =====================================================================

CREATE TABLE IF NOT EXISTS security_events (
    event_id    VARCHAR(255) PRIMARY KEY,
    device_id   VARCHAR(255) NOT NULL,
    owner_id    VARCHAR(255) NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    occurred_at TIMESTAMP    NOT NULL,
    metadata    TEXT
);

CREATE INDEX IF NOT EXISTS idx_events_device_time ON security_events(device_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_events_owner_type  ON security_events(owner_id, event_type, occurred_at);

-- =====================================================================
-- Video archive
-- =====================================================================

CREATE TABLE IF NOT EXISTS video_clips (
    clip_id         VARCHAR(255) PRIMARY KEY,
    device_id       VARCHAR(255) NOT NULL,
    owner_id        VARCHAR(255) NOT NULL,
    start_time      TIMESTAMP    NOT NULL,
    duration_millis BIGINT       NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    storage_key     VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS video_bytes (
    storage_key VARCHAR(255) PRIMARY KEY,
    raw_bytes   BYTEA NOT NULL
);

-- =====================================================================
-- Firmware binaries
-- =====================================================================

CREATE TABLE IF NOT EXISTS firmware (
    device_type_key  VARCHAR(50)  NOT NULL,
    firmware_version VARCHAR(50)  NOT NULL,
    binary_data      BYTEA        NOT NULL,
    PRIMARY KEY (device_type_key, firmware_version)
);

CREATE TABLE IF NOT EXISTS latest_firmware (
    device_type_key  VARCHAR(50)  PRIMARY KEY,
    firmware_version VARCHAR(50)  NOT NULL
);

-- =====================================================================
-- Push token registry
-- =====================================================================

CREATE TABLE IF NOT EXISTS push_tokens (
    token     VARCHAR(255) PRIMARY KEY,
    user_id   VARCHAR(255) NOT NULL,
    platform  VARCHAR(10)  NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON push_tokens(user_id);
