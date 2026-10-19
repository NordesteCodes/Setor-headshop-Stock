package br.com.inventory.catalog.application;

import br.com.inventory.catalog.domain.Product;
import br.com.inventory.inventory.application.InventoryService;
import br.com.inventory.shared.application.CommandExecutor;
import java.math.BigDecimal;
import java.util.UUID;
import static br.com.inventory.shared.domain.Rules.*;

/** Coordinates product creation and its optional opening stock in one idempotent transaction. */
public class ProductRegistrationService {
    public record Registration(Product product,long initialQuantity) {}

    private final CatalogRepository catalog;
    private final InventoryService inventory;
    private final CommandExecutor commands;

    public ProductRegistrationService(CatalogRepository catalog,InventoryService inventory,CommandExecutor commands) {
        this.catalog=catalog;
        this.inventory=inventory;
        this.commands=commands;
    }

    public UUID create(UUID key,Registration registration,UUID actor) {
        var product=registration.product();
        require(product!=null && product.id()!=null && product.version()==0,"Cadastro de produto inválido.");
        require(registration.initialQuantity()>=0 && registration.initialQuantity()<=1_000_000_000L,"Quantidade inicial inválida.");
        var cost=registration.initialQuantity()>0 ? product.purchasePrice() : BigDecimal.ZERO.setScale(2);
        var payload=new Registration(product,registration.initialQuantity());
        return commands.execute("product.create",key,payload,actor,()-> {
            catalog.create(product);
            inventory.initialize(product.id(),payload.initialQuantity(),cost,actor);
            return product.id();
        });
    }
}
