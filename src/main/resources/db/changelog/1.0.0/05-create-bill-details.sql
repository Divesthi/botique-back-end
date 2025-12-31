CREATE TABLE bill_details
(
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mobile_no           VARCHAR(25) NOT NULL,
    created_date        DATE,
    total_amount        DECIMAL(10,2),
    advance_paid        DECIMAL(10,2),
    balance_amount      DECIMAL(10,2),
    status              ENUM('fresh', 'closed', 'pending'),
    discount            VARCHAR(50),
    remarks             VARCHAR(500),
    FOREIGN KEY (mobile_no) REFERENCES customer_details(mobile_no)
);