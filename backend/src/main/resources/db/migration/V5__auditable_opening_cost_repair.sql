-- Original quantities, movement costs and delivered costs remain immutable.
CREATE TABLE opening_cost_repair (
 id uuid PRIMARY KEY,
 product_id uuid NOT NULL UNIQUE REFERENCES product,
 unit_cost numeric(18,2) NOT NULL CHECK(unit_cost>=0),
 previous_average_cost numeric(20,6) NOT NULL CHECK(previous_average_cost>=0),
 quantity_at_repair bigint NOT NULL CHECK(quantity_at_repair>=0),
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 operator varchar(150) NOT NULL CHECK(length(trim(operator))>0),
 database_role text NOT NULL DEFAULT current_user,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE opening_cost_repair_movement (
 movement_id uuid PRIMARY KEY REFERENCES stock_movement,
 repair_id uuid NOT NULL REFERENCES opening_cost_repair,
 original_unit_cost numeric(20,6) NOT NULL CHECK(original_unit_cost>=0)
);
CREATE FUNCTION prevent_cost_repair_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Cost repairs are append-only; original evidence must be preserved';
END $$;
CREATE TRIGGER opening_cost_repair_immutable BEFORE UPDATE OR DELETE ON opening_cost_repair
 FOR EACH ROW EXECUTE FUNCTION prevent_cost_repair_mutation();
CREATE TRIGGER opening_cost_repair_movement_immutable BEFORE UPDATE OR DELETE ON opening_cost_repair_movement
 FOR EACH ROW EXECUTE FUNCTION prevent_cost_repair_mutation();

CREATE VIEW effective_stock_movement AS
SELECT m.id,m.product_id,m.delta,m.balance,coalesce(r.unit_cost,m.unit_cost) unit_cost,
 m.type,m.reason,m.sale_id,m.actor,m.source,m.created_at,
 m.unit_cost original_unit_cost,r.id cost_repair_id,r.reason cost_repair_reason
FROM stock_movement m
LEFT JOIN opening_cost_repair_movement link ON link.movement_id=m.id
LEFT JOIN opening_cost_repair r ON r.id=link.repair_id;

CREATE VIEW effective_sale_line AS
SELECT sl.sale_id,sl.product_id,sl.product_name,sl.quantity,sl.price,
 coalesce(r.unit_cost,sl.delivered_cost) delivered_cost,
 sl.delivered_cost original_delivered_cost,r.id cost_repair_id
FROM sale_line sl
LEFT JOIN stock_movement m ON m.sale_id=sl.sale_id AND m.product_id=sl.product_id AND m.type='DELIVERY'
LEFT JOIN opening_cost_repair_movement link ON link.movement_id=m.id
LEFT JOIN opening_cost_repair r ON r.id=link.repair_id;
