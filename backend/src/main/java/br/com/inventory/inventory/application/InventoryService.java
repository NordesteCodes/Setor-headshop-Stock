package br.com.inventory.inventory.application;
import br.com.inventory.catalog.application.CatalogRepository;
import br.com.inventory.inventory.domain.StockPosition;
import br.com.inventory.shared.application.CommandExecutor;
import java.math.BigDecimal;
import java.util.UUID;
import static br.com.inventory.shared.domain.Rules.*;

/** The only application service permitted to mutate physical stock. Caller owns transaction. */
public class InventoryService {
    private final InventoryRepository stock;
    private final CatalogRepository catalog;
    private final CommandExecutor commands;
    public InventoryService(InventoryRepository stock,CatalogRepository catalog,CommandExecutor commands) {
        this.stock=stock; this.catalog=catalog; this.commands=commands;
    }
    public record Adjustment(UUID productId,long quantity,BigDecimal unitCost,String type,String reason) {}
    public UUID adjust(UUID key,Adjustment input,UUID actor) {
        return commands.execute("stock",key,input,actor,()-> {
            require(input.quantity()>0 && input.quantity()<=1_000_000_000L,"Quantidade inválida.");
            require(input.type()!=null && java.util.Set.of("INBOUND","DAMAGE").contains(input.type()),"Tipo inválido.");
            text(input.reason(),500);
            var before=stock.lock(input.productId());
            require(catalog.product(input.productId()).active(),"Produto inativo.");
            boolean inbound=input.type().equals("INBOUND");
            var after=inbound ? before.receive(input.quantity(),input.unitCost()) : before.deduct(input.quantity());
            UUID id=UUID.randomUUID();
            stock.update(input.productId(),after);
            stock.movement(id,input.productId(),inbound?input.quantity():-input.quantity(),after.quantity(),inbound?input.unitCost():before.averageCost(),input.type(),input.reason(),null,actor);
            return id;
        });
    }
    /** Initializes stock only as part of the atomic product-registration use case. */
    public void initialize(UUID product,long quantity,BigDecimal unitCost,UUID actor) {
        if(quantity==0) return;
        require(quantity>0 && quantity<=1_000_000_000L,"Quantidade inicial inválida.");
        var before=stock.lock(product);
        require(before.quantity()==0,"O produto já possui saldo inicial.");
        var after=before.receive(quantity,unitCost);
        stock.update(product,after);
        stock.movement(UUID.randomUUID(),product,quantity,after.quantity(),unitCost,"INBOUND","Estoque inicial no cadastro",null,actor);
    }
    public BigDecimal deliver(UUID product,long quantity,UUID sale,UUID actor) {
        var before=stock.lock(product);
        require(catalog.product(product).active() || stock.saleContainsProduct(sale,product),"Produto inativo; revise o pedido.");
        StockPosition after=before.deduct(quantity);
        stock.update(product,after);
        stock.movement(UUID.randomUUID(),product,-quantity,after.quantity(),before.averageCost(),"DELIVERY","Entrega do pedido",sale,actor);
        return before.averageCost();
    }
}
