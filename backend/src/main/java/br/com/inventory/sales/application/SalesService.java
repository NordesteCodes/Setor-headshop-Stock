package br.com.inventory.sales.application;
import br.com.inventory.catalog.application.CatalogRepository;
import br.com.inventory.customers.application.CustomerRepository;
import br.com.inventory.finance.application.CashRepository;
import br.com.inventory.inventory.application.InventoryService;
import br.com.inventory.sales.domain.Sale;
import br.com.inventory.shared.application.CommandExecutor;
import java.math.BigDecimal;
import java.util.*;
import static br.com.inventory.shared.domain.Rules.*;

public class SalesService {
    private final SaleRepository sales;
    private final CatalogRepository catalog;
    private final CustomerRepository customers;
    private final CashRepository cash;
    private final InventoryService inventory;
    private final CommandExecutor commands;
    public SalesService(SaleRepository sales,CatalogRepository catalog,CustomerRepository customers,CashRepository cash,InventoryService inventory,CommandExecutor commands) {
        this.sales=sales; this.catalog=catalog; this.customers=customers; this.cash=cash; this.inventory=inventory; this.commands=commands;
    }
    public record Item(UUID productId,long quantity,BigDecimal price) {}
    public record NewSale(UUID customerId,List<Item> items,BigDecimal initialPayment) {}
    public UUID create(UUID key,NewSale input,UUID actor) {
        return commands.execute("sale.create",key,input,actor,()-> {
            require(input.items()!=null && !input.items().isEmpty() && input.items().size()<=100,"Selecione de 1 a 100 produtos.");
            var customer=customers.customer(input.customerId());
            var ids=new HashSet<UUID>(); var lines=new ArrayList<Sale.Line>();
            for(var item:input.items()) {
                require(ids.add(item.productId()),"Produto repetido no pedido.");
                var product=catalog.product(item.productId()); require(product.active(),"Produto inativo.");
                lines.add(new Sale.Line(product.id(),product.name(),item.quantity(),item.price(),null));
            }
            var total=lines.stream().map(Sale.Line::total).reduce(BigDecimal.ZERO,BigDecimal::add);
            money(total); require(total.signum()>0,"Total deve ser maior que zero.");
            var payment=money(input.initialPayment());
            UUID id=UUID.randomUUID();
            var sale=new Sale(id,customer.id(),customer.name(),customer.address(),total,BigDecimal.ZERO,null,lines);
            sales.create(sale,actor);
            if(payment.signum()>0) receive(sale,payment,actor);
            return id;
        });
    }
    public UUID payment(UUID key,UUID saleId,BigDecimal amount,UUID actor) {
        return commands.execute("sale.payment",key,List.of(saleId,amount),actor,()->receive(sales.lock(saleId),amount,actor));
    }
    private UUID receive(Sale sale,BigDecimal amount,UUID actor) {
        sale.validatePayment(amount); UUID id=UUID.randomUUID();
        cash.append(new CashRepository.Entry(id,money(amount),"RECEIPT","Recebimento de pedido",sale.id(),null,null),actor);
        sales.addPayment(sale.id(),amount);
        return id;
    }
    public UUID deliver(UUID key,UUID id,UUID actor) {
        return commands.execute("sale.delivery",key,id,actor,()-> {
            var sale=sales.lock(id); sale.validateDelivery();
            sale.lines().stream().sorted(Comparator.comparing(line->line.productId().toString())).forEach(line -> {
                var cost=inventory.deliver(line.productId(),line.quantity(),id,actor);
                sales.lineCost(id,line.productId(),cost);
            });
            sales.delivered(id); return id;
        });
    }
}
