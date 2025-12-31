CREATE TABLE order_item_details
(
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mobile_no           VARCHAR(25) NOT NULL,
    order_id            BIGINT UNSIGNED NOT NULL,
    measurement_id      BIGINT UNSIGNED NOT NULL,
    remarks             VARCHAR(500),
    cost_per_quantity   DECIMAL(10,2),
    quantity            INT,
    status              ENUM('new', 'in_progress','completed','delivered') NOT NULL,
    FOREIGN KEY (mobile_no) REFERENCES customer_details(mobile_no),
    FOREIGN KEY (order_id) REFERENCES order_details(id),
    FOREIGN KEY (measurement_id) REFERENCES customer_measurement_details(id)
);