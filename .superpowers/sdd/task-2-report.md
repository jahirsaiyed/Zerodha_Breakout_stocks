STATUS: DONE_WITH_CONCERNS

FILES_CREATED:
- backend/src/main/resources/application.yml
- backend/src/main/resources/db/migration/V1__initial_schema.sql
- backend/src/main/java/com/trading/TradingApplication.java

VERIFICATION:
Docker was not running at task execution time. Live Flyway migration run was skipped.
SQL was verified by static review:
- All 6 tables present: users, user_configs, signals, positions, orders, signal_sync_log
- All constraints use VARCHAR CHECK (not PostgreSQL ENUMs) as specified
- Foreign keys: user_configs.user_id -> users.id (UNIQUE), positions.user_id -> users.id,
  positions.signal_id -> signals.id, orders.user_id -> users.id, orders.position_id -> positions.id
- Partial unique index uq_position_user_signal_active on positions(user_id, signal_id)
  WHERE status IN ('PENDING_ENTRY', 'ACTIVE') is correct
- All BIGSERIAL PKs, NUMERIC(18,2) for prices, TIMESTAMP (UTC stored via application.yml
  hibernate.jdbc.time_zone: UTC)
- application.yml sets ddl-auto: validate (never create/update)
- JWT_SECRET and ENCRYPTION_KEY bound from environment variables with no defaults (fail-fast)
- cors.allowed-origin bound from CORS_ALLOWED_ORIGINS with default http://localhost:5173
- TradingApplication.java has @SpringBootApplication + @EnableScheduling in package com.trading

COMMITS: 05118fa - feat: database schema V1 migration and Spring Boot bootstrap

CONCERNS:
- Docker not running: Flyway migration could not be executed live. SQL correctness verified by
  manual review only. Run `docker compose up postgres -d` then
  `./mvnw spring-boot:run` (with JWT_SECRET and ENCRYPTION_KEY env vars set) to confirm
  "Successfully applied 1 migration" in the log.
- orders.type uses column name `type` which is a reserved word in some SQL dialects; it is
  valid in PostgreSQL and the CHECK constraint is correct, but downstream JPA mapping will
  need @Column(name="type") on the entity field to avoid ambiguity.
