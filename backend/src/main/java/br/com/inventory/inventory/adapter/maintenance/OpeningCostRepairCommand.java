package br.com.inventory.inventory.adapter.maintenance;

import br.com.inventory.InventoryApplication;
import br.com.inventory.inventory.application.InventoryService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;

/** Local operator command; no public endpoint or automatic data repair at startup. */
public final class OpeningCostRepairCommand {
    public static void main(String[] args) {
        if(args.length!=5) throw new IllegalArgumentException("Usage: operation-uuid product-uuid unit-cost operator reason");
        try(var context=new SpringApplicationBuilder(InventoryApplication.class).properties("server.port=0").run()) {
            var service=context.getBean(InventoryService.class);
            var transaction=context.getBean(TransactionTemplate.class);
            var result=transaction.execute(status->service.repairOpeningCost(UUID.fromString(args[0]),UUID.fromString(args[1]),new BigDecimal(args[2]),args[4],args[3]));
            System.out.println("COST_REPAIR_COMPLETED="+result);
        }
    }
}
