-- ============================================================
-- Sample data for BQOM multi-tenancy testing
-- Idempotent: ON CONFLICT DO NOTHING on every insert
-- 2 tenants | 6 customers | 11 measurements
-- 14 orders (all statuses, varied delivery dates)
-- 18 order items | 41 order item costs
-- 9 bills (multi-order, partial-pay, discount scenarios)
-- 10 bill-order associations
-- ============================================================

-- ── Tenants ──────────────────────────────────────────────────
INSERT INTO tenant (id, name, code, address, phone_number, started_date, active) VALUES
(1, 'Silk & Style',    'BOUTIQUE_A', '123 MG Road, Bangalore, Karnataka',  '9876500001', '2020-01-15', true),
(2, 'Thread & Needle', 'BOUTIQUE_B', '45 Anna Salai, Chennai, Tamil Nadu', '9876500002', '2021-06-10', true)
ON CONFLICT DO NOTHING;

SELECT setval('tenant_id_seq', (SELECT MAX(id) FROM tenant));

-- ── Customers ─────────────────────────────────────────────────
-- BOUTIQUE_A
INSERT INTO customer_details (id, name, address, mobile_no, alternate_contact_no, tenant_code, creation_date) VALUES
(1, 'Priya Sharma', '12 Rose Garden, Indiranagar, Bangalore', '9876543210', '9876543211', 'BOUTIQUE_A', '2024-01-10'),
(2, 'Anita Kumari', '34 Koramangala, Bangalore',              '9123456789', NULL,          'BOUTIQUE_A', '2024-02-15'),
(3, 'Deepa Menon',  '78 Whitefield, Bangalore',               '9500001234', '9500001235', 'BOUTIQUE_A', '2024-03-20')
ON CONFLICT DO NOTHING;

-- BOUTIQUE_B  (9876543210 intentionally reused — same person, different boutique)
INSERT INTO customer_details (id, name, address, mobile_no, alternate_contact_no, tenant_code, creation_date) VALUES
(4, 'Priya Sharma',  '5 T Nagar, Chennai',   '9876543210', NULL,          'BOUTIQUE_B', '2024-04-01'),
(5, 'Meena Nair',    '22 Adyar, Chennai',     '9988776655', '9988776656', 'BOUTIQUE_B', '2024-04-10'),
(6, 'Kavitha Rajan', '10 Velachery, Chennai', '9345678901', NULL,          'BOUTIQUE_B', '2024-05-05')
ON CONFLICT DO NOTHING;

SELECT setval('customer_details_id_seq', (SELECT MAX(id) FROM customer_details));

-- ── Customer Measurements ─────────────────────────────────────
-- Priya @ BOUTIQUE_A
INSERT INTO customer_measurement_details
    (id, mobile_no, tenant_code, dress_type, name, measurement, remarks, creation_date)
VALUES
(1, '9876543210', 'BOUTIQUE_A', 'Blouse', 'Priya - Blouse',
    '{"chest":36,"waist":30,"shoulder":13,"sleeve_length":6,"back_length":15,"front_length":14}'::jsonb,
    'Regular fit', '2024-01-15'),
(2, '9876543210', 'BOUTIQUE_A', 'Salwar', 'Priya - Salwar',
    '{"chest":38,"waist":32,"hip":40,"shoulder":14,"kurta_length":42,"salwar_length":38}'::jsonb,
    'Straight cut', '2024-01-15')
ON CONFLICT DO NOTHING;

-- Anita @ BOUTIQUE_A
INSERT INTO customer_measurement_details
    (id, mobile_no, tenant_code, dress_type, name, measurement, remarks, creation_date)
VALUES
(3, '9123456789', 'BOUTIQUE_A', 'Blouse', 'Anita - Blouse',
    '{"chest":34,"waist":28,"shoulder":12,"sleeve_length":7,"back_length":14,"front_length":13}'::jsonb,
    'Slim fit', '2024-02-20'),
(4, '9123456789', 'BOUTIQUE_A', 'Lehenga', 'Anita - Lehenga',
    '{"waist":28,"hip":38,"lehenga_length":42,"blouse_chest":34,"blouse_length":14}'::jsonb,
    'Flared style', '2024-02-20')
ON CONFLICT DO NOTHING;

