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
    /** Restricted maintenance for an incorrectly valued single opening lot; caller owns transaction. */
    public UUID repairOpeningCost(UUID key, UUID product, BigDecimal cost, String reason, String operator) {
        require(key!=null && product!=null,"Chave e produto obrigatórios.");
        cost=money(cost); reason=text(reason,500); operator=text(operator,150);
        var existing=stock.costRepair(key);
        if(existing!=null) {
            require(existing.product().equals(product) && existing.cost().compareTo(cost)==0 && existing.reason().equals(reason) && existing.operator().equals(operator),"Chave de correção já usada com outros dados.");
            return key;
        }
        var before=stock.lock(product);
        var history=stock.costHistory(product);
        require(!history.isEmpty() && history.getFirst().type().equals("INBOUND"),"Entrada inicial não encontrada.");
        long balance=0;
        for(int i=0;i<history.size();i++) {
            var movement=history.get(i);
            require(i==0 || java.util.Set.of("DELIVERY","DAMAGE").contains(movement.type()),"Correção exige um único lote de entrada, sem reposições ou ajustes.");
            require(movement.cost().compareTo(before.averageCost())==0,"Custos divergentes: necessária reconciliação específica.");
            require(i==0 ? movement.delta()>0 : movement.delta()<0,"Direção de movimento inválida.");
            balance=Math.addExact(balance,movement.delta());
            require(balance>=0,"Histórico de estoque inconsistente.");
        }
        require(balance==before.quantity(),"Saldo diverge dos movimentos.");
        stock.recordCostRepair(new InventoryRepository.CostRepair(key,product,cost,reason,operator),before,history);
        stock.update(product,new StockPosition(before.quantity(),cost.setScale(6)));
        return key;
    }
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
