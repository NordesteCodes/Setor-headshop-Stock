package br.com.inventory.inventory.application;
import br.com.inventory.inventory.domain.StockPosition;
import java.math.BigDecimal;
import java.util.UUID;
public interface InventoryRepository {
    StockPosition lock(UUID product);
    boolean saleContainsProduct(UUID sale, UUID product);
    void update(UUID product, StockPosition position);
    void movement(UUID id, UUID product, long delta, long balance, BigDecimal cost, String type, String reason, UUID sale, UUID actor);
    record CostMovement(UUID id, long delta, BigDecimal cost, String type) {}
    record CostRepair(UUID id, UUID product, BigDecimal cost, String reason, String operator) {}
    CostRepair costRepair(UUID key);
    java.util.List<CostMovement> costHistory(UUID product);
    void recordCostRepair(CostRepair repair, StockPosition before, java.util.List<CostMovement> movements);
}