-- Deepa @ BOUTIQUE_A
INSERT INTO customer_measurement_details
    (id, mobile_no, tenant_code, dress_type, name, measurement, remarks, creation_date)
VALUES
(5, '9500001234', 'BOUTIQUE_A', 'Salwar', 'Deepa - Salwar',
    '{"chest":40,"waist":34,"hip":42,"shoulder":15,"kurta_length":44,"salwar_length":40}'::jsonb,
    NULL, '2024-03-22'),
(11, '9500001234', 'BOUTIQUE_A', 'Blouse', 'Deepa - Blouse',
    '{"chest":40,"waist":34,"shoulder":15,"sleeve_length":7,"back_length":16,"front_length":15}'::jsonb,
    NULL, '2024-03-22')
ON CONFLICT DO NOTHING;

-- Priya @ BOUTIQUE_B
INSERT INTO customer_measurement_details
    (id, mobile_no, tenant_code, dress_type, name, measurement, remarks, creation_date)
VALUES
(6, '9876543210', 'BOUTIQUE_B', 'Blouse', 'Priya - Blouse',
    '{"chest":36,"waist":30,"shoulder":13,"sleeve_length":5,"back_length":15,"front_length":14}'::jsonb,
    'Short sleeve', '2024-04-05')
ON CONFLICT DO NOTHING;

-- Meena @ BOUTIQUE_B
INSERT INTO customer_measurement_details
    (id, mobile_no, tenant_code, dress_type, name, measurement, remarks, creation_date)
VALUES
(7, '9988776655', 'BOUTIQUE_B', 'Salwar', 'Meena - Salwar',
    '{"chest":38,"waist":32,"hip":40,"shoulder":14,"kurta_length":43,"salwar_length":39}'::jsonb,
    NULL, '2024-04-12'),
(8, '9988776655', 'BOUTIQUE_B', 'Blouse', 'Meena - Blouse',
    '{"chest":38,"waist":32,"shoulder":14,"sleeve_length":6,"back_length":15,"front_length":14}'::jsonb,
    NULL, '2024-04-12')
ON CONFLICT DO NOTHING;

-- Kavitha @ BOUTIQUE_B
INSERT INTO customer_measurement_details
    (id, mobile_no, tenant_code, dress_type, name, measurement, remarks, creation_date)
VALUES
(9,  '9345678901', 'BOUTIQUE_B', 'Blouse', 'Kavitha - Blouse',
    '{"chest":35,"waist":29,"shoulder":12,"sleeve_length":6,"back_length":14,"front_length":13}'::jsonb,
    NULL, '2024-05-10'),
(10, '9345678901', 'BOUTIQUE_B', 'Salwar', 'Kavitha - Salwar',
    '{"chest":37,"waist":31,"hip":39,"shoulder":13,"kurta_length":43,"salwar_length":39}'::jsonb,
    NULL, '2024-05-10')
ON CONFLICT DO NOTHING;

SELECT setval('customer_measurement_details_id_seq', (SELECT MAX(id) FROM customer_measurement_details));

-- ── Orders ────────────────────────────────────────────────────
-- BOUTIQUE_A orders
INSERT INTO order_details
    (id, mobile_no, tenant_code, received_date, delivery_date, cutting_date, packaging_date,
     total_items, remarks, status, total, advance, balance, estimate_amount)
VALUES
(1,  '9876543210', 'BOUTIQUE_A', '2024-06-01', '2024-06-20', '2024-06-05', NULL,
    2, 'Urgent — wedding on June 20',
    'in_progress', 2500.00, 1000.00, 1500.00,
    '{"fabric":1200,"stitching":800,"embroidery":500}'::jsonb),

(2,  '9123456789', 'BOUTIQUE_A', '2024-06-10', '2024-06-30', '2024-06-15', '2024-06-28',
    1, 'Festival wear — Onam',
    'completed', 1800.00, 1800.00, 0.00,
    '{"fabric":1000,"stitching":600,"embroidery":200}'::jsonb),

(3,  '9500001234', 'BOUTIQUE_A', '2024-07-01', '2024-07-15', NULL, NULL,
    1, NULL,
    'fresh', 1200.00, 500.00, 700.00,
    '{"fabric":700,"stitching":500}'::jsonb),

