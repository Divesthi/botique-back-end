# BQOM - Boutique Order Management System

A multi-tenant Spring Boot REST API backend for managing boutique operations including customer management, order processing, measurements tracking, and billing — with Supabase authentication and role-based access control.

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Programming Language |
| Spring Boot | 3.4.2 | Application Framework |
| Spring Security | - | Authentication & Authorization |
| PostgreSQL | 14+ | Database |
| Hibernate/JPA | - | ORM |
| Liquibase | - | Database Migration |
| JJWT | 0.12.6 | Supabase JWT Validation |
| Maven | - | Build Tool |
| Lombok | - | Boilerplate Reduction |

## Features

- **Multi-Tenancy** — Each tenant (customer/boutique) has isolated data via `tenant_code`
- **Supabase Authentication** — JWT-based authentication using Supabase Auth
- **Role-Based Access Control** — `TENANT_ADMIN` and `TENANT_USER` roles with DB-driven permissions
- **Customer Management** — Store and manage customer details with contact information
- **Measurements Tracking** — Flexible JSON-based body measurements for different dress types
- **Order Processing** — Track orders through workflow stages (fresh → in_progress → completed → delivered)
- **Item Management** — Manage individual items within orders with cost breakdowns
- **Billing** — Combine multiple orders into bills with payment tracking (advance, balance, discount)
- **Database Backup** — Automated and manual PostgreSQL backup/restore

## Prerequisites

