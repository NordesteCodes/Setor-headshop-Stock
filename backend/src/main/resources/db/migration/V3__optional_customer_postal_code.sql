ALTER TABLE customer ALTER COLUMN postal_code DROP NOT NULL;
ALTER TABLE customer DROP CONSTRAINT customer_postal_code_check;
ALTER TABLE customer ADD CONSTRAINT customer_postal_code_optional_check
    CHECK (postal_code IS NULL OR postal_code ~ '^[0-9]{8}$');