(6,  '9876543210', 'BOUTIQUE_A', '2024-04-01', '2024-04-25', '2024-04-05', '2024-04-23',
    1, 'Summer blouse',
    'delivered', 800.00, 800.00, 0.00,
    '{"fabric":500,"stitching":300}'::jsonb),

(7,  '9123456789', 'BOUTIQUE_A', '2024-03-10', '2024-04-05', '2024-03-15', '2024-04-03',
    1, 'Casual blouse',
    'delivered', 650.00, 650.00, 0.00,
    '{"fabric":400,"stitching":250}'::jsonb),

(8,  '9500001234', 'BOUTIQUE_A', '2024-07-05', '2024-07-31', '2024-07-10', NULL,
    2, 'Office wear set',
    'in_progress', 2150.00, 1000.00, 1150.00,
    '{"fabric":1300,"stitching":750,"other":100}'::jsonb),

(9,  '9876543210', 'BOUTIQUE_A', '2024-07-15', '2024-08-20', NULL, NULL,
    1, 'Churidar for college event',
    'fresh', 870.00, 300.00, 570.00,
    '{"fabric":550,"stitching":320}'::jsonb),

(10, '9123456789', 'BOUTIQUE_A', '2024-07-01', '2024-07-25', '2024-07-05', '2024-07-22',
    2, 'Party wear — Lehenga + Blouse',
    'completed', 2360.00, 1500.00, 860.00,
    '{"fabric":1300,"stitching":760,"embroidery":300}'::jsonb)

ON CONFLICT DO NOTHING;

-- BOUTIQUE_B orders
INSERT INTO order_details
    (id, mobile_no, tenant_code, received_date, delivery_date, cutting_date, packaging_date,
     total_items, remarks, status, total, advance, balance, estimate_amount)
VALUES
(4,  '9876543210', 'BOUTIQUE_B', '2024-06-05', '2024-06-25', '2024-06-08', NULL,
    1, 'Saree blouse for reception',
    'in_progress', 900.00, 500.00, 400.00,
    '{"fabric":400,"stitching":500}'::jsonb),

(5,  '9988776655', 'BOUTIQUE_B', '2024-06-15', '2024-07-05', '2024-06-18', '2024-07-03',
    2, 'Party wear set',
    'delivered', 3200.00, 3200.00, 0.00,
    '{"fabric":1800,"stitching":900,"embroidery":500}'::jsonb),

(11, '9345678901', 'BOUTIQUE_B', '2024-07-10', '2024-08-05', NULL, NULL,
    1, NULL,
    'fresh', 730.00, 0.00, 730.00,
    '{"fabric":450,"stitching":280}'::jsonb),

(12, '9988776655', 'BOUTIQUE_B', '2024-07-01', '2024-07-20', '2024-07-04', NULL,
    1, 'Office salwar',
    'in_progress', 1250.00, 500.00, 750.00,
    '{"fabric":800,"stitching":450}'::jsonb),

(13, '9876543210', 'BOUTIQUE_B', '2024-04-10', '2024-05-05', '2024-04-14', '2024-05-03',
    1, 'Silk blouse',
    'delivered', 750.00, 750.00, 0.00,
    '{"fabric":450,"stitching":300}'::jsonb),

(14, '9345678901', 'BOUTIQUE_B', '2024-06-01', '2024-06-30', '2024-06-05', '2024-06-28',
    1, 'Printed cotton salwar',
    'delivered', 1080.00, 1080.00, 0.00,
    '{"fabric":700,"stitching":380}'::jsonb)

ON CONFLICT DO NOTHING;

SELECT setval('order_details_id_seq', (SELECT MAX(id) FROM order_details));

-- ── Order Items ────────────────────────────────────────────────
INSERT INTO order_item_details
    (id, mobile_no, tenant_code, order_id, measurement_id, remarks, cost_per_quantity, quantity, status)
