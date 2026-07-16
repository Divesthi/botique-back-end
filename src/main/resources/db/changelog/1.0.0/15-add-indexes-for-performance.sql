CREATE INDEX IF NOT EXISTS idx_cmd_tenant_code
    ON customer_measurement_details(tenant_code);

CREATE INDEX IF NOT EXISTS idx_cmd_mobile_tenant
    ON customer_measurement_details(mobile_no, tenant_code);

CREATE INDEX IF NOT EXISTS idx_boa_bill_id ON bill_orders_association(bill_id);
CREATE INDEX IF NOT EXISTS idx_boa_order_id ON bill_orders_association(order_id);
CREATE INDEX IF NOT EXISTS idx_boa_tenant_code ON bill_orders_association(tenant_code);

CREATE INDEX IF NOT EXISTS idx_bill_details_tenant ON bill_details(tenant_code);
CREATE INDEX IF NOT EXISTS idx_oid_order_id ON order_item_details(order_id);
CREATE INDEX IF NOT EXISTS idx_oid_measurement_id ON order_item_details(measurement_id);
CREATE INDEX IF NOT EXISTS idx_oic_item_id ON order_item_cost(item_id);