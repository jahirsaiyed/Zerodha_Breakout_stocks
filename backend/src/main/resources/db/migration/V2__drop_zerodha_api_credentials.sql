-- API key and secret are now server-level config (ZERODHA_API_KEY / ZERODHA_API_SECRET env vars).
-- Each user only retains their own access token obtained via OAuth.
ALTER TABLE user_configs DROP COLUMN IF EXISTS zerodha_api_key;
ALTER TABLE user_configs DROP COLUMN IF EXISTS zerodha_api_secret;
