# Trading System — Phase 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the complete project with a deployable Spring Boot backend, PostgreSQL with full schema, JWT auth, user management API, and a React frontend with login and protected routing.

**Architecture:** Modular monolith Spring Boot 3.3 application. Six bounded modules as Java packages under `com.trading`: `users`, `auth`, `signals`, `portfolio`, `broker`, `notifications`. React 18 TypeScript frontend. Docker Compose wires all services. This is Plan 1 of 6 — Plans 2–6 cover Signals, Broker, Portfolio Engine, Notifications, and Full Frontend.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven + Maven Wrapper, PostgreSQL 16, Flyway 10, Spring Security 6, JJWT 0.12.6, Lombok, React 18, TypeScript 5, Vite 5, React Router v6, TanStack Query v5, Axios, Tailwind CSS 3, Docker Compose 2.x

## Global Constraints

- Java: 21 LTS; Spring Boot: 3.3.5; package root: `com.trading`
- All REST endpoints prefixed `/api` — e.g., `/api/auth/login`
- JWT in `httpOnly` cookie named `jwt`; 24-hour expiry; `SameSite=Strict`
- Passwords hashed with BCrypt; never log or return plaintext
- All API responses: `{ "success": true|false, "data": ..., "error": null|string }`
- Schema owned exclusively by Flyway — `ddl-auto: validate` always
- Zerodha API secret, access token, TOTP secret: AES-256-GCM encrypted before DB storage
- `ENCRYPTION_KEY` env var: minimum 32 characters; `JWT_SECRET`: minimum 64 characters
- Timestamps stored UTC in PostgreSQL; displayed as IST (`Asia/Kolkata`) in frontend
- CORS allowed origin from env var `ALLOWED_ORIGIN`

---

## File Structure

```
D:\Zerodha_Breakout_stocks\
├── docker-compose.yml
├── nginx.conf
├── .env.example
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/trading/
│       │   │   ├── TradingApplication.java
│       │   │   ├── common/
│       │   │   │   ├── ApiResponse.java
│       │   │   │   ├── GlobalExceptionHandler.java
│       │   │   │   └── EncryptionUtil.java
│       │   │   ├── config/
│       │   │   │   └── SecurityConfig.java
│       │   │   ├── auth/
│       │   │   │   ├── JwtUtil.java
│       │   │   │   ├── JwtFilter.java
│       │   │   │   ├── AuthService.java
│       │   │   │   ├── AuthController.java
│       │   │   │   └── dto/LoginRequest.java
│       │   │   └── users/
│       │   │       ├── User.java
│       │   │       ├── UserConfig.java
│       │   │       ├── UserRepository.java
│       │   │       ├── UserConfigRepository.java
│       │   │       ├── UserService.java
│       │   │       ├── UserController.java
│       │   │       ├── AdminController.java
│       │   │       └── dto/
│       │   │           ├── CreateUserRequest.java
│       │   │           ├── UpdateConfigRequest.java
│       │   │           ├── UserResponse.java
│       │   │           └── UserConfigResponse.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__initial_schema.sql
│       └── test/java/com/trading/
│           ├── auth/AuthControllerTest.java
│           └── users/UserServiceTest.java
└── frontend/
    ├── Dockerfile
    ├── nginx.conf
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── tailwind.config.js
    ├── postcss.config.js
    ├── index.html
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── lib/
        │   └── api.ts
        ├── contexts/
        │   └── AuthContext.tsx
        ├── components/
        │   └── ProtectedRoute.tsx
        └── pages/
            ├── LoginPage.tsx
            └── DashboardPage.tsx
```

---

### Task 1: Project Scaffold + Docker Compose

**Files:**
- Create: `docker-compose.yml`
- Create: `nginx.conf`
- Create: `.env.example`
- Create: `backend/pom.xml`
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`

**Interfaces:**
- Produces: running PostgreSQL on port 5432; backend slot on 8080; frontend slot on 3000

- [ ] **Step 1: Create root docker-compose.yml**

```yaml
# docker-compose.yml
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

