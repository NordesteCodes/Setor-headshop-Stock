package br.com.inventory.inventory.adapter.postgres;

import br.com.inventory.inventory.application.InventoryRepository;
import br.com.inventory.inventory.domain.StockPosition;
import br.com.inventory.shared.adapter.postgres.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class JdbcInventory implements InventoryRepository {
    private final JdbcTemplate db;

    public JdbcInventory(JdbcTemplate db) {
        this.db = db;
    }

    public StockPosition lock(UUID id) {
        return Rows.one(db.query("SELECT quantity,average_cost FROM stock WHERE product_id=? FOR UPDATE", (r, n) -> new StockPosition(r.getLong(1), r.getBigDecimal(2)), id));
    }
    public boolean saleContainsProduct(UUID sale,UUID product){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM sale_line WHERE sale_id=? AND product_id=?)",Boolean.class,sale,product));}

    public void update(UUID id, StockPosition p) {
        db.update("UPDATE stock SET quantity=?,average_cost=? WHERE product_id=?", p.quantity(), p.averageCost(), id);
    }

    public void movement(UUID id, UUID product, long delta, long balance, BigDecimal cost, String type, String reason, UUID sale, UUID actor) {
        db.update("INSERT INTO stock_movement(id,product_id,delta,balance,unit_cost,type,reason,sale_id,actor) VALUES (?,?,?,?,?,?,?,?,?)", id, product, delta, balance, cost, type, reason, sale, actor);
    }

    public CostRepair costRepair(UUID key) {
        db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"opening-cost-repair:"+key);
        var rows=db.query("SELECT * FROM opening_cost_repair WHERE id=?",(r,n)->new CostRepair(key,r.getObject("product_id",UUID.class),r.getBigDecimal("unit_cost"),r.getString("reason"),r.getString("operator")),key);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public java.util.List<CostMovement> costHistory(UUID product) {
        return db.query("SELECT id,delta,unit_cost,type FROM stock_movement WHERE product_id=? ORDER BY created_at,id",(r,n)->new CostMovement(r.getObject("id",UUID.class),r.getLong("delta"),r.getBigDecimal("unit_cost"),r.getString("type")),product);
    }
    public void recordCostRepair(CostRepair repair,StockPosition before,java.util.List<CostMovement> movements) {
        db.update("INSERT INTO opening_cost_repair(id,product_id,unit_cost,previous_average_cost,quantity_at_repair,reason,operator) VALUES (?,?,?,?,?,?,?)",repair.id(),repair.product(),repair.cost(),before.averageCost(),before.quantity(),repair.reason(),repair.operator());
        for(var movement:movements) db.update("INSERT INTO opening_cost_repair_movement(movement_id,repair_id,original_unit_cost) VALUES (?,?,?)",movement.id(),repair.id(),movement.cost());
    }
}
