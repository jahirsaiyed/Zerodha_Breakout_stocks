# Task 1 Brief: Project Scaffold + Docker Compose

## Context
This is Task 1 of 7 in Phase 1 (Foundation) of a Trading Portfolio Management System for Indian stocks.
Project root: `D:\Zerodha_Breakout_stocks`
Git is already initialized on branch `main` with one commit (docs only).

## Your Job
Create the project scaffold: all infrastructure files, the Maven backend project, and the frontend project skeleton. Do NOT write Java application code yet — that comes in Tasks 2–7.

## Global Constraints (apply to all your output)
- Java 21 LTS; Spring Boot 3.3.5; package root `com.trading`
- All REST endpoints prefixed `/api`
- JWT in `httpOnly` cookie named `jwt`; 24-hour expiry; `SameSite=Strict`
- All API responses: `{ "success": true|false, "data": ..., "error": null|string }`
- Schema owned exclusively by Flyway — `ddl-auto: validate` always
- `ENCRYPTION_KEY` env var: minimum 32 characters; `JWT_SECRET`: minimum 64 characters

## Files to Create

### Root level
- `docker-compose.yml` — services: postgres (16-alpine, healthcheck), backend (builds ./backend), frontend (builds ./frontend)
- `nginx.conf` — reverse proxy: `/api/` → backend:8080, `/` → frontend:80
- `.env.example` — all env vars with placeholder values and comments

### backend/
- `pom.xml` — Spring Boot 3.3.5 parent, Java 21, dependencies: web, data-jpa, security, validation, postgresql (runtime), flyway-core, flyway-database-postgresql, jjwt-api/impl/jackson (0.12.6), lombok, spring-boot-starter-test, spring-security-test
- `Dockerfile` — multi-stage: eclipse-temurin:21-jdk-alpine build stage → eclipse-temurin:21-jre-alpine runtime; uses Maven Wrapper (./mvnw)
- Generate Maven Wrapper: run `mvn wrapper:wrapper -Dmaven=3.9.6` inside backend/ (requires Maven installed) OR create `.mvn/wrapper/maven-wrapper.properties` manually if Maven not available

### frontend/
- `Dockerfile` — multi-stage: node:20-alpine build → nginx:alpine serve `/app/dist`
- `nginx.conf` — SPA fallback: `try_files $uri $uri/ /index.html`

**Important:** The `backend/src/` directory structure, Java files, and `frontend/src/` files are NOT created in this task. Only infrastructure and build files.

## Exact docker-compose.yml content to create:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ${DB_NAME:-trading}
      POSTGRES_USER: ${DB_USERNAME:-trading}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-trading}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-trading}"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    env_file: .env
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/${DB_NAME:-trading}
      DB_USERNAME: ${DB_USERNAME:-trading}
      DB_PASSWORD: ${DB_PASSWORD:-trading}
    depends_on:
      postgres:
        condition: service_healthy
    ports:
      - "8080:8080"

  frontend:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - backend

volumes:
  postgres_data:
```

## Verification
After creating all files:
1. `docker compose config` — should show valid config with no errors
2. `docker compose up postgres -d` — postgres should reach "healthy" state
3. `docker compose down` — clean up

**SKIP the `git init` step** — git is already initialized. Just commit at the end.

## Commit message
```
feat: project scaffold — Docker Compose, Maven pom, Dockerfiles
```

## Report
Write your report to: `D:\Zerodha_Breakout_stocks\.superpowers\sdd\task-1-report.md`

Include:
- STATUS: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Files created (list)
- Verification results (docker compose config output summary)
- Any concerns or deviations from the plan
- Commits made (hash + message)