- [ ] **Step 2: Create nginx.conf (reverse proxy for production)**

```nginx
# nginx.conf
events { worker_connections 1024; }

http {
  server {
    listen 80;
    server_name _;

    location /api/ {
      proxy_pass http://backend:8080/api/;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
      proxy_pass http://frontend:80/;
      proxy_set_header Host $host;
    }
  }
}
```

- [ ] **Step 3: Create .env.example**

```bash
# .env.example — copy to .env and fill in real values
DB_NAME=trading
DB_USERNAME=trading
DB_PASSWORD=changeme

# Must be at least 64 characters
JWT_SECRET=change-this-to-a-very-long-random-string-at-least-64-chars-long!!

# Must be at least 32 characters
ENCRYPTION_KEY=change-this-32-char-encryption-key!

ALLOWED_ORIGIN=http://localhost:3000
```

- [ ] **Step 4: Create backend/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
    </parent>
    <groupId>com.trading</groupId>
    <artifactId>trading-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>21</java.version>
    </properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
        <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: Create backend/Dockerfile**

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:resolve -q
COPY src ./src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: Create frontend/Dockerfile**

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [ ] **Step 7: Create frontend/nginx.conf (SPA fallback)**

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 8: Generate Maven Wrapper inside backend/**

```bash
cd backend
# Requires Maven 3.9+ installed locally
mvn wrapper:wrapper -Dmaven=3.9.6
```

Expected: `.mvn/wrapper/maven-wrapper.properties` created, `mvnw` and `mvnw.cmd` present.

- [ ] **Step 9: Verify Docker Compose starts PostgreSQL**

```bash
cp .env.example .env
# Edit .env with real secrets if desired, defaults work for dev
docker compose up postgres -d
docker compose ps
```

Expected: `postgres` shows status `healthy`.

- [ ] **Step 10: Commit**

```bash
git init
git add .
git commit -m "feat: project scaffold with Docker Compose and Maven pom"
```

---

### Task 2: Database Schema + Spring Boot Bootstrap

**Files:**
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `backend/src/main/java/com/trading/TradingApplication.java`

**Interfaces:**
- Produces: all 6 tables in PostgreSQL; application starts without errors

- [ ] **Step 1: Create application.yml**

```yaml
# backend/src/main/resources/application.yml
spring:
  application:
    name: trading-backend
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/trading}
    username: ${DB_USERNAME:trading}
    password: ${DB_PASSWORD:trading}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

jwt:
  secret: ${JWT_SECRET}

encryption:
  key: ${ENCRYPTION_KEY}

cors:
  allowed-origin: ${ALLOWED_ORIGIN:http://localhost:5173}

logging:
  level:
    root: INFO
    com.trading: DEBUG
```

- [ ] **Step 2: Create V1__initial_schema.sql**

```sql
-- backend/src/main/resources/db/migration/V1__initial_schema.sql

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

-- Prevent duplicate active/pending positions for the same user+signal
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
```

- [ ] **Step 3: Create TradingApplication.java**

```java
// backend/src/main/java/com/trading/TradingApplication.java
package com.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }
}
```

- [ ] **Step 4: Start PostgreSQL and verify migration runs**

```bash
docker compose up postgres -d
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/trading
export DB_USERNAME=trading
export DB_PASSWORD=trading
export JWT_SECRET=test-secret-at-least-64-characters-long-for-testing-purposes-here
export ENCRYPTION_KEY=test-encryption-key-32chars!!!!!
./mvnw spring-boot:run
```

Expected: Application starts, logs show `Successfully applied 1 migration to schema "public"`. Stop with Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: database schema V1 migration and Spring Boot bootstrap"
```

---

### Task 3: Common Module

**Files:**
- Create: `backend/src/main/java/com/trading/common/ApiResponse.java`
- Create: `backend/src/main/java/com/trading/common/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/trading/common/EncryptionUtil.java`

**Interfaces:**
- Produces:
  - `ApiResponse.success(T data)` → `ApiResponse<T>`
  - `ApiResponse.error(String message)` → `ApiResponse<Void>`
  - `EncryptionUtil.encrypt(String plaintext)` → `String` (Base64 AES-256-GCM ciphertext)
  - `EncryptionUtil.decrypt(String ciphertext)` → `String`

- [ ] **Step 1: Write the failing test for EncryptionUtil**

```java
// backend/src/test/java/com/trading/common/EncryptionUtilTest.java
package com.trading.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EncryptionUtilTest {
    private final EncryptionUtil util = new EncryptionUtil("test-key-32-characters-long!!!!!");

    @Test
    void encryptAndDecrypt_roundTrips() {
        String plaintext = "my-secret-api-key";
        String encrypted = util.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(util.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String a = util.encrypt("same");
        String b = util.encrypt("same");
        assertThat(a).isNotEqualTo(b); // different IV each time
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -pl . -Dtest=EncryptionUtilTest -q 2>&1 | tail -5
```

Expected: `FAILED` — `EncryptionUtil` does not exist yet.

- [ ] **Step 3: Create ApiResponse.java**

```java
// backend/src/main/java/com/trading/common/ApiResponse.java
package com.trading.common;

public record ApiResponse<T>(boolean success, T data, String error) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
```

- [ ] **Step 4: Create EncryptionUtil.java**

```java
// backend/src/main/java/com/trading/common/EncryptionUtil.java
package com.trading.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class EncryptionUtil {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final byte[] keyBytes;

    public EncryptionUtil(@Value("${encryption.key}") String key) {
        byte[] raw = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        keyBytes = new byte[32];
        System.arraycopy(raw, 0, keyBytes, 0, Math.min(raw.length, 32));
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

- [ ] **Step 5: Create GlobalExceptionHandler.java**

```java
// backend/src/main/java/com/trading/common/GlobalExceptionHandler.java
package com.trading.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error"));
    }
}
```

- [ ] **Step 6: Run EncryptionUtil test to verify it passes**

```bash
cd backend && ./mvnw test -Dtest=EncryptionUtilTest -q
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add . && git commit -m "feat: common module — ApiResponse, EncryptionUtil, GlobalExceptionHandler"
```

---

### Task 4: Users Module

**Files:**
- Create: `backend/src/main/java/com/trading/users/User.java`
- Create: `backend/src/main/java/com/trading/users/UserConfig.java`
- Create: `backend/src/main/java/com/trading/users/UserRepository.java`
- Create: `backend/src/main/java/com/trading/users/UserConfigRepository.java`
- Create: `backend/src/main/java/com/trading/users/UserService.java`
- Create: `backend/src/main/java/com/trading/users/dto/` (4 DTOs)
- Test: `backend/src/test/java/com/trading/users/UserServiceTest.java`

**Interfaces:**
- Consumes: `EncryptionUtil`, `PasswordEncoder` (from SecurityConfig Task 5 — add `@Lazy` if circular dep)
- Produces:
  - `UserService.createUser(CreateUserRequest)` → `UserResponse`
  - `UserService.getUserByEmail(String email)` → `UserResponse`
  - `UserService.getConfigByEmail(String email)` → `UserConfigResponse`
  - `UserService.updateConfig(String email, UpdateConfigRequest)` → `UserConfigResponse`
  - `UserService.getAllUsers()` → `List<UserResponse>`
  - `UserService.setUserActive(Long id, boolean active)` → `void`

- [ ] **Step 1: Write the failing UserService tests**

```java
// backend/src/test/java/com/trading/users/UserServiceTest.java
package com.trading.users;

import com.trading.common.EncryptionUtil;
import com.trading.users.dto.CreateUserRequest;
import com.trading.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserConfigRepository userConfigRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EncryptionUtil encryptionUtil;
    @InjectMocks UserService userService;

    @Test
    void createUser_savesUserAndDefaultConfig() {
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        User saved = User.builder().id(1L).name("Alice").email("alice@test.com")
                .passwordHash("hashed").role(User.UserRole.USER).active(true).build();
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse result = userService.createUser(
                new CreateUserRequest("Alice", "alice@test.com", "password123", null));

        assertThat(result.email()).isEqualTo("alice@test.com");
        assertThat(result.role()).isEqualTo("USER");
        verify(userConfigRepository).save(any(UserConfig.class));
    }

    @Test
    void createUser_throwsWhenEmailExists() {
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("Alice", "alice@test.com", "pw", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void setUserActive_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.setUserActive(99L, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=UserServiceTest -q 2>&1 | tail -5
```

Expected: FAILED — `UserService` does not exist.

- [ ] **Step 3: Create User.java**

```java
// backend/src/main/java/com/trading/users/User.java
package com.trading.users;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private UserRole role = UserRole.USER;

    @Column(nullable = false) private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum UserRole { ADMIN, USER }
}
```

- [ ] **Step 4: Create UserConfig.java**

```java
// backend/src/main/java/com/trading/users/UserConfig.java
package com.trading.users;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_configs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "max_positions", nullable = false) private Integer maxPositions = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_sizing_method", nullable = false)
    private PositionSizingMethod positionSizingMethod = PositionSizingMethod.FIXED;

    @Column(name = "position_sizing_value", nullable = false)
    private BigDecimal positionSizingValue = new BigDecimal("10000");

    @Column(name = "order_expiry_days", nullable = false) private Integer orderExpiryDays = 5;

    @Column(name = "zerodha_api_key") private String zerodhaApiKey;
    @Column(name = "zerodha_api_secret") private String zerodhaApiSecret;   // encrypted
    @Column(name = "zerodha_access_token") private String zerodhaAccessToken; // encrypted
    @Column(name = "zerodha_totp_secret") private String zerodhaTotpSecret; // encrypted
    @Column(name = "telegram_chat_id") private String telegramChatId;
    @Column(name = "zerodha_connected", nullable = false) private Boolean zerodhaConnected = false;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public enum PositionSizingMethod { EQUAL, FIXED, RISK_BASED }
}
```

- [ ] **Step 5: Create repositories**

```java
// backend/src/main/java/com/trading/users/UserRepository.java
package com.trading.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

```java
// backend/src/main/java/com/trading/users/UserConfigRepository.java
package com.trading.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {
    Optional<UserConfig> findByUser(User user);
    Optional<UserConfig> findByUser_Email(String email);
}
```

- [ ] **Step 6: Create the 4 DTOs**

```java
// backend/src/main/java/com/trading/users/dto/CreateUserRequest.java
package com.trading.users.dto;
import com.trading.users.User;
import jakarta.validation.constraints.*;
public record CreateUserRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    @NotBlank @Size(min = 8) String password,
    User.UserRole role
) {}
```

```java
// backend/src/main/java/com/trading/users/dto/UpdateConfigRequest.java
package com.trading.users.dto;
import com.trading.users.UserConfig;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record UpdateConfigRequest(
    @Min(1) @Max(50) Integer maxPositions,
    UserConfig.PositionSizingMethod positionSizingMethod,
    @DecimalMin("1000") BigDecimal positionSizingValue,
    @Min(1) @Max(30) Integer orderExpiryDays,
    String telegramChatId,
    String zerodhaApiKey,
    String zerodhaApiSecret
) {}
```

```java
// backend/src/main/java/com/trading/users/dto/UserResponse.java
package com.trading.users.dto;
public record UserResponse(Long id, String name, String email, String role, Boolean active) {}
```

```java
// backend/src/main/java/com/trading/users/dto/UserConfigResponse.java
package com.trading.users.dto;
import java.math.BigDecimal;
public record UserConfigResponse(
    Integer maxPositions,
    String positionSizingMethod,
    BigDecimal positionSizingValue,
    Integer orderExpiryDays,
    String telegramChatId,
    Boolean zerodhaConnected,
    String zerodhaApiKey   // return key for display; NEVER return secret or token
) {}
```

- [ ] **Step 7: Create UserService.java**

```java
// backend/src/main/java/com/trading/users/UserService.java
package com.trading.users;

import com.trading.common.EncryptionUtil;
import com.trading.users.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserConfigRepository userConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already registered: " + req.email());
        }
        User user = User.builder()
                .name(req.name()).email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(req.role() != null ? req.role() : User.UserRole.USER)
                .active(true).build();
        user = userRepository.save(user);
        userConfigRepository.save(UserConfig.builder().user(user).build());
        return toResponse(user);
    }

    public UserResponse getUserByEmail(String email) {
        return toResponse(findByEmail(email));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void setUserActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setActive(active);
        userRepository.save(user);
    }

    public UserConfigResponse getConfigByEmail(String email) {
        UserConfig cfg = userConfigRepository.findByUser_Email(email)
                .orElseThrow(() -> new IllegalArgumentException("Config not found for: " + email));
        return toConfigResponse(cfg);
    }

    @Transactional
    public UserConfigResponse updateConfig(String email, UpdateConfigRequest req) {
        UserConfig cfg = userConfigRepository.findByUser_Email(email)
                .orElseThrow(() -> new IllegalArgumentException("Config not found for: " + email));
        if (req.maxPositions() != null) cfg.setMaxPositions(req.maxPositions());
        if (req.positionSizingMethod() != null) cfg.setPositionSizingMethod(req.positionSizingMethod());
        if (req.positionSizingValue() != null) cfg.setPositionSizingValue(req.positionSizingValue());
        if (req.orderExpiryDays() != null) cfg.setOrderExpiryDays(req.orderExpiryDays());
        if (req.telegramChatId() != null) cfg.setTelegramChatId(req.telegramChatId());
        if (req.zerodhaApiKey() != null) cfg.setZerodhaApiKey(req.zerodhaApiKey());
        if (req.zerodhaApiSecret() != null)
            cfg.setZerodhaApiSecret(encryptionUtil.encrypt(req.zerodhaApiSecret()));
        cfg.setUpdatedAt(LocalDateTime.now());
        return toConfigResponse(userConfigRepository.save(cfg));
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getActive());
    }

    private UserConfigResponse toConfigResponse(UserConfig cfg) {
        return new UserConfigResponse(
                cfg.getMaxPositions(), cfg.getPositionSizingMethod().name(),
                cfg.getPositionSizingValue(), cfg.getOrderExpiryDays(),
                cfg.getTelegramChatId(), cfg.getZerodhaConnected(), cfg.getZerodhaApiKey());
    }
}
```

- [ ] **Step 8: Run UserService tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=UserServiceTest -q
```

Expected: `BUILD SUCCESS`, 3 tests passed.

- [ ] **Step 9: Commit**

```bash
git add . && git commit -m "feat: users module — User, UserConfig entities, repositories, UserService"
```

---

### Task 5: Auth Module + Security Config

**Files:**
- Create: `backend/src/main/java/com/trading/auth/JwtUtil.java`
- Create: `backend/src/main/java/com/trading/auth/JwtFilter.java`
- Create: `backend/src/main/java/com/trading/auth/AuthService.java`
- Create: `backend/src/main/java/com/trading/auth/AuthController.java`
- Create: `backend/src/main/java/com/trading/auth/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/trading/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/trading/auth/AuthControllerTest.java`

**Interfaces:**
- Produces:
  - `POST /api/auth/login` → sets `jwt` httpOnly cookie, returns `ApiResponse<UserResponse>`
  - `DELETE /api/auth/logout` → clears `jwt` cookie, returns `ApiResponse<Void>`
  - `JwtUtil.generateToken(String email, String role)` → `String`
  - `JwtUtil.validateToken(String token)` → `Claims`

- [ ] **Step 1: Write the failing AuthController test**

```java
// backend/src/test/java/com/trading/auth/AuthControllerTest.java
package com.trading.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.auth.dto.LoginRequest;
import com.trading.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(com.trading.config.SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean JwtUtil jwtUtil;  // JwtFilter depends on this

    @Test
    void login_returns200WithUserOnSuccess() throws Exception {
        UserResponse user = new UserResponse(1L, "Alice", "alice@test.com", "USER", true);
        when(authService.login(any(), any())).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("alice@test.com", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alice@test.com"));
    }

    @Test
    void login_returns401OnBadCredentials() throws Exception {
        when(authService.login(any(), any())).thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("x@x.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd backend && ./mvnw test -Dtest=AuthControllerTest -q 2>&1 | tail -5
```

Expected: FAILED — `AuthController` does not exist.

- [ ] **Step 3: Create LoginRequest DTO**

```java
// backend/src/main/java/com/trading/auth/dto/LoginRequest.java
package com.trading.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
```

- [ ] **Step 4: Create JwtUtil.java**

```java
// backend/src/main/java/com/trading/auth/JwtUtil.java
package com.trading.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private static final long EXPIRY_MS = 24L * 60 * 60 * 1000;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
                .signWith(key)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 5: Create JwtFilter.java**

```java
// backend/src/main/java/com/trading/auth/JwtFilter.java
package com.trading.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        extractToken(req).ifPresent(token -> {
            try {
                Claims claims = jwtUtil.validateToken(token);
                String role = claims.get("role", String.class);
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) { /* invalid token — Spring Security denies access */ }
        });
        chain.doFilter(req, res);
    }

    private Optional<String> extractToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> "jwt".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
