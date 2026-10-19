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
}
