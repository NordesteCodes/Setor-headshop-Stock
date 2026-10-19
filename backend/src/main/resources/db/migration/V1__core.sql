CREATE TABLE app_user (
 id uuid PRIMARY KEY, username varchar(80) NOT NULL UNIQUE, password_hash text NOT NULL,
 totp_cipher text NOT NULL, last_totp_step bigint NOT NULL DEFAULT -1,
 active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE auth_attempt (bucket varchar(180) PRIMARY KEY, attempts integer NOT NULL, window_start timestamptz NOT NULL);
CREATE TABLE category (id uuid PRIMARY KEY, name varchar(100) NOT NULL UNIQUE CHECK(length(trim(name))>0));
CREATE TABLE unit (id uuid PRIMARY KEY, name varchar(30) NOT NULL UNIQUE CHECK(length(trim(name))>0));
INSERT INTO unit VALUES ('00000000-0000-0000-0000-000000000001','UN');
CREATE TABLE product (
 id uuid PRIMARY KEY, name varchar(120) NOT NULL CHECK(length(trim(name))>0), sku varchar(80) UNIQUE,
 description varchar(2000) NOT NULL DEFAULT '', category_id uuid NOT NULL REFERENCES category,
 unit_id uuid NOT NULL REFERENCES unit, price numeric(18,2) NOT NULL CHECK(price>=0),
 minimum bigint NOT NULL CHECK(minimum>=0), active boolean NOT NULL DEFAULT true,
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE stock (
 product_id uuid PRIMARY KEY REFERENCES product, quantity bigint NOT NULL DEFAULT 0 CHECK(quantity>=0),
 average_cost numeric(20,6) NOT NULL DEFAULT 0 CHECK(average_cost>=0)
);
CREATE TABLE customer (
 id uuid PRIMARY KEY, name varchar(150) NOT NULL CHECK(length(trim(name))>0), document varchar(18),
 phone varchar(30), email varchar(200), street varchar(200) NOT NULL, number varchar(30) NOT NULL,
 district varchar(100) NOT NULL, city varchar(100) NOT NULL, state char(2) NOT NULL,
 postal_code char(8) NOT NULL CHECK(postal_code ~ '^[0-9]{8}$'), complement varchar(200), reference varchar(200),
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE sale (
 id uuid PRIMARY KEY, customer_id uuid NOT NULL REFERENCES customer, customer_name varchar(150) NOT NULL,
 address jsonb NOT NULL, total numeric(18,2) NOT NULL CHECK(total>0), paid numeric(18,2) NOT NULL DEFAULT 0 CHECK(paid>=0 AND paid<=total),
 delivered_at timestamptz, settled_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), actor uuid NOT NULL REFERENCES app_user
);
CREATE TABLE sale_line (
 sale_id uuid NOT NULL REFERENCES sale, product_id uuid NOT NULL REFERENCES product, product_name varchar(120) NOT NULL,
 quantity bigint NOT NULL CHECK(quantity>0), price numeric(18,2) NOT NULL CHECK(price>=0),
 delivered_cost numeric(20,6) CHECK(delivered_cost>=0), PRIMARY KEY(sale_id,product_id)
);
CREATE TABLE stock_movement (
 id uuid PRIMARY KEY, product_id uuid NOT NULL REFERENCES product, delta bigint NOT NULL CHECK(delta<>0),
 balance bigint NOT NULL CHECK(balance>=0), unit_cost numeric(20,6) NOT NULL CHECK(unit_cost>=0),
 type varchar(30) NOT NULL CHECK(type IN ('INBOUND','DAMAGE','DELIVERY','CORRECTION')),
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0), sale_id uuid REFERENCES sale,
 actor uuid NOT NULL REFERENCES app_user, source varchar(30) NOT NULL DEFAULT 'INTERNAL', created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(sale_id,product_id)
);
CREATE TABLE cash_entry (
 id uuid PRIMARY KEY, amount numeric(18,2) NOT NULL CHECK(amount<>0),
 category varchar(40) NOT NULL CHECK(category IN ('OPENING','CONTRIBUTION','WITHDRAWAL','EXPENSE','PURCHASE','RECEIPT','REIMBURSEMENT','REVERSAL')),
 description varchar(500) NOT NULL CHECK(length(trim(description))>0),
 sale_id uuid REFERENCES sale, damage_id uuid REFERENCES stock_movement, reversal_of uuid UNIQUE REFERENCES cash_entry,
 actor uuid NOT NULL REFERENCES app_user, created_at timestamptz NOT NULL DEFAULT now(),
 CHECK((category='RECEIPT' AND sale_id IS NOT NULL AND amount>0) OR category<>'RECEIPT'),
 CHECK((category='REIMBURSEMENT' AND damage_id IS NOT NULL AND amount>0) OR category<>'REIMBURSEMENT'),
 CHECK((category='REVERSAL')=(reversal_of IS NOT NULL))
);
CREATE UNIQUE INDEX cash_one_opening ON cash_entry(category) WHERE category='OPENING';
CREATE TABLE command_receipt (
 scope varchar(80) NOT NULL, key uuid NOT NULL, fingerprint char(64) NOT NULL, result uuid NOT NULL,
 actor uuid NOT NULL REFERENCES app_user, status varchar(20) NOT NULL DEFAULT 'COMPLETED' CHECK(status='COMPLETED'),
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(scope,key)
);
CREATE TABLE annual_goal (
 year integer PRIMARY KEY CHECK(year BETWEEN 2020 AND 2200), revenue numeric(18,2) NOT NULL CHECK(revenue>=0),
 margin numeric(18,2) NOT NULL CHECK(margin>=0), actor uuid NOT NULL REFERENCES app_user, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE audit_entry (
 id uuid PRIMARY KEY, actor uuid NOT NULL REFERENCES app_user, action varchar(80) NOT NULL,
 resource_id uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX product_category ON product(category_id);
CREATE INDEX movement_product_time ON stock_movement(product_id,created_at DESC);
CREATE INDEX cash_time ON cash_entry(created_at DESC);
CREATE INDEX cash_sale ON cash_entry(sale_id);
CREATE INDEX sale_customer ON sale(customer_id,created_at DESC);
CREATE INDEX sale_pending_delivery ON sale(created_at) WHERE delivered_at IS NULL;
CREATE FUNCTION forbid_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Audit history is immutable'; END; $$;
CREATE TRIGGER stock_history_immutable BEFORE UPDATE OR DELETE ON stock_movement FOR EACH ROW EXECUTE FUNCTION forbid_history_mutation();
CREATE TRIGGER cash_history_immutable BEFORE UPDATE OR DELETE ON cash_entry FOR EACH ROW EXECUTE FUNCTION forbid_history_mutation();
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit_entry FOR EACH ROW EXECUTE FUNCTION forbid_history_mutation();
CREATE TRIGGER receipt_immutable BEFORE UPDATE OR DELETE ON command_receipt FOR EACH ROW EXECUTE FUNCTION forbid_history_mutation();
