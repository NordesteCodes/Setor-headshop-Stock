ALTER TABLE product
    ADD COLUMN purchase_price numeric(18,2) NOT NULL DEFAULT 0 CHECK (purchase_price >= 0),
    ADD COLUMN deleted_at timestamptz;

CREATE INDEX product_visible ON product(name) WHERE deleted_at IS NULL;
