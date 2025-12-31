CREATE TABLE bill_orders_association
(
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mobile_no           VARCHAR(25) NOT NULL,
    order_id            BIGINT UNSIGNED NOT NULL,
    bill_id             BIGINT UNSIGNED NOT NULL,
    CONSTRAINT BILL_ORDER_UC UNIQUE (order_id, bill_id),
    FOREIGN KEY (mobile_no) REFERENCES customer_details(mobile_no),
    FOREIGN KEY (order_id) REFERENCES order_details(id),
    FOREIGN KEY (bill_id) REFERENCES bill_details(id)
);