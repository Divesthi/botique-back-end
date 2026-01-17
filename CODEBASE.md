# BQOM (Boutique Order Management) Backend - Codebase Documentation

This document provides a comprehensive overview of the codebase for AI agents to quickly understand the project structure without re-analyzing the code.

## Project Overview

| Property | Value |
|----------|-------|
| Framework | Spring Boot 3.4.2 |
| Language | Java 17 |
| Build Tool | Apache Maven |
| Database | MySQL 8 |
| ORM | Hibernate/JPA |
| Migration | Liquibase |
| Architecture | Layered (Controller → Service → Repository → Entity) |

## Directory Structure

```
bqom-back-end/
├── src/main/java/com/dreamworks/bqom/
│   ├── BqomApplication.java              # Main entry point
│   ├── controller/                       # REST Controllers
│   │   ├── OrdersController.java         # /v1/bqom/orders
│   │   ├── CustomersController.java      # /v1/bqom/customers
│   │   ├── BillsController.java          # /v1/bqom/bills
│   │   └── TestController.java           # /v1/bqom (test endpoints)
│   ├── service/                          # Business Logic
│   │   ├── OrdersService.java
│   │   ├── CustomersService.java
│   │   ├── BillsService.java
│   │   └── StudentsService.java
│   ├── repository/                       # Data Access Layer
│   │   ├── OrdersRepository.java
│   │   ├── CustomersRepository.java
│   │   ├── BillRepository.java
│   │   ├── CustomerMeasurementRepository.java
│   │   ├── StudentsRepository.java
│   │   ├── entity/                       # JPA Entities
│   │   │   ├── base/
│   │   │   │   └── BaseEntity.java
│   │   │   ├── CustomerDetails.java
│   │   │   ├── OrderDetails.java
│   │   │   ├── OrderItemDetails.java
│   │   │   ├── OrderItemCost.java
│   │   │   ├── BillDetails.java
│   │   │   ├── BillOrdersAssociation.java
│   │   │   ├── CustomerMeasurementDetails.java
│   │   │   └── Students.java
│   │   └── enums/
│   │       ├── OrderStatus.java          # fresh, in_progress, completed, delivered
│   │       └── BillStatus.java           # fresh, closed, pending
│   └── model/                            # DTOs/Request-Response Models
│       ├── bill/
│       │   └── BillModel.java
│       ├── customer/
│       │   ├── CustomerDetailsModel.java
│       │   ├── CustomerMeasurementModel.java
│       │   └── MeasurementRequestBody.java
│       └── order/
│           ├── OrderModel.java
│           ├── OrderItemModel.java
│           └── OrderItemCostModel.java
└── src/main/resources/
    ├── application.properties            # App configuration
    └── db/changelog/                     # Liquibase migrations
        ├── db.changelog-master.xml
        └── 1.0.0/                        # SQL migration files
```

## Database Schema

### Entity Relationship Diagram

```
CustomerDetails (1) ─────────────── (Many) OrderDetails
       │                                      │
       │                                      ├─── (1:Many) OrderItemDetails
       │                                      │         │
       │                                      │         ├─── (1:Many) OrderItemCost
       │                                      │         └─── (Many:1) CustomerMeasurementDetails
       │                                      │
       │                                      └─── (1:Many) BillOrdersAssociation
       │
       ├──── (1:Many) CustomerMeasurementDetails
       │
       └──── (1:Many) BillDetails
                       │
                       └─── (1:Many) BillOrdersAssociation ──── (Many:1) OrderDetails
```

### Entities Summary

| Entity | Table | Primary Key | Foreign Keys | Purpose |
|--------|-------|-------------|--------------|---------|
| CustomerDetails | customer_details | id | - | Customer information |
| OrderDetails | order_details | id | mobile_no → CustomerDetails | Order tracking |
| OrderItemDetails | order_item_details | id | order_id → OrderDetails, mobile_no → CustomerDetails, measurement_id → CustomerMeasurementDetails | Individual order items |
| OrderItemCost | order_item_cost | id | item_id → OrderItemDetails, mobile_no → CustomerDetails | Cost breakdown per item |
| CustomerMeasurementDetails | customer_measurement_details | id | mobile_no → CustomerDetails | Body measurements (JSON) |
| BillDetails | bill_details | id | mobile_no → CustomerDetails | Billing records |
| BillOrdersAssociation | bill_orders_association | id | bill_id → BillDetails, order_id → OrderDetails | Bill-Order junction |

### Key Fields by Entity

**CustomerDetails:**
- `id`, `name`, `address`, `mobile_no` (UNIQUE), `alternate_contact_no`, `tenant_id`, `creation_date`

**OrderDetails:**
- `id`, `mobile_no`, `received_date`, `delivery_date`, `cutting_date`, `packaging_date`, `total_items`, `remarks`, `status`, `total`, `advance`, `balance`, `estimate_amount` (JSON)

**OrderItemDetails:**
- `id`, `order_id`, `mobile_no`, `measurement_id`, `remarks`, `cost_per_quantity`, `quantity`, `status`

**OrderItemCost:**
- `id`, `item_id`, `mobile_no`, `cost`, `type`, `remarks`

