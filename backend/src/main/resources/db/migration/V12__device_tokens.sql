CREATE TABLE device_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      TEXT NOT NULL,
    platform   VARCHAR(10) NOT NULL CHECK (platform IN ('FCM', 'APNS')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, token)
);
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);
