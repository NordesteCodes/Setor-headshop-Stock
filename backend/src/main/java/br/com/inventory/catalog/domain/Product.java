package br.com.inventory.catalog.domain;

import java.math.BigDecimal;
import java.util.UUID;
import static br.com.inventory.shared.domain.Rules.*;

public record Product(UUID id, String name, String sku, String description, UUID categoryId, UUID unitId,
                      BigDecimal price, BigDecimal purchasePrice, long minimum, boolean active, long version) {
    public Product {
        name = text(name, 120);
        sku = sku == null || sku.isBlank() ? null : text(sku, 80).toUpperCase(java.util.Locale.ROOT);
        description = description == null ? "" : description.strip();
        require(description.length()<=2000 && categoryId!=null && unitId!=null && minimum>=0, "Cadastro inválido.");
        price = money(price);
        purchasePrice = money(purchasePrice);
    }
}