```

- [ ] **Step 6: Create SecurityConfig.java**

```java
// backend/src/main/java/com/trading/config/SecurityConfig.java
package com.trading.config;

import com.trading.auth.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;

    @Value("${cors.allowed-origin}") private String allowedOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

- [ ] **Step 7: Create AuthService.java**

```java
// backend/src/main/java/com/trading/auth/AuthService.java
package com.trading.auth;

import com.trading.auth.dto.LoginRequest;
import com.trading.users.User;
import com.trading.users.UserRepository;
import com.trading.users.dto.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserResponse login(LoginRequest req, HttpServletResponse response) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.getActive()) throw new BadCredentialsException("Account is deactivated");
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash()))
            throw new BadCredentialsException("Invalid credentials");

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true).secure(false).path("/")
                .maxAge(Duration.ofHours(24)).sameSite("Strict").build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), user.getActive());
    }

    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true).secure(false).path("/").maxAge(0).build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
```

- [ ] **Step 8: Create AuthController.java**

```java
// backend/src/main/java/com/trading/auth/AuthController.java
package com.trading.auth;

import com.trading.auth.dto.LoginRequest;
import com.trading.common.ApiResponse;
import com.trading.users.dto.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(
            @RequestBody @Valid LoginRequest req, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(req, response)));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

- [ ] **Step 9: Run AuthController tests**

```bash
cd backend && ./mvnw test -Dtest=AuthControllerTest -q
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 10: Boot the full backend and smoke-test login**