VALUES
(1,  '9876543210', 'BOUTIQUE_A', 1,  1,  'Silk blouse',            800.00, 1, 'in_progress'),
(2,  '9876543210', 'BOUTIQUE_A', 1,  2,  'Churidar set',          1200.00, 1, 'in_progress'),
(3,  '9123456789', 'BOUTIQUE_A', 2,  4,  'Lehenga',               1800.00, 1, 'completed'),
(4,  '9500001234', 'BOUTIQUE_A', 3,  5,  'Cotton salwar',         1200.00, 1, 'in_progress'),
(5,  '9876543210', 'BOUTIQUE_B', 4,  6,  'Saree blouse',           900.00, 1, 'in_progress'),
(6,  '9988776655', 'BOUTIQUE_B', 5,  7,  'Salwar set',            1800.00, 1, 'delivered'),
(7,  '9988776655', 'BOUTIQUE_B', 5,  8,  'Blouse',                 900.00, 1, 'delivered'),
(8,  '9876543210', 'BOUTIQUE_A', 6,  1,  'Summer blouse',          800.00, 1, 'delivered'),
(9,  '9123456789', 'BOUTIQUE_A', 7,  3,  'Casual blouse',          650.00, 1, 'delivered'),
(10, '9500001234', 'BOUTIQUE_A', 8,  11, 'Office blouse',          950.00, 1, 'in_progress'),
(11, '9500001234', 'BOUTIQUE_A', 8,  5,  'Office salwar',         1200.00, 1, 'in_progress'),
(12, '9876543210', 'BOUTIQUE_A', 9,  2,  'Churidar',               870.00, 1, 'in_progress'),
(13, '9123456789', 'BOUTIQUE_A', 10, 3,  'Party blouse',           660.00, 1, 'completed'),
(14, '9123456789', 'BOUTIQUE_A', 10, 4,  'Flared lehenga',        1700.00, 1, 'completed'),
(15, '9345678901', 'BOUTIQUE_B', 11, 9,  'Casual blouse',          730.00, 1, 'in_progress'),
(16, '9988776655', 'BOUTIQUE_B', 12, 7,  'Office salwar',         1250.00, 1, 'in_progress'),
(17, '9876543210', 'BOUTIQUE_B', 13, 6,  'Silk blouse',            750.00, 1, 'delivered'),
(18, '9345678901', 'BOUTIQUE_B', 14, 10, 'Printed cotton salwar', 1080.00, 1, 'delivered')
ON CONFLICT DO NOTHING;

SELECT setval('order_item_details_id_seq', (SELECT MAX(id) FROM order_item_details));

