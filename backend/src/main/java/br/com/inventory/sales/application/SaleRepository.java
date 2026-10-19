package br.com.inventory.sales.application;
import br.com.inventory.sales.domain.Sale;
import java.math.BigDecimal;
import java.util.UUID;
public interface SaleRepository {
    Sale lock(UUID id);
    void create(Sale sale, UUID actor);
    void addPayment(UUID id, BigDecimal amount);
    void delivered(UUID id);
    void lineCost(UUID sale, UUID product, BigDecimal cost);
}