```bash
# Terminal 1
docker compose up postgres -d
cd backend && ./mvnw spring-boot:run

# Terminal 2 — first create an admin user directly in DB
docker compose exec postgres psql -U trading -c \
  "INSERT INTO users(name,email,password_hash,role) VALUES('Admin','admin@trading.com','\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','ADMIN');"
# (that BCrypt hash = "password")

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@trading.com","password":"password"}' -c cookies.txt -v
```

Expected: HTTP 200, `{"success":true,"data":{"email":"admin@trading.com",...}}`, `Set-Cookie: jwt=...` header.

- [ ] **Step 11: Commit**

```bash
git add . && git commit -m "feat: auth module — JWT, security config, login/logout endpoints"
```

---

### Task 6: User Config + Admin API

**Files:**
- Create: `backend/src/main/java/com/trading/users/UserController.java`
- Create: `backend/src/main/java/com/trading/users/AdminController.java`

**Interfaces:**
- Produces:
  - `GET /api/users/me` → `ApiResponse<UserResponse>`
  - `GET /api/users/me/config` → `ApiResponse<UserConfigResponse>`
  - `PUT /api/users/me/config` → `ApiResponse<UserConfigResponse>`
  - `GET /api/admin/users` → `ApiResponse<List<UserResponse>>`
  - `POST /api/admin/users` → `ApiResponse<UserResponse>` (201)
  - `PATCH /api/admin/users/{id}/status?active=true|false` → `ApiResponse<Void>`