-- ── Order Item Costs ──────────────────────────────────────────
INSERT INTO order_item_cost (id, item_id, mobile_no, tenant_code, cost, type, remarks) VALUES
-- Item 1 (Order 1, Priya@A Blouse — in_progress)
(1,  1,  '9876543210', 'BOUTIQUE_A',  500.00, 'fabric',     'Silk fabric'),
(2,  1,  '9876543210', 'BOUTIQUE_A',  300.00, 'stitching',  NULL),
-- Item 2 (Order 1, Priya@A Salwar — in_progress)
(3,  2,  '9876543210', 'BOUTIQUE_A',  700.00, 'fabric',     'Cotton blend'),
(4,  2,  '9876543210', 'BOUTIQUE_A',  400.00, 'stitching',  NULL),
(5,  2,  '9876543210', 'BOUTIQUE_A',  100.00, 'other',      'Buttons & zip'),
-- Item 3 (Order 2, Anita@A Lehenga — completed)
(6,  3,  '9123456789', 'BOUTIQUE_A', 1000.00, 'fabric',     'Embroidered net'),
(7,  3,  '9123456789', 'BOUTIQUE_A',  600.00, 'stitching',  NULL),
(8,  3,  '9123456789', 'BOUTIQUE_A',  200.00, 'embroidery', 'Border work'),
-- Item 4 (Order 3, Deepa@A Salwar — fresh)
(9,  4,  '9500001234', 'BOUTIQUE_A',  700.00, 'fabric',     'Cotton'),
(10, 4,  '9500001234', 'BOUTIQUE_A',  500.00, 'stitching',  NULL),
-- Item 5 (Order 4, Priya@B Blouse — in_progress)
(11, 5,  '9876543210', 'BOUTIQUE_B',  400.00, 'fabric',     'Silk'),
(12, 5,  '9876543210', 'BOUTIQUE_B',  500.00, 'stitching',  NULL),
-- Item 6 (Order 5, Meena@B Salwar — delivered)
(13, 6,  '9988776655', 'BOUTIQUE_B', 1000.00, 'fabric',     NULL),
(14, 6,  '9988776655', 'BOUTIQUE_B',  600.00, 'stitching',  NULL),
(15, 6,  '9988776655', 'BOUTIQUE_B',  200.00, 'embroidery', NULL),
-- Item 7 (Order 5, Meena@B Blouse — delivered)
(16, 7,  '9988776655', 'BOUTIQUE_B',  500.00, 'fabric',     NULL),
(17, 7,  '9988776655', 'BOUTIQUE_B',  400.00, 'stitching',  NULL),
-- Item 8 (Order 6, Priya@A Blouse — delivered)
(18, 8,  '9876543210', 'BOUTIQUE_A',  500.00, 'fabric',     NULL),
(19, 8,  '9876543210', 'BOUTIQUE_A',  300.00, 'stitching',  NULL),
-- Item 9 (Order 7, Anita@A Blouse — delivered)
(20, 9,  '9123456789', 'BOUTIQUE_A',  400.00, 'fabric',     NULL),
(21, 9,  '9123456789', 'BOUTIQUE_A',  250.00, 'stitching',  NULL),
-- Item 10 (Order 8, Deepa@A Blouse — in_progress)
(22, 10, '9500001234', 'BOUTIQUE_A',  600.00, 'fabric',     NULL),
(23, 10, '9500001234', 'BOUTIQUE_A',  350.00, 'stitching',  NULL),
-- Item 11 (Order 8, Deepa@A Salwar — in_progress)
(24, 11, '9500001234', 'BOUTIQUE_A',  700.00, 'fabric',     NULL),
(25, 11, '9500001234', 'BOUTIQUE_A',  400.00, 'stitching',  NULL),
(26, 11, '9500001234', 'BOUTIQUE_A',  100.00, 'other',      'Dupatta fabric'),
-- Item 12 (Order 9, Priya@A Salwar — fresh)
(27, 12, '9876543210', 'BOUTIQUE_A',  550.00, 'fabric',     NULL),
(28, 12, '9876543210', 'BOUTIQUE_A',  320.00, 'stitching',  NULL),
-- Item 13 (Order 10, Anita@A Blouse — completed)
(29, 13, '9123456789', 'BOUTIQUE_A',  400.00, 'fabric',     NULL),
(30, 13, '9123456789', 'BOUTIQUE_A',  260.00, 'stitching',  NULL),
-- Item 14 (Order 10, Anita@A Lehenga — completed)
(31, 14, '9123456789', 'BOUTIQUE_A',  900.00, 'fabric',     'Heavy embroidered net'),
(32, 14, '9123456789', 'BOUTIQUE_A',  500.00, 'stitching',  NULL),
(33, 14, '9123456789', 'BOUTIQUE_A',  300.00, 'embroidery', 'Full border work'),
-- Item 15 (Order 11, Kavitha@B Blouse — fresh)
(34, 15, '9345678901', 'BOUTIQUE_B',  450.00, 'fabric',     NULL),
(35, 15, '9345678901', 'BOUTIQUE_B',  280.00, 'stitching',  NULL),
-- Item 16 (Order 12, Meena@B Salwar — in_progress)
(36, 16, '9988776655', 'BOUTIQUE_B',  800.00, 'fabric',     NULL),
(37, 16, '9988776655', 'BOUTIQUE_B',  450.00, 'stitching',  NULL),
-- Item 17 (Order 13, Priya@B Blouse — delivered)
(38, 17, '9876543210', 'BOUTIQUE_B',  450.00, 'fabric',     'Silk'),
(39, 17, '9876543210', 'BOUTIQUE_B',  300.00, 'stitching',  NULL),
-- Item 18 (Order 14, Kavitha@B Salwar — delivered)
(40, 18, '9345678901', 'BOUTIQUE_B',  700.00, 'fabric',     'Printed cotton'),
(41, 18, '9345678901', 'BOUTIQUE_B',  380.00, 'stitching',  NULL)
ON CONFLICT DO NOTHING;

SELECT setval('order_item_cost_id_seq', (SELECT MAX(id) FROM order_item_cost));

-- ── Bills ─────────────────────────────────────────────────────
INSERT INTO bill_details
    (id, mobile_no, tenant_code, created_date, total_amount, advance_paid, balance_amount, status, discount, remarks)
VALUES
-- Bill 1: Anita@A, Order 2 (completed lehenga) — fully paid, closed
(1, '9123456789', 'BOUTIQUE_A', '2024-06-30',
    1800.00, 1800.00, 0.00, 'closed', NULL, 'Festival order — fully paid'),