- Java 17 or higher
- PostgreSQL 14+
- Maven 3.6+
- A [Supabase](https://supabase.com) project (for authentication)

## Setup

### 1. Database Setup

```sql
CREATE DATABASE bqom;
```

Update database credentials in `src/main/resources/application.properties` if needed:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/bqom
spring.datasource.username=postgres
spring.datasource.password=welcome123
```

Liquibase will automatically create all tables on first run.

### 2. Supabase Configuration

Get the following from your Supabase Dashboard → Settings → API:

| Value | Dashboard Location | Environment Variable |
|-------|-------------------|---------------------|
| Project URL | `URL` | `SUPABASE_URL` |
| JWT Secret | `JWT Secret` | `SUPABASE_JWT_SECRET` |
| Service Role Key | `service_role` (secret) | `SUPABASE_SERVICE_ROLE_KEY` |

```bash
# Linux/Mac
export SUPABASE_URL=https://your-project.supabase.co
export SUPABASE_JWT_SECRET=your-jwt-secret-here
export SUPABASE_SERVICE_ROLE_KEY=your-service-role-key-here

# Windows
set SUPABASE_URL=https://your-project.supabase.co
set SUPABASE_JWT_SECRET=your-jwt-secret-here
set SUPABASE_SERVICE_ROLE_KEY=your-service-role-key-here
```

> **Note:** The `service_role` key has admin privileges and should never be exposed to the frontend.

### 3. Seed the First Admin User

Create your first admin user in Supabase Auth (Dashboard → Authentication → Users → "Add user"), then link them in the BQOM database:

```sql
INSERT INTO tenant_users (supabase_uid, email, display_name, tenant_code, role)
VALUES ('your-supabase-user-uuid', 'admin@example.com', 'Admin', 'YOUR_TENANT_CODE', 'TENANT_ADMIN');
```

After this, the admin can register additional users via `POST /v1/bqom/auth/register` — this **automatically creates the user in Supabase** and sends them an invite email to set their password. No need to manually create users in Supabase anymore.

## Build & Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Authentication & Authorization

### How It Works

1. **Frontend** authenticates users via Supabase Auth (email/password, OAuth, etc.)
2. **Frontend** sends the Supabase JWT in the `Authorization: Bearer <token>` header
3. **Backend** validates the JWT, looks up the user in `tenant_users`, and enforces tenant isolation + role permissions

### Roles

| Role | Description |
|------|-------------|
| `PLATFORM_ADMIN` | Platform owner — can manage all tenants, onboard customers, access any tenant's data |
| `TENANT_ADMIN` | Full access within their tenant — can manage users, backup/restore, and all operations |
| `TENANT_USER` | Standard access — orders, customers, bills, measurements within their tenant |

Role hierarchy: `PLATFORM_ADMIN` > `TENANT_ADMIN` > `TENANT_USER`. Each higher role inherits all permissions of the lower roles.

### Permission Matrix

| Endpoint Group | PLATFORM_ADMIN | TENANT_ADMIN | TENANT_USER |
|----------------|:---:|:---:|:---:|
| Orders (CRUD) | ✅ (any tenant) | ✅ (own tenant) | ✅ (own tenant) |
| Customers (CRUD) | ✅ (any tenant) | ✅ (own tenant) | ✅ (own tenant) |
| Bills (CRUD) | ✅ (any tenant) | ✅ (own tenant) | ✅ (own tenant) |
| Measurements (CRUD) | ✅ (any tenant) | ✅ (own tenant) | ✅ (own tenant) |
| User Management | ✅ (any tenant) | ✅ (own tenant) | ❌ |
| Backup/Restore | ✅ | ✅ | ❌ |
| Tenant Create/Update | ✅ | ✅ | ❌ |
| Tenant View | ✅ | ✅ | ✅ |

### Tenant Isolation

Users can only access data belonging to their own tenant. The JWT filter automatically verifies that the `{tenantCode}` in the URL path matches the authenticated user's tenant. Cross-tenant access returns `403 Forbidden`.

## API Endpoints

> All tenant-scoped endpoints use the pattern: `/v1/bqom/tenants/{tenantCode}/...`

### Auth API

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/v1/bqom/auth/register` | TENANT_ADMIN | Register a new user for the tenant |
| GET | `/v1/bqom/auth/me` | Any authenticated | Get current user profile |
| GET | `/v1/bqom/auth/users` | TENANT_ADMIN | List all users for the tenant |
| PUT | `/v1/bqom/auth/users/{id}/role` | TENANT_ADMIN | Change a user's role |
| PUT | `/v1/bqom/auth/users/{id}/status` | TENANT_ADMIN | Activate/deactivate a user |

### Customers API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/tenants/{tenantCode}/customers` | Get all customers (optional: `?search=`) |
| GET | `/v1/bqom/tenants/{tenantCode}/customers/{contactNo}` | Get customer by mobile number |
| POST | `/v1/bqom/tenants/{tenantCode}/customers` | Create new customer |
| PUT | `/v1/bqom/tenants/{tenantCode}/customers` | Update customer |

### Customer Measurements API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/tenants/{tenantCode}/customers/measurements` | Get all measurements |
| GET | `/v1/bqom/tenants/{tenantCode}/customers/measurements/{contactNo}` | Get measurements by customer |
| POST | `/v1/bqom/tenants/{tenantCode}/customers/measurements` | Create measurement |
| PUT | `/v1/bqom/tenants/{tenantCode}/customers/measurements` | Update measurement |

### Orders API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/tenants/{tenantCode}/orders` | Get all orders (optional: `?search=&fromDate=&toDate=`) |
| POST | `/v1/bqom/tenants/{tenantCode}/orders` | Create new order |
| PUT | `/v1/bqom/tenants/{tenantCode}/orders` | Update order |

### Bills API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/tenants/{tenantCode}/bills` | Get all bills (optional: `?search=&fromDate=&toDate=`) |
| POST | `/v1/bqom/tenants/{tenantCode}/bills` | Create new bill |
| PUT | `/v1/bqom/tenants/{tenantCode}/bills` | Update bill |

### Tenants API

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/v1/bqom/tenants` | Any authenticated | List all tenants |
| GET | `/v1/bqom/tenants/{code}` | Any authenticated | Get tenant by code |
| POST | `/v1/bqom/tenants` | TENANT_ADMIN | Create new tenant |
| PUT | `/v1/bqom/tenants` | TENANT_ADMIN | Update tenant |

### Backup API (Admin Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/bqom/backup/create` | Trigger manual database backup |
| GET | `/v1/bqom/backup/list` | List available backups |
| POST | `/v1/bqom/backup/restore/{fileName}` | Restore from a backup file |
| GET | `/v1/bqom/backup/status` | Get backup status and configuration |

## Project Structure

```
src/main/java/com/dreamworks/bqom/
├── BqomApplication.java          # Main entry point
├── config/                       # Configuration
│   ├── SecurityConfig.java       # Spring Security (JWT filter, CORS, session)
│   └── WebMvcConfig.java         # MVC interceptor registration
├── security/                     # Security Layer
│   ├── AuthenticatedUser.java    # Authenticated user context
│   ├── RequireRole.java          # @RequireRole annotation
│   ├── RoleAuthorizationInterceptor.java  # Role enforcement
│   └── SupabaseJwtAuthenticationFilter.java  # JWT validation
├── controller/                   # REST Controllers
│   ├── AuthController.java       # Auth/user management
│   ├── OrdersController.java
│   ├── CustomersController.java
│   ├── BillsController.java
│   ├── TenantController.java
│   └── BackupController.java
├── service/                      # Business Logic
│   ├── AuthService.java
│   ├── OrdersService.java
│   ├── CustomersService.java
│   ├── BillsService.java
│   ├── TenantService.java
│   └── DatabaseBackupService.java
├── repository/                   # Data Access Layer
│   ├── entity/                   # JPA Entities
│   │   ├── TenantUser.java       # User-tenant-role mapping
│   │   └── ...
│   └── enums/
│       ├── UserRole.java         # TENANT_ADMIN, TENANT_USER
│       └── ...
└── model/                        # DTOs

src/main/resources/
├── application.properties        # Configuration
└── db/changelog/                 # Liquibase migrations
```

## Database Schema

### Core Entities

- **Tenant** — Boutique/customer organizations
- **TenantUsers** — User accounts with Supabase UID, tenant, and role
- **CustomerDetails** — Customer information (name, address, contact) scoped by tenant
- **CustomerMeasurementDetails** — Body measurements per dress type (JSON storage)
- **OrderDetails** — Order header with dates and totals
- **OrderItemDetails** — Individual items in an order
- **OrderItemCost** — Cost breakdown per item
- **BillDetails** — Billing information with payment tracking
- **BillOrdersAssociation** — Links bills to multiple orders

### Status Values

**Order Status:** `fresh`, `in_progress`, `completed`, `delivered`

**Bill Status:** `fresh`, `closed`, `pending`

**User Roles:** `TENANT_ADMIN`, `TENANT_USER`

## Configuration

Key configuration in `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/bqom
spring.jpa.hibernate.ddl-auto=none  # Schema managed by Liquibase

# Liquibase
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml

# Supabase
supabase.url=${SUPABASE_URL}
supabase.jwt.secret=${SUPABASE_JWT_SECRET}
supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY}

# Backup
backup.directory=./backups
backup.retention.days=30
```

## Development

### Adding Database Changes

1. Create SQL migration file in `src/main/resources/db/changelog/1.0.0/`
2. Liquibase applies changes automatically on startup (via `includeAll`)

### Running Tests

```bash
mvn test
```

### Building for Production

```bash
mvn clean package
SUPABASE_URL=https://your-project.supabase.co SUPABASE_JWT_SECRET=your-secret SUPABASE_SERVICE_ROLE_KEY=your-key java -jar target/bqom-0.0.1-SNAPSHOT.jar
```

## License

This project is proprietary software.