- [ ] **Step 1: Create UserController.java**

```java
// backend/src/main/java/com/trading/users/UserController.java
package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.users.dto.UpdateConfigRequest;
import com.trading.users.dto.UserConfigResponse;
import com.trading.users.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByEmail(auth.getName())));
    }

    @GetMapping("/me/config")
    public ResponseEntity<ApiResponse<UserConfigResponse>> getMyConfig(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userService.getConfigByEmail(auth.getName())));
    }

    @PutMapping("/me/config")
    public ResponseEntity<ApiResponse<UserConfigResponse>> updateMyConfig(
            Authentication auth, @RequestBody @Valid UpdateConfigRequest req) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateConfig(auth.getName(), req)));
    }
}
```

- [ ] **Step 2: Create AdminController.java**

```java
// backend/src/main/java/com/trading/users/AdminController.java
package com.trading.users;

import com.trading.common.ApiResponse;
import com.trading.users.dto.CreateUserRequest;
import com.trading.users.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody @Valid CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(req)));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable Long id, @RequestParam boolean active) {
        userService.setUserActive(id, active);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

- [ ] **Step 3: Smoke-test the user endpoints**

```bash
# Assumes backend running and cookies.txt from Task 5 Step 10

curl http://localhost:8080/api/users/me -b cookies.txt
# Expected: {"success":true,"data":{"email":"admin@trading.com","role":"ADMIN",...}}

