-- ─────────────────────────────────────────────────────────────────────────────
-- 14-add-performance-indexes.sql
--
-- Phase 1 performance indexes.
--
-- Root cause addressed: nearly every child table joins back to
-- customer_details via (mobile_no, tenant_code) rather than a simple FK id
-- (see CustomerMeasurementDetails, OrderDetails, BillDetails,
-- BillOrdersAssociation @OneToOne @JoinColumns). Only customer_details itself
-- had an index covering that pair (its own UNIQUE constraint). Every
-- "list all X for tenant" call — the primary page-load query for every
-- feature — was sequential-scanning these join columns.
--
-- Column ordering rationale:
--   • (mobile_no, tenant_code) — both used as equality predicates in joins;
--     order matches the FK/unique-constraint order already used elsewhere
--     in the schema for consistency.
--   • (tenant_code, date_column) — tenant_code is always an equality filter,
--     the date is a range filter. Equality column leads so Postgres can
--     narrow to the tenant's rows first, then range-scan the date within
--     that narrowed set.
--
-- Safe to run as plain CREATE INDEX now (seed-data-scale tables). Once in
-- production with real tenant volume, switch to CREATE INDEX CONCURRENTLY
-- (run outside a transaction / outside Liquibase's default changeset wrapper)
-- to avoid blocking writes during index build.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── customer_details ────────────────────────────────────────────────────────
-- Powers: findAllByTenantCode, searchCustomers (tenant filter half of the OR)
CREATE INDEX IF NOT EXISTS idx_customer_details_tenant_code
    ON customer_details(tenant_code);

-- ── customer_measurement_details ────────────────────────────────────────────
-- Powers: findAllByTenantCode (via customerDetails join), getMeasurementByMobileNo*,
-- searchMeasurements, and the join OrdersService performs when resolving
-- measurement -> customer during order creation/update.
CREATE INDEX IF NOT EXISTS idx_cust_measurement_mobile_tenant
    ON customer_measurement_details(mobile_no, tenant_code);

-- ── order_details ────────────────────────────────────────────────────────────
-- Powers: getOrders / searchOrders (via customerDetails join), getOrdersByMobileNumber
CREATE INDEX IF NOT EXISTS idx_order_details_mobile_tenant
    ON order_details(mobile_no, tenant_code);

-- Powers: getOrdersByDateRange, searchOrdersWithDateRange (delivery_date range scan)
CREATE INDEX IF NOT EXISTS idx_order_details_tenant_delivery_date
    ON order_details(tenant_code, delivery_date);

-- ── order_item_details ──────────────────────────────────────────────────────
-- Powers: getOrderItemsByOrderId — executed for every order fetched (n+1-style
-- lazy load pattern), and the bulk delete in deleteOrder/updateOrder
CREATE INDEX IF NOT EXISTS idx_order_item_details_order_id
    ON order_item_details(order_id);

-- Powers: the OneToOne join to customer_measurement_details when building
-- OrderItemModel.measurementId in the response
CREATE INDEX IF NOT EXISTS idx_order_item_details_measurement_id
    ON order_item_details(measurement_id);

-- Powers: tenant-scoped item lookups (defense in depth for tenant isolation queries)
CREATE INDEX IF NOT EXISTS idx_order_item_details_mobile_tenant
    ON order_item_details(mobile_no, tenant_code);

-- ── order_item_cost ──────────────────────────────────────────────────────────
-- Powers: getOrderItemCostByItemId — executed per item, per order fetched
CREATE INDEX IF NOT EXISTS idx_order_item_cost_item_id
    ON order_item_cost(item_id);

CREATE INDEX IF NOT EXISTS idx_order_item_cost_mobile_tenant
    ON order_item_cost(mobile_no, tenant_code);

-- ── bill_details ─────────────────────────────────────────────────────────────
-- Powers: getBills / searchBills (via customerDetails join), getBillsByMobileNumber
CREATE INDEX IF NOT EXISTS idx_bill_details_mobile_tenant
    ON bill_details(mobile_no, tenant_code);

-- Powers: getBillsByDateRange, searchBillsWithDateRange (created_date range scan)
CREATE INDEX IF NOT EXISTS idx_bill_details_tenant_created_date
    ON bill_details(tenant_code, created_date);

-- ── bill_orders_association ─────────────────────────────────────────────────
-- Powers: getBillOrdersAssociationsByBillId — the existing UNIQUE(order_id, bill_id)
-- does NOT help here since order_id is the leading column; every deleteBill
-- and multi-order bill fetch needs a bill_id-first index.
CREATE INDEX IF NOT EXISTS idx_bill_orders_assoc_bill_id
    ON bill_orders_association(bill_id);

CREATE INDEX IF NOT EXISTS idx_bill_orders_assoc_mobile_tenant
    ON bill_orders_association(mobile_no, tenant_code);

-- ── tenant_users ─────────────────────────────────────────────────────────────
-- Powers: findByTenantCode — admin's user-list page (AuthService.getUsers)
CREATE INDEX IF NOT EXISTS idx_tenant_users_tenant_code
    ON tenant_users(tenant_code);