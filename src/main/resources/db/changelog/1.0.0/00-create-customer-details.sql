CREATE TABLE customer_details
(
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL,
    address                 VARCHAR(500) NOT NULL,
    mobile_no               VARCHAR(25) UNIQUE NOT NULL,
    alternate_contact_no    VARCHAR(25),
    tenant_id               VARCHAR(25),
    creation_date           DATE
);