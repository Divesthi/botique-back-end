# BQOM (Boutique Order Management) Backend - Codebase Documentation

This document provides a comprehensive overview of the codebase for AI agents to quickly understand the project structure without re-analyzing the code.

## Project Overview

| Property | Value |
|----------|-------|
| Framework | Spring Boot 3.4.2 |
| Language | Java 17 |
| Build Tool | Apache Maven |
| Database | **PostgreSQL 14+** |
| ORM | Hibernate/JPA |
| Migration | Liquibase |
| Architecture | Layered (Controller → Service → Repository → Entity) |
| Auth | Supabase (JWT via JWK endpoint) |

> ⚠️ **Note:** Earlier versions of this document incorrectly stated MySQL 8.
> The runtime database is **PostgreSQL**. The driver, dialect, and all migration
> scripts use PostgreSQL syntax (`JSONB`, `BIGSERIAL`, `::jsonb` casts, etc.).

---

## Directory Structure

```
bqom-back-end/
├── src/main/java/com/dreamworks/bqom/
│   ├── BqomApplication.java
│   ├── config/
│   │   ├── JwtConfig.java
│   │   ├── RestTemplateConfig.java
│   │   ├── SecurityConfig.java
│   │   └── WebMvcConfig.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── BackupController.java
│   │   ├── BillsController.java
│   │   ├── CustomersController.java
│   │   ├── HealthController.java
│   │   ├── OrdersController.java
│   │   ├── TenantController.java        ← PATCH /tenants/{code}/preferences
│   │   └── TestController.java
│   ├── model/
│   │   ├── bill/BillModel.java
│   │   ├── customer/
│   │   │   ├── CustomerDetailsModel.java
│   │   │   ├── CustomerMeasurementModel.java
│   │   │   └── MeasurementRequestBody.java
│   │   ├── notification/               ← NEW
│   │   │   ├── NotificationChannel.java
│   │   │   ├── NotificationMessage.java
│   │   │   ├── TelegramConfigRequest.java
│   │   │   └── TenantPreferencesRequest.java
│   │   ├── order/
│   │   │   ├── OrderItemCostModel.java
│   │   │   ├── OrderItemModel.java
│   │   │   └── OrderModel.java
│   │   ├── TenantModel.java
│   │   └── TenantUserModel.java
│   ├── repository/
│   │   ├── BillRepository.java
│   │   ├── CustomerMeasurementRepository.java
│   │   ├── CustomersRepository.java
│   │   ├── OrdersRepository.java
│   │   ├── StudentsRepository.java
│   │   ├── TenantRepository.java
│   │   ├── TenantTelegramConfigRepository.java  ← NEW
│   │   ├── TenantUserRepository.java
│   │   └── entity/
│   │       ├── base/BaseEntity.java
│   │       ├── BillDetails.java
│   │       ├── BillOrdersAssociation.java
│   │       ├── CustomerDetails.java
│   │       ├── CustomerMeasurementDetails.java
│   │       ├── OrderDetails.java
│   │       ├── OrderItemCost.java
│   │       ├── OrderItemDetails.java
│   │       ├── Students.java
│   │       ├── Tenant.java                      ← UPDATED (preferences field)
│   │       ├── TenantTelegramConfig.java         ← NEW
│   │       └── TenantUser.java
│   ├── security/
│   │   ├── AuthenticatedUser.java
│   │   ├── RequireRole.java
│   │   ├── RoleAuthorizationInterceptor.java
│   │   └── SupabaseJwtAuthenticationFilter.java
│   └── service/
│       ├── AuthService.java
│       ├── BillsService.java
│       ├── CustomersService.java
│       ├── DatabaseBackupService.java
│       ├── EncryptionService.java               ← NEW (AES-256-GCM)
│       ├── OrdersService.java
│       ├── StudentsService.java
│       ├── SupabaseAdminClient.java
│       ├── TelegramConfigService.java           ← NEW
│       ├── TenantService.java                   ← UPDATED (preferences merge)
│       └── notification/
│           ├── NotificationDispatcher.java      ← NEW
│           ├── NotificationStrategy.java        ← NEW (interface)
│           ├── TelegramNotificationStrategy.java← NEW
│           └── WhatsAppNotificationStrategy.java← NEW
└── src/main/resources/
    ├── application.properties
    └── db/changelog/
        ├── db.changelog-master.xml
        └── 1.0.0/
            ├── 00-create-complete-schema.sql
            ├── 01-sample-data.sql
            ├── 02-create-tenant-users.sql
            ├── 03-add-platform-admin-role.sql
            ├── 04-add-phone-number-to-tenant-users.sql
            ├── 05-add-updated-date.sql
            ├── 06-add-delivered-date.sql
            ├── 07-fix-order-item-status-constraint.sql
            └── 08-notification-channel-config.sql  ← NEW
```

---

## Database Schema

### Runtime Database: PostgreSQL 14+