**CustomerMeasurementDetails:**
- `id`, `mobile_no`, `dress_type`, `measurement` (JSON), `remarks`, `name`, `creation_date`

**BillDetails:**
- `id`, `mobile_no`, `created_date`, `total_amount`, `advance_paid`, `balance_amount`, `status`, `discount`, `remarks`

## API Endpoints

### Orders API (`/v1/bqom/orders`)

| Method | Endpoint | Parameters | Description |
|--------|----------|------------|-------------|
| GET | `/` | `search`, `fromDate`, `toDate` (optional) | Get all orders with filtering |
| POST | `/` | `OrderModel` body | Create new order |
| PUT | `/` | `OrderModel` body | Update order and items |

### Customers API (`/v1/bqom/customers`)

| Method | Endpoint | Parameters | Description |
|--------|----------|------------|-------------|
| GET | `/` | `search` (optional) | Get all customers |
| GET | `/{contactNo}` | `contactNo` path | Get customer by mobile |
| POST | `/` | `CustomerDetailsModel` body | Create customer |
| PUT | `/` | `CustomerDetailsModel` body | Update customer |
| GET | `/measurements` | `search` (optional) | Get all measurements |
| GET | `/measurements/{contactNo}` | `contactNo` path | Get customer measurements |
| POST | `/measurements` | `CustomerMeasurementModel` body | Create measurement |
| PUT | `/measurements` | `CustomerMeasurementModel` body | Update measurement |

### Bills API (`/v1/bqom/bills`)

| Method | Endpoint | Parameters | Description |
|--------|----------|------------|-------------|
| GET | `/` | `search`, `fromDate`, `toDate` (optional) | Get all bills with filtering |
| POST | `/` | `BillModel` body | Create bill with orders |
| PUT | `/` | `BillModel` body | Update bill |

## Services Layer

### OrdersService
- `getOrders()` - Fetch all orders
- `searchOrders(searchTerm)` - Search by name/mobile/orderId
- `getOrdersByDateRange(fromDate, toDate)` - Date filtering
- `searchOrdersWithDateRange(searchTerm, fromDate, toDate)` - Combined search
- `createOrder(OrderModel)` - Creates order with cascading items/costs
- `updateOrder(OrderModel)` - Full update with item management

### CustomersService
- `getCustomers()` / `searchCustomers(searchTerm)` - Customer retrieval
- `getCustomer(contactNumber)` - Single customer lookup
- `createCustomer()` / `updateCustomer()` - CRUD operations
- `getMeasurements()` / `searchMeasurements()` - Measurement retrieval
- `getCustomerMeasurements(mobileNo)` - Customer-specific measurements
- `createCustomerMeasurement()` / `updateCustomerMeasurement()` - Measurement CRUD

### BillsService
- `getBills()` / `searchBills(searchTerm)` - Bill retrieval
- `getBillsByDateRange()` / `searchBillsWithDateRange()` - Date filtering
- `createBill(BillModel)` - Creates bill with order associations
- `updateBill(BillModel)` - Updates bill details

## Enums

### OrderStatus
- `fresh` - New order
- `in_progress` - Being worked on
- `completed` - Ready for delivery
- `delivered` - Order delivered

### BillStatus
- `fresh` - New bill
- `closed` - Paid/completed
- `pending` - Awaiting payment

## Configuration

### Database (application.properties)
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/bqom
spring.datasource.username=root
spring.datasource.password=Welcome123
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.ddl-auto=none  # Liquibase manages schema
```

### Liquibase Migrations
Located in: `src/main/resources/db/changelog/1.0.0/`
- `00-create-customer-details.sql`
- `01-create-order-details.sql`
- `02-create-customer-measurement-details.sql`
- `03-create-order-item-details.sql`
- `04-create-order-item-cost.sql`
- `05-create-bill-details.sql`
- `06-create-bill-orders-association.sql`
- `09-add-estimate-amount-to-orders.sql`

## Important Notes

### Security Status
- **No authentication/authorization implemented**
- CORS enabled for all origins (`@CrossOrigin(origins="*")`)
- Database credentials in plain text

### Validation Rules
- Customer measurement: `mobileNo + name + dressType` must be unique
- Measurement name cannot be changed after creation
- All orders must exist before creating a bill

### Transaction Handling
- Services use `@Transactional` for atomic operations
- Order updates: deletes old items/costs before inserting new ones
- Bill creation: validates all orders exist first

### Data Patterns
- `mobile_no` is the primary link between customer and related entities
- Measurements stored as JSON for flexibility
- `estimate_amount` in orders stored as JSON

## Common Development Tasks

### Adding a New Entity
1. Create entity in `repository/entity/`
2. Create repository interface extending `JpaRepository`
3. Create model/DTO in `model/`
4. Add service methods in `service/`
5. Add controller endpoints in `controller/`
6. Create Liquibase migration script

### Adding a New Endpoint
1. Add method to controller with appropriate mapping
2. Implement business logic in service
3. Add repository query if needed

### Database Changes
1. Create SQL file in `db/changelog/1.0.0/` (or new version folder)
2. Register in `db.changelog-master.xml`
3. Liquibase auto-applies on startup

## Build & Run

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Package
mvn package
```

Application runs on default port 8080.
