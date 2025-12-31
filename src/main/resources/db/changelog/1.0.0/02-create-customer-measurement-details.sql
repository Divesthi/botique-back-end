CREATE TABLE customer_measurement_details
(
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mobile_no           VARCHAR(25) NOT NULL,
    dress_type          VARCHAR(50) NOT NULL,
    measurement         JSON NOT NULL,
    remarks             VARCHAR(500),
    name                VARCHAR(100) NOT NULL,
    creation_date       DATE NOT NULL,
    FOREIGN KEY (mobile_no) REFERENCES customer_details(mobile_no)
);