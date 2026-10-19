package br.com.inventory.finance.application;
import java.math.BigDecimal;
import java.util.UUID;
public interface CashRepository {
    record Entry(UUID id, BigDecimal amount, String category, String description, UUID saleId, UUID damageId, UUID reversalOf) {}
    void append(Entry entry, UUID actor);
    Entry lock(UUID id);
    boolean reversed(UUID id);
}
