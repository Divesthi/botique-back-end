# BQOM (Boutique Order Management) Backend - Project Context

This file provides foundational context for Gemini to understand the architecture, patterns, and conventions of the BQOM backend.

## 🚀 Project Overview
BQOM is a multi-tenant Boutique Order Management System. It allows boutiques to manage customers, measurements, orders, and billing with data isolation per boutique.

- **Primary Tech Stack:** Java 17, Spring Boot 3.4.2, PostgreSQL, Liquibase.
- **Security:** Supabase Authentication (JWT) with Custom Role-Based Access Control (RBAC).
- **Multi-Tenancy:** Data isolation is achieved via a `tenant_code` present in URL paths and database tables.

## 🏗 Architecture & Patterns

### 1. Layered Architecture
- **Controllers:** `/v1/bqom/tenants/{tenantCode}/...` - Handles REST requests and tenant validation.
- **Services:** Contains business logic, `@Transactional` management, and tenant-scoped data access.
- **Repositories:** JPA repositories with tenant-aware query methods.
- **Entities:** Hibernate entities inheriting from `BaseEntity`.
- **Models:** DTOs (Request/Response) used for API communication.

### 2. Multi-Tenancy
- Most endpoints are prefixed with `/v1/bqom/tenants/{tenantCode}`.
- Tenant isolation is enforced via `SupabaseJwtAuthenticationFilter` and `RoleAuthorizationInterceptor`.
- Every major entity has a `tenant_code` or is linked to an entity that does.

### 3. Database & Migrations
- **Tool:** Liquibase.
- **Location:** `src/main/resources/db/changelog/`.
- **Convention:** New changes should be added as new files in `1.0.0/` (or current version) and included in `db.changelog-master.xml`.
- **JSON Storage:** Flexible data like measurements and cost breakdowns are stored as JSONB (PostgreSQL).

### 4. Security & Roles
- **Auth Provider:** Supabase.
- **Roles:** `PLATFORM_ADMIN`, `TENANT_ADMIN`, `TENANT_USER`.
- **RBAC:** Enforced via `@RequireRole` annotations and the `RoleAuthorizationInterceptor`.

## 📂 Key Directory Structure
```
src/main/java/com/dreamworks/bqom/
├── config/              # Security, MVC, and App configurations
├── controller/          # REST endpoints
├── model/               # DTOs / Request-Response models
├── repository/          # JPA Repositories
│   ├── entity/          # Hibernate entities
│   └── enums/           # OrderStatus, BillStatus, UserRole, etc.
├── security/            # JWT filters, RBAC logic, AuthenticatedUser context
└── service/             # Business logic layer
```

## 🛠 Coding Standards & Conventions
- **Language:** Java 17.
- **Boilerplate:** Use **Lombok** (`@Data`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, etc.).
- **Dependency Injection:** Current project uses field injection (`@Autowired` on fields), though constructor injection is preferred for new code.
- **Naming:**
    - Entities: `XyzDetails.java` (e.g., `OrderDetails`, `CustomerDetails`).
    - Models: `XyzModel.java` (e.g., `OrderModel`).
    - Controllers: `XyzController.java`.
- **Validation:** Use `@RequestBody` with model objects. Tenant code is always a path variable.

## 🔄 Common Workflows

### Adding a New Feature
1. **DB:** Create a new Liquibase migration file.
2. **Entity:** Add JPA entity in `repository/entity/` extending `BaseEntity`.
3. **Repository:** Create interface in `repository/`.
4. **Model:** Create DTOs in `model/`.
5. **Service:** Implement business logic, ensuring all queries are tenant-scoped.
6. **Controller:** Expose endpoints, usually under the tenant-scoped path.

### Handling Measurements
Measurements are dress-type specific and stored as JSON. When modifying measurements logic, refer to `CustomerMeasurementRepository` and `CustomerMeasurementDetails`.

## ⚠️ Important Notes
- **Tenant Isolation:** ALWAYS include `tenantCode` in repository queries to prevent cross-tenant data leakage.
- **CORS:** Currently configured to allow all origins in `SecurityConfig`.
- **Environment Variables:** Required for Supabase integration (`SUPABASE_URL`, `SUPABASE_JWT_SECRET`, `SUPABASE_SERVICE_ROLE_KEY`).

---
*Refer to `CODEBASE.md` for detailed ERDs and API documentation.*
