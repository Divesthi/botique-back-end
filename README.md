# BQOM - Boutique Order Management System

A Spring Boot REST API backend for managing boutique operations including customer management, order processing, measurements tracking, and billing.

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Programming Language |
| Spring Boot | 3.4.2 | Application Framework |
| MySQL | 8.x | Database |
| Hibernate/JPA | - | ORM |
| Liquibase | - | Database Migration |
| Maven | - | Build Tool |
| Lombok | - | Boilerplate Reduction |

## Features

- **Customer Management** - Store and manage customer details with contact information
- **Measurements Tracking** - Flexible JSON-based body measurements for different dress types
- **Order Processing** - Track orders through workflow stages (fresh → in_progress → completed → delivered)
- **Item Management** - Manage individual items within orders with cost breakdowns
- **Billing** - Combine multiple orders into bills with payment tracking (advance, balance, discount)

## Prerequisites

- Java 17 or higher
- MySQL 8.x
- Maven 3.6+

## Database Setup

1. Create a MySQL database:
```sql
CREATE DATABASE bqom;
```

2. Update database credentials in `src/main/resources/application.properties` if needed:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/bqom
spring.datasource.username=root
spring.datasource.password=Welcome123
```

Liquibase will automatically create all tables on first run.

## Build & Run

```bash
# Clone the repository
git clone <repository-url>
cd bqom-back-end

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## API Endpoints

### Customers API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/customers` | Get all customers (optional: `?search=`) |
| GET | `/v1/bqom/customers/{contactNo}` | Get customer by mobile number |
| POST | `/v1/bqom/customers` | Create new customer |
| PUT | `/v1/bqom/customers` | Update customer |

### Customer Measurements API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/customers/measurements` | Get all measurements |
| GET | `/v1/bqom/customers/measurements/{contactNo}` | Get measurements by customer |
| POST | `/v1/bqom/customers/measurements` | Create measurement |
| PUT | `/v1/bqom/customers/measurements` | Update measurement |

### Orders API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/orders` | Get all orders (optional: `?search=&fromDate=&toDate=`) |
| POST | `/v1/bqom/orders` | Create new order |
| PUT | `/v1/bqom/orders` | Update order |

### Bills API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/v1/bqom/bills` | Get all bills (optional: `?search=&fromDate=&toDate=`) |
| POST | `/v1/bqom/bills` | Create new bill |
| PUT | `/v1/bqom/bills` | Update bill |

## Project Structure

```
src/main/java/com/dreamworks/bqom/
├── BqomApplication.java          # Main entry point
├── controller/                   # REST Controllers
├── service/                      # Business Logic
├── repository/                   # Data Access Layer
│   ├── entity/                   # JPA Entities
│   └── enums/                    # Status Enums
└── model/                        # DTOs

src/main/resources/
├── application.properties        # Configuration
└── db/changelog/                 # Liquibase migrations
```

## Database Schema

### Core Entities

- **CustomerDetails** - Customer information (name, address, contact)
- **CustomerMeasurementDetails** - Body measurements per dress type (JSON storage)
- **OrderDetails** - Order header with dates and totals
- **OrderItemDetails** - Individual items in an order
- **OrderItemCost** - Cost breakdown per item
- **BillDetails** - Billing information with payment tracking
- **BillOrdersAssociation** - Links bills to multiple orders

### Status Values

**Order Status:** `fresh`, `in_progress`, `completed`, `delivered`

**Bill Status:** `fresh`, `closed`, `pending`

## Configuration

Key configuration in `application.properties`:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/bqom
spring.jpa.hibernate.ddl-auto=none  # Schema managed by Liquibase

# Liquibase
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml
```

## Development

### Adding Database Changes

1. Create SQL migration file in `src/main/resources/db/changelog/1.0.0/`
2. Register in `db.changelog-master.xml`
3. Liquibase applies changes automatically on startup

### Running Tests

```bash
mvn test
```

### Building for Production

```bash
mvn clean package
java -jar target/bqom-0.0.1-SNAPSHOT.jar
```

## License

This project is proprietary software.