```
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

PostgreSQL-specific features in use:
- `JSONB` columns: `measurement`, `estimate_amount`, `preferences`
- `BIGSERIAL` for auto-increment PKs
- `::jsonb` cast operator in migration scripts
- `->>` and `->` JSON path operators in native queries
- `ON CONFLICT DO NOTHING` in seed data

### Entity Relationship Diagram

```
Tenant (1) ──────────────────────────── (1) TenantTelegramConfig   ← NEW
   │  └─ preferences JSONB              (encrypted bot_token, chat_id)
   │
   └── (via tenant_code FK on all tables below)
        │
        ├── CustomerDetails (1) ─── (Many) OrderDetails
        │         │                         │
        │         │                         ├── (1:Many) OrderItemDetails
        │         │                         │         └── (1:Many) OrderItemCost
        │         │                         └── (1:Many) BillOrdersAssociation
        │         │
        │         └── (1:Many) CustomerMeasurementDetails
        │
        ├── BillDetails (1) ─── (1:Many) BillOrdersAssociation
        │
        └── TenantUsers
```

### Entities Summary

| Entity | Table | Key Columns | Notes |
|--------|-------|-------------|-------|
| Tenant | tenant | id, code, preferences (JSONB) | `preferences` added in migration 08 |
| TenantTelegramConfig | tenant_telegram_config | tenant_code, bot_token, chat_id | Credentials AES-256-GCM encrypted |
| CustomerDetails | customer_details | id, mobile_no, tenant_code | PK for customer data |
| OrderDetails | order_details | id, mobile_no, tenant_code | estimate_amount is JSONB |
| OrderItemDetails | order_item_details | id, order_id, measurement_id | |
| OrderItemCost | order_item_cost | id, item_id | Cost breakdown per item |
| CustomerMeasurementDetails | customer_measurement_details | id, mobile_no | measurement is JSONB |
| BillDetails | bill_details | id, mobile_no | |
| BillOrdersAssociation | bill_orders_association | id, bill_id, order_id | Junction table |
| TenantUser | tenant_users | id, supabase_uid, tenant_code | RBAC |

---

## Notification System (NEW)

### Architecture: Strategy Pattern

```
NotificationDispatcher (@Async)
        │
        ├── reads tenant.preferences->>'notifications'->>'channel'
        │
        ├── Map<NotificationChannel, NotificationStrategy> (Spring bean registry)
        │
        ├── WhatsAppNotificationStrategy  (channel = WHATSAPP)
        │       └── reads tenant_whatsapp_config, decrypts via EncryptionService
        │
        └── TelegramNotificationStrategy  (channel = TELEGRAM)
                └── reads tenant_telegram_config, decrypts via EncryptionService
```

### Credential Storage

- Credentials (`bot_token`, `chat_id`, `api_key`) are **never** stored in plaintext.
- AES-256-GCM encryption/decryption happens in `EncryptionService` at the **service layer**.
- The DB column type is `TEXT` (encrypted blob), not `JSONB`.
- Encryption key sourced from `BQOM_ENCRYPTION_KEY` env var (Base64-encoded 32 bytes).

### Adding a New Channel

1. Add constant to `NotificationChannel` enum.
2. Create `@Service` implementing `NotificationStrategy`.
3. Return the new channel from `channel()`.
4. `NotificationDispatcher` auto-registers it — **zero changes to dispatcher**.

---

## API Endpoints

### Tenant Preferences API (NEW)

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| PATCH | `/v1/bqom/tenants/{code}/preferences` | TENANT_ADMIN | Deep-merge notification preferences |

**Request body:**
```json
{
  "notifications": {
    "channel": "telegram"
  }
}
```

**Behaviour:** Deep-merges into existing `preferences` JSONB — does not overwrite
the entire column. Unknown keys are preserved.

### Telegram Config API (NEW)

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/v1/bqom/tenants/{code}/telegram-config` | TENANT_ADMIN | Create/update Telegram config |

---

## Configuration

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SPRING_DATASOURCE_URL` | Yes | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | DB username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | DB password |
| `SUPABASE_URL` | Yes | Supabase project URL |
| `SUPABASE_JWT_SECRET` | Yes | JWT signing secret |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes | Admin API key |
| `SUPABASE_ANON_KEY` | Yes | Public anon key |
| `BQOM_ENCRYPTION_KEY` | Yes | Base64-encoded 32-byte AES-256 key |
| `BQOM_TELEGRAM_API_URL` | No | Override Telegram Bot API base URL |
| `BQOM_WHATSAPP_API_URL` | No | Override WhatsApp Cloud API base URL |

### Key application.properties Settings

```properties
# PostgreSQL
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=none   # Liquibase manages schema

# Encryption
bqom.encryption.key=${BQOM_ENCRYPTION_KEY}

# Async
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=16
spring.task.execution.pool.queue-capacity=100

# Notification
bqom.telegram.api-url=https://api.telegram.org
bqom.whatsapp.api-url=https://graph.facebook.com/v18.0
```

---

## Enums

### OrderStatus
`fresh` | `in_progress` | `completed` | `delivered`

### BillStatus
`fresh` | `closed` | `pending`

### UserRole
`PLATFORM_ADMIN` | `TENANT_ADMIN` | `TENANT_USER`

### NotificationChannel (NEW)
`whatsapp` | `telegram`

---

## Build & Run

```bash
mvn clean install
mvn spring-boot:run
```

Application runs on port `8080` by default.
