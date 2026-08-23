-- Per-user Telegram bot configuration.
-- Each user can connect their own bot and receive notifications via it.
ALTER TABLE user_configs
    ADD COLUMN telegram_bot_token    VARCHAR(2000),  -- AES-GCM encrypted; NULL = not configured
    ADD COLUMN telegram_bot_username VARCHAR(100),   -- cached from Telegram getMe
    ADD COLUMN telegram_bot_name     VARCHAR(200);   -- cached display name from getMe