curl -X PUT http://localhost:8080/api/users/me/config \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"maxPositions":5,"positionSizingMethod":"FIXED","positionSizingValue":20000,"orderExpiryDays":7}'
# Expected: {"success":true,"data":{"maxPositions":5,...}}

curl -X POST http://localhost:8080/api/admin/users \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"name":"Bob","email":"bob@trading.com","password":"password123","role":"USER"}'
# Expected: HTTP 201, {"success":true,"data":{"name":"Bob",...}}
```

- [ ] **Step 4: Commit**

```bash
git add . && git commit -m "feat: user config API and admin user management endpoints"
```

---

### Task 7: React Frontend Scaffold + Login

**Files:**
- Create: all `frontend/` files listed in the File Structure above

**Interfaces:**
- Produces: running React app at `http://localhost:5173`; `/login` page that authenticates via `/api/auth/login`; protected routes redirect to `/login` when unauthenticated

- [ ] **Step 1: Scaffold Vite + React + TypeScript in frontend/**

```bash
cd D:\Zerodha_Breakout_stocks
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install react-router-dom @tanstack/react-query axios
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

- [ ] **Step 2: Configure Tailwind — update tailwind.config.js**

```js
// frontend/tailwind.config.js
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: { extend: {} },
  plugins: [],
}
```

- [ ] **Step 3: Add Tailwind directives to src/index.css**

```css
/* frontend/src/index.css */
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 4: Configure Vite proxy (dev only)**

