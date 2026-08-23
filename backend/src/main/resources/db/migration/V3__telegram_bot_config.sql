-- Bot token, name, and enabled flag are now stored in the database
-- and configured through the admin UI instead of application.yml.
CREATE TABLE telegram_bot_config (
    id           BIGSERIAL PRIMARY KEY,
    bot_token    VARCHAR(2000),     -- AES-GCM encrypted; NULL = not configured
    bot_username VARCHAR(100),      -- cached from Telegram getMe (e.g. "MyTradingBot")
    bot_name     VARCHAR(200),      -- cached display name from getMe
    enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed exactly one row so the service can always UPDATE rather than INSERT.
INSERT INTO telegram_bot_config (enabled) VALUES (FALSE);
