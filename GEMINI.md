# BQOM (Boutique Order Management) Backend - Project Context

This file provides foundational context for AI agents to understand the
architecture, patterns, and conventions of the BQOM backend.

> ⚠️ **Correction:** Earlier versions of this file incorrectly stated MySQL 8.
> The runtime database is **PostgreSQL 14+**. All migrations use PostgreSQL
> syntax (`JSONB`, `BIGSERIAL`, `::jsonb` casts, `ON CONFLICT DO NOTHING`).

---

## 🚀 Project Overview

BQOM is a multi-tenant Boutique Order Management System. It allows boutiques to
manage customers, measurements, orders, and billing with strict data isolation
per boutique. Notifications are dispatched via a pluggable Strategy Pattern
(WhatsApp, Telegram — extensible to any channel without changing the dispatcher).

- **Primary Tech Stack:** Java 17, Spring Boot 3.4.2, **PostgreSQL 14+**, Liquibase.
- **Security:** Supabase Authentication (JWT via JWK endpoint) with Custom RBAC.
- **Multi-Tenancy:** Data isolation via `tenant_code` in URL paths and DB tables.
- **Notifications:** Async Strategy Pattern — `NotificationDispatcher` routes to
  `WhatsAppNotificationStrategy` or `TelegramNotificationStrategy` based on
  `tenant.preferences->>'notifications'->>'channel'`.
- **Credential Encryption:** AES-256-GCM via `EncryptionService` — credentials
  are encrypted at the service layer before DB persistence.

---

## 🏗 Architecture & Patterns

### 1. Layered Architecture
- **Controllers:** `/v1/bqom/tenants/{tenantCode}/...` — REST, tenant validation.
- **Services:** Business logic, `@Transactional`, `@Async` for notifications.
- **Repositories:** JPA with tenant-scoped query methods.
- **Entities:** Hibernate entities. Most extend `BaseEntity` (auto-increment PK).
- **Models:** DTOs used for API request/response. Never expose entities directly.

### 2. Multi-Tenancy
- All endpoints prefixed: `/v1/bqom/tenants/{tenantCode}`.
- Tenant isolation enforced by `SupabaseJwtAuthenticationFilter` (URL vs JWT
  tenant match) and `RoleAuthorizationInterceptor` (`@RequireRole`).
- Every major entity carries `tenant_code` or is linked to one that does.

### 3. Database & Migrations
- **Tool:** Liquibase — `includeAll` scans `1.0.0/` in filename order.
- **Location:** `src/main/resources/db/changelog/`.
- **New changes:** Add a new numbered SQL file in `1.0.0/` (e.g., `09-...sql`).
  No need to touch `db.changelog-master.xml`.
- **JSONB columns:** `measurement` (CustomerMeasurementDetails), `estimate_amount`
  (OrderDetails), `preferences` (Tenant) — all mapped with
  `@JdbcTypeCode(SqlTypes.JSON)` as `Map<String, Object>`.

### 4. Security & Roles
- **Auth Provider:** Supabase (JWK-based JWT validation via `NimbusJwtDecoder`).
- **Roles:** `PLATFORM_ADMIN` > `TENANT_ADMIN` > `TENANT_USER`.
- **RBAC:** `@RequireRole` annotation processed by `RoleAuthorizationInterceptor`.
- **Public endpoints:** `/actuator/**`, `/v1/bqom/health`.

### 5. Notification System (Strategy Pattern)
- `NotificationStrategy` interface: `channel()` + `send(NotificationMessage)`.
- `NotificationDispatcher` builds a `Map<NotificationChannel, NotificationStrategy>`
  from all Spring beans implementing the interface — **no if/else chains**.
- Dispatch is `@Async` (fire-and-forget). Failures are logged with `tenantCode`
  and message context — never silently swallowed.
- Credentials stored encrypted (`EncryptionService`, AES-256-GCM). Key from
  `BQOM_ENCRYPTION_KEY` env var (Base64-encoded 32 bytes). Fails fast at startup
  if the key is missing or wrong length.

### 6. Encryption
- `EncryptionService`: AES-256-GCM, random 12-byte IV per encryption call.
- Wire format: `Base64([12-byte IV][ciphertext + 16-byte GCM auth tag])`.
- Encrypt on write, decrypt on read — DB always stores ciphertext.

---

## 📂 Key Directory Structure

```
src/main/java/com/dreamworks/bqom/
├── config/              # JwtConfig, RestTemplateConfig, SecurityConfig, WebMvcConfig
├── controller/          # REST endpoints
├── model/               # DTOs / Request-Response models
│   └── notification/    # NotificationChannel, NotificationMessage, etc.
├── repository/          # JPA Repositories
│   └── entity/          # Hibernate entities (Tenant, TenantTelegramConfig, ...)
│       └── enums/       # OrderStatus, BillStatus, UserRole, NotificationChannel
├── security/            # JWT filters, RBAC, AuthenticatedUser context
└── service/             # Business logic layer
    └── notification/    # NotificationStrategy, Dispatcher, concrete strategies
```

---

## 🛠 Coding Standards & Conventions

- **Language:** Java 17.
- **Boilerplate:** Lombok (`@Data`, `@Builder`, `@Getter/@Setter`, etc.).
- **DI:** Field injection (`@Autowired`) — existing code style. Constructor
  injection preferred for new services (testability).
- **Naming:**
  - Entities: `XyzDetails.java` or `XyzConfig.java`.
  - Models: `XyzModel.java` or `XyzRequest.java`.
  - Controllers: `XyzController.java`.
- **Tenant isolation:** ALWAYS include `tenantCode` in repository queries.
- **Validation:** `@Valid` on `@RequestBody` + JSR-380 annotations on models.
- **JSONB mapping:** Use `@JdbcTypeCode(SqlTypes.JSON)` + `Map<String, Object>`.

---

## 🔄 Common Workflows

### Adding a New Feature
1. **DB:** New Liquibase file in `db/changelog/1.0.0/`.
2. **Entity:** JPA entity in `repository/entity/` (extend `BaseEntity` for PK).
3. **Repository:** Interface extending `JpaRepository`.
4. **Model:** DTOs in `model/`.
5. **Service:** Business logic — all queries tenant-scoped.
6. **Controller:** Endpoints under `/v1/bqom/tenants/{tenantCode}/`.

### Adding a New Notification Channel
1. Add constant to `NotificationChannel` enum.
2. Create `@Service` implementing `NotificationStrategy`.
3. Return new channel from `channel()`.
4. Create `tenant_<channel>_config` table (Liquibase).
5. `NotificationDispatcher` auto-registers — zero changes to dispatcher.

### Updating Tenant Preferences
```
PATCH /v1/bqom/tenants/{code}/preferences
Body: { "notifications": { "channel": "telegram" } }
```
Deep-merges into `tenant.preferences` JSONB. Existing keys not in the PATCH
body are preserved (not overwritten).

---

## ⚠️ Important Notes

- **Tenant Isolation:** ALWAYS include `tenantCode` in queries. Violation = 403.
- **Credentials:** Never store plaintext credentials in DB. Always encrypt via
  `EncryptionService`. Never log credential values.
- **CORS:** Wildcard (`*`) — tighten in production via env-specific config.
- **Async failures:** `@Async` notification failures are logged (not thrown to
  caller). Monitor logs for `[Telegram]` / `[WhatsApp]` ERROR entries.
- **Env vars:** All secrets via environment variables. See `.env.example`.
- **PostgreSQL:** Do NOT use MySQL-specific syntax in migrations or native queries.
