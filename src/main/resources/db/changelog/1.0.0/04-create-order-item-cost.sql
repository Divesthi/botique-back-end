CREATE TABLE order_item_cost
(
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_id             BIGINT UNSIGNED NOT NULL,
    mobile_no           VARCHAR(25) NOT NULL,
    cost                DECIMAL(10,2),
    type                VARCHAR(50),
    remarks             VARCHAR(500),
    FOREIGN KEY (item_id) REFERENCES order_item_details(id),
    FOREIGN KEY (mobile_no) REFERENCES customer_details(mobile_no)
);