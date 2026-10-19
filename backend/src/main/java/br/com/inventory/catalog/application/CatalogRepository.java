package br.com.inventory.catalog.application;
import br.com.inventory.catalog.domain.Product;
import java.util.UUID;
public interface CatalogRepository {
    Product product(UUID id);
    void save(Product product);
    void create(Product product);
    void namedValue(String type, UUID id, String name);
    void renameCategory(UUID id, String name);
    void deleteProduct(UUID id);
}