-- Bill 2: Priya@A, Order 1 (in_progress wedding) — partial pay, pending
(2, '9876543210', 'BOUTIQUE_A', '2024-06-20',
    2500.00, 1000.00, 1500.00, 'pending', NULL, 'Wedding order — balance on delivery'),

-- Bill 3: Meena@B, Order 5 (delivered party wear) — fully paid, closed
(3, '9988776655', 'BOUTIQUE_B', '2024-07-05',
    3200.00, 3200.00, 0.00, 'closed', NULL, 'Party wear — fully paid'),

-- Bill 4: Priya@A, Order 6 (delivered summer blouse) — fully paid, closed
(4, '9876543210', 'BOUTIQUE_A', '2024-04-25',
    800.00, 800.00, 0.00, 'closed', NULL, 'Summer blouse — collected & paid'),

-- Bill 5: Anita@A, Orders 7+10 (multi-order, ₹100 loyalty discount) — partial pay, pending
--   Order7(650) + Order10(2360) = 3010 − 100 discount = 2910 total
(5, '9123456789', 'BOUTIQUE_A', '2024-07-26',
    2910.00, 2000.00, 910.00, 'pending', '100', 'Combined bill — Blouse + Lehenga set, ₹100 loyalty discount'),

-- Bill 6: Priya@B, Order 13 (delivered silk blouse) — fully paid, closed
(6, '9876543210', 'BOUTIQUE_B', '2024-05-06',
    750.00, 750.00, 0.00, 'closed', NULL, 'Silk blouse — collected & settled'),

-- Bill 7: Kavitha@B, Order 14 (delivered cotton salwar) — fully paid, closed
(7, '9345678901', 'BOUTIQUE_B', '2024-06-30',
    1080.00, 1080.00, 0.00, 'closed', NULL, 'Cotton salwar — settled'),

-- Bill 8: Meena@B, Order 12 (in_progress office salwar) — advance only, fresh
(8, '9988776655', 'BOUTIQUE_B', '2024-07-01',
    1250.00, 500.00, 750.00, 'fresh', NULL, 'Office salwar — advance received'),

-- Bill 9: Deepa@A, Order 3 (fresh order) — advance only, fresh
(9, '9500001234', 'BOUTIQUE_A', '2024-07-02',
    1200.00, 500.00, 700.00, 'fresh', NULL, 'New order — advance received')

ON CONFLICT DO NOTHING;

SELECT setval('bill_details_id_seq', (SELECT MAX(id) FROM bill_details));

-- ── Bill-Order Associations ───────────────────────────────────
INSERT INTO bill_orders_association (id, mobile_no, tenant_code, bill_id, order_id) VALUES
(1,  '9123456789', 'BOUTIQUE_A', 1,  2),   -- Bill 1  → Order 2  (Anita@A lehenga)
(2,  '9876543210', 'BOUTIQUE_A', 2,  1),   -- Bill 2  → Order 1  (Priya@A wedding)
(3,  '9988776655', 'BOUTIQUE_B', 3,  5),   -- Bill 3  → Order 5  (Meena@B party wear)
(4,  '9876543210', 'BOUTIQUE_A', 4,  6),   -- Bill 4  → Order 6  (Priya@A summer blouse)
(5,  '9123456789', 'BOUTIQUE_A', 5,  7),   -- Bill 5  → Order 7  (Anita@A — 1st of 2 orders)
(6,  '9123456789', 'BOUTIQUE_A', 5,  10),  -- Bill 5  → Order 10 (Anita@A — 2nd order, same bill)
(7,  '9876543210', 'BOUTIQUE_B', 6,  13),  -- Bill 6  → Order 13 (Priya@B silk blouse)
(8,  '9345678901', 'BOUTIQUE_B', 7,  14),  -- Bill 7  → Order 14 (Kavitha@B salwar)
(9,  '9988776655', 'BOUTIQUE_B', 8,  12),  -- Bill 8  → Order 12 (Meena@B in_progress)
(10, '9500001234', 'BOUTIQUE_A', 9,  3)    -- Bill 9  → Order 3  (Deepa@A fresh)
ON CONFLICT DO NOTHING;

SELECT setval('bill_orders_association_id_seq', (SELECT MAX(id) FROM bill_orders_association));
