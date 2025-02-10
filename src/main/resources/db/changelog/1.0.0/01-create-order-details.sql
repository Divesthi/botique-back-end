CREATE TABLE order_details
(
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mobile_no           VARCHAR(25) NOT NULL,
    received_date       DATE,
    delivery_date       DATE,
    cutting_date        DATE,
    packaging_date      DATE,
    total_items         INT,
    remarks             VARCHAR(500),
    status              ENUM('fresh', 'in_progress','completed','delivered'),
    total               DECIMAL(10,2),
    advance             DECIMAL(10,2),
    balance             DECIMAL(10,2),
    FOREIGN KEY (mobile_no) REFERENCES customer_details(mobile_no)
);