```ts
// frontend/vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 5: Create Axios instance**

```ts
// frontend/src/lib/api.ts
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
```

- [ ] **Step 6: Create AuthContext**

```tsx
// frontend/src/contexts/AuthContext.tsx
import { createContext, useContext, ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import api from '../lib/api'

interface User { id: number; name: string; email: string; role: string; active: boolean }
interface AuthCtx { user: User | null; isLoading: boolean; logout: () => void }

const AuthContext = createContext<AuthCtx>({ user: null, isLoading: true, logout: () => {} })

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const { data: user, isLoading } = useQuery<User>({
    queryKey: ['me'],
    queryFn: () => api.get('/users/me').then(r => r.data.data),
    retry: false,
    staleTime: 5 * 60 * 1000,
  })

  const logout = async () => {
    await api.delete('/auth/logout').catch(() => {})
    queryClient.clear()
    navigate('/login')
  }

  return (
    <AuthContext.Provider value={{ user: user ?? null, isLoading, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
```

- [ ] **Step 7: Create ProtectedRoute**

```tsx
// frontend/src/components/ProtectedRoute.tsx
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

export function ProtectedRoute() {
  const { user, isLoading } = useAuth()
  if (isLoading) return (
    <div className="flex h-screen items-center justify-center text-gray-500">Loading...</div>
  )
  if (!user) return <Navigate to="/login" replace />
  return <Outlet />
}
```

- [ ] **Step 8: Create LoginPage**

```tsx
// frontend/src/pages/LoginPage.tsx
import { useState, FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../contexts/AuthContext'
import api from '../lib/api'

export function LoginPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  if (user) return <Navigate to="/" replace />

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    const form = new FormData(e.currentTarget)
    try {
      await api.post('/auth/login', {
        email: form.get('email'),
        password: form.get('password'),
      })
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      navigate('/')
    } catch {
      setError('Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-md">
        <h1 className="mb-2 text-2xl font-bold text-gray-900">Trading System</h1>
        <p className="mb-6 text-sm text-gray-500">Sign in to your account</p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">Email</label>
            <input name="email" type="email" required autoFocus
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm
                         focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Password</label>
            <input name="password" type="password" required
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm
                         focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500" />
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button type="submit" disabled={loading}
            className="w-full rounded-lg bg-blue-600 py-2 text-sm font-semibold text-white
                       hover:bg-blue-700 disabled:opacity-60">
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 9: Create DashboardPage placeholder**

```tsx
// frontend/src/pages/DashboardPage.tsx
import { useAuth } from '../contexts/AuthContext'

export function DashboardPage() {
  const { user, logout } = useAuth()
  return (
    <div className="min-h-screen bg-gray-50 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <button onClick={logout}
          className="rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100">
          Sign Out
        </button>
      </div>
      <p className="mt-4 text-gray-600">Welcome, {user?.name}. More features coming in Phase 6.</p>
    </div>
  )
}
```

- [ ] **Step 10: Wire App.tsx**

```tsx
// frontend/src/App.tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './contexts/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'

const queryClient = new QueryClient()

function AppRoutes() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
```

- [ ] **Step 11: Update main.tsx**

```tsx
// frontend/src/main.tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
```

- [ ] **Step 12: Run the frontend and verify end-to-end login**

```bash
cd frontend && npm run dev
```

Open `http://localhost:5173` — should redirect to `/login`.

Log in with `admin@trading.com` / `password` (the user seeded in Task 5).

Expected: redirected to `/dashboard`, "Welcome, Admin" displayed.

Log out — redirected back to `/login`.

- [ ] **Step 13: Commit**

```bash
git add . && git commit -m "feat: React frontend scaffold with login, protected routing, AuthContext"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Covered by |
|---|---|
| Java backend with user configurations | Task 4 + 6 |
| JWT auth | Task 5 |
| PostgreSQL with all 6 tables | Task 2 |
| AES-256 encrypted Zerodha secrets | Task 3 (EncryptionUtil) + Task 4 (UserService.updateConfig) |
| Role-based access (ADMIN/USER) | Task 5 (SecurityConfig) |
| Docker Compose deployment | Task 1 |
| React frontend with login | Task 7 |
| Flyway migrations | Task 2 |
| CORS configured | Task 5 (SecurityConfig) |
| Partial unique index for positions | Task 2 (V1 migration) |

**Gaps:** Signal module, Broker module, Portfolio Engine, Telegram, and full frontend pages are intentionally deferred to Plans 2–6.

**Placeholder scan:** No TBDs. All code blocks are complete and compilable.

**Type consistency:** `UserResponse`, `UserConfigResponse`, `LoginRequest` defined in Tasks 4–5 and consumed correctly in Tasks 6–7. `ApiResponse<T>` used consistently across all controllers.

**Ambiguity resolved:** `secure(false)` on JWT cookie — must be changed to `secure(true)` when deploying with HTTPS. Add a `server.ssl.enabled` or `COOKIE_SECURE=true` env var before production deployment.
