ALTER TABLE customer ADD COLUMN card_number VARCHAR(50);
ALTER TABLE customer ADD COLUMN points INT NOT NULL DEFAULT 0;
ALTER TABLE customer ADD COLUMN total_spend DECIMAL(15,2) NOT NULL DEFAULT 0.00;
ALTER TABLE customer ADD COLUMN tier VARCHAR(50) NOT NULL DEFAULT 'SILVER';

-- Generate unique card numbers for existing customers (MEM-XXXX-XXXX)
UPDATE customer 
SET card_number = CONCAT('MEM-', 
                         LPAD(FLOOR(RAND() * 10000), 4, '0'), 
                         '-', 
                         LPAD(FLOOR(RAND() * 10000), 4, '0'))
WHERE card_number IS NULL;

ALTER TABLE customer ADD CONSTRAINT uq_customer_card_number UNIQUE (card_number);
