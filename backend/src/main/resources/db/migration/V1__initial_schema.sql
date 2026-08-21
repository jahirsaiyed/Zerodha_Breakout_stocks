CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER'
                  CHECK (role IN ('ADMIN', 'USER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE user_configs (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    max_positions          INT          NOT NULL DEFAULT 5,
    position_sizing_method VARCHAR(20)  NOT NULL DEFAULT 'FIXED'
                           CHECK (position_sizing_method IN ('EQUAL','FIXED','RISK_BASED')),
    position_sizing_value  NUMERIC(18,2) NOT NULL DEFAULT 10000,
    order_expiry_days      INT          NOT NULL DEFAULT 5,
    zerodha_api_key        VARCHAR(255),
    zerodha_api_secret     VARCHAR(1000),
    zerodha_access_token   VARCHAR(2000),
    zerodha_totp_secret    VARCHAR(1000),
    telegram_chat_id       VARCHAR(100),
    zerodha_connected      BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE signals (
    id                BIGSERIAL PRIMARY KEY,
    symbol            VARCHAR(50)   NOT NULL,
    entry_price       NUMERIC(18,2) NOT NULL,
    stop_loss         NUMERIC(18,2) NOT NULL,
    target            NUMERIC(18,2) NOT NULL,
    risk_reward_ratio NUMERIC(10,4) NOT NULL,
    source            VARCHAR(20)   NOT NULL
                      CHECK (source IN ('GOOGLE_SHEET','MANUAL')),
    source_ref        VARCHAR(255),
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED')),
    notes             TEXT,
    added_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE positions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    signal_id       BIGINT        REFERENCES signals(id),
    symbol          VARCHAR(50)   NOT NULL,
    quantity        INT           NOT NULL,
    avg_entry_price NUMERIC(18,2),
    entry_order_id  VARCHAR(255),
    gtt_order_id    VARCHAR(255),
    status          VARCHAR(25)   NOT NULL DEFAULT 'PENDING_ENTRY'
                    CHECK (status IN ('PENDING_ENTRY','ACTIVE','CANCELLED',
                                      'CLOSED_TARGET','CLOSED_SL','CLOSED_MANUAL')),
    opened_at       TIMESTAMP,
    closed_at       TIMESTAMP,
    realised_pnl    NUMERIC(18,2)
);

CREATE UNIQUE INDEX uq_position_user_signal_active
    ON positions(user_id, signal_id)
    WHERE status IN ('PENDING_ENTRY', 'ACTIVE');

CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    position_id      BIGINT        REFERENCES positions(id),
    zerodha_order_id VARCHAR(255),
    type             VARCHAR(20)   NOT NULL
                     CHECK (type IN ('ENTRY','EXIT_TARGET','EXIT_SL')),
    order_kind       VARCHAR(20)   NOT NULL
                     CHECK (order_kind IN ('LIMIT','GTT_OCO','MARKET')),
    symbol           VARCHAR(50)   NOT NULL,
    quantity         INT           NOT NULL,
    price            NUMERIC(18,2),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','FILLED','CANCELLED','REJECTED')),
    placed_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE signal_sync_log (
    id               BIGSERIAL PRIMARY KEY,
    synced_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    source           VARCHAR(20) NOT NULL CHECK (source IN ('GOOGLE_SHEET','MANUAL')),
    signals_added    INT         NOT NULL DEFAULT 0,
    signals_modified INT         NOT NULL DEFAULT 0,
    signals_removed  INT         NOT NULL DEFAULT 0,
    notes            TEXT
);
