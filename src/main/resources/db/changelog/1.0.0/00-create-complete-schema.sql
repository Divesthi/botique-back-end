-- ============================================================
-- Complete BQOM schema with multi-tenancy support
-- ============================================================

-- Tenant table (must exist before any FK referencing it)
CREATE TABLE tenant (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    code         VARCHAR(25)  UNIQUE NOT NULL,
    address      VARCHAR(500),
    phone_number VARCHAR(25),
    started_date DATE,
    churned_date DATE,
    active       BOOLEAN DEFAULT TRUE
);

-- Customers: same mobile_no can belong to different tenants
CREATE TABLE customer_details (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    address              VARCHAR(500) NOT NULL,
    mobile_no            VARCHAR(25)  NOT NULL,
    alternate_contact_no VARCHAR(25),
    tenant_code          VARCHAR(25),
    creation_date        DATE,
    CONSTRAINT customer_details_mobile_no_tenant_code_key UNIQUE (mobile_no, tenant_code),
    FOREIGN KEY (tenant_code) REFERENCES tenant(code)
);

-- Orders
CREATE TABLE order_details (
    id             BIGSERIAL PRIMARY KEY,
    mobile_no      VARCHAR(25) NOT NULL,
    tenant_code    VARCHAR(25),
    received_date  DATE,
    delivery_date  DATE,
    cutting_date   DATE,
    packaging_date DATE,
    total_items    INT,
    remarks        VARCHAR(500),
    status         VARCHAR(20) CHECK (status IN ('fresh', 'in_progress', 'completed', 'delivered')),
    total          DECIMAL(10, 2),
    advance        DECIMAL(10, 2),
    balance        DECIMAL(10, 2),
    estimate_amount JSONB,
    FOREIGN KEY (tenant_code) REFERENCES tenant(code),
    FOREIGN KEY (mobile_no, tenant_code) REFERENCES customer_details(mobile_no, tenant_code)
);

-- Customer measurements
CREATE TABLE customer_measurement_details (
    id            BIGSERIAL PRIMARY KEY,
    mobile_no     VARCHAR(25)  NOT NULL,
    tenant_code   VARCHAR(25),
    dress_type    VARCHAR(50)  NOT NULL,
    measurement   JSONB        NOT NULL,
    remarks       VARCHAR(500),
    name          VARCHAR(100) NOT NULL,
    creation_date DATE         NOT NULL,
    FOREIGN KEY (tenant_code) REFERENCES tenant(code),
    FOREIGN KEY (mobile_no, tenant_code) REFERENCES customer_details(mobile_no, tenant_code)
);

-- Order items
CREATE TABLE order_item_details (
    id                BIGSERIAL PRIMARY KEY,
    mobile_no         VARCHAR(25) NOT NULL,
    tenant_code       VARCHAR(25),
    order_id          BIGINT      NOT NULL,
    measurement_id    BIGINT      NOT NULL,
    remarks           VARCHAR(500),
    cost_per_quantity DECIMAL(10, 2),
    quantity          INT,
    status            VARCHAR(20) CHECK (status IN ('new', 'in_progress', 'completed', 'delivered')) NOT NULL,
    FOREIGN KEY (tenant_code) REFERENCES tenant(code),
    FOREIGN KEY (mobile_no, tenant_code) REFERENCES customer_details(mobile_no, tenant_code),
    FOREIGN KEY (order_id) REFERENCES order_details(id),
    FOREIGN KEY (measurement_id) REFERENCES customer_measurement_details(id)
);

-- Order item costs
CREATE TABLE order_item_cost (
    id          BIGSERIAL PRIMARY KEY,
    item_id     BIGINT      NOT NULL,
    mobile_no   VARCHAR(25) NOT NULL,
    tenant_code VARCHAR(25),
    cost        DECIMAL(10, 2),
    type        VARCHAR(50),
    remarks     VARCHAR(500),
    FOREIGN KEY (tenant_code) REFERENCES tenant(code),
    FOREIGN KEY (mobile_no, tenant_code) REFERENCES customer_details(mobile_no, tenant_code),
    FOREIGN KEY (item_id) REFERENCES order_item_details(id)
);

-- Bills
CREATE TABLE bill_details (
    id             BIGSERIAL PRIMARY KEY,
    mobile_no      VARCHAR(25) NOT NULL,
    tenant_code    VARCHAR(25),
    created_date   DATE,
    total_amount   DECIMAL(10, 2),
    advance_paid   DECIMAL(10, 2),
    balance_amount DECIMAL(10, 2),
    status         VARCHAR(20) CHECK (status IN ('fresh', 'closed', 'pending')),
    discount       VARCHAR(50),
    remarks        VARCHAR(500),
    FOREIGN KEY (tenant_code) REFERENCES tenant(code),
    FOREIGN KEY (mobile_no, tenant_code) REFERENCES customer_details(mobile_no, tenant_code)
);

-- Bill-order associations
CREATE TABLE bill_orders_association (
    id          BIGSERIAL PRIMARY KEY,
    mobile_no   VARCHAR(25) NOT NULL,
    tenant_code VARCHAR(25),
    order_id    BIGINT      NOT NULL,
    bill_id     BIGINT      NOT NULL,
    CONSTRAINT BILL_ORDER_UC UNIQUE (order_id, bill_id),
    FOREIGN KEY (tenant_code) REFERENCES tenant(code),
    FOREIGN KEY (mobile_no, tenant_code) REFERENCES customer_details(mobile_no, tenant_code),
    FOREIGN KEY (order_id) REFERENCES order_details(id),
    FOREIGN KEY (bill_id) REFERENCES bill_details(id)
);
