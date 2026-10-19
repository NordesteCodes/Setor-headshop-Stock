package br.com.inventory.config;
import br.com.inventory.catalog.application.CatalogRepository;
import br.com.inventory.customers.application.CustomerRepository;
import br.com.inventory.finance.application.*;
import br.com.inventory.inventory.application.*;
import br.com.inventory.sales.application.*;
import br.com.inventory.shared.application.CommandExecutor;
import org.springframework.context.annotation.*;

@Configuration
public class UseCaseConfiguration {
    @Bean br.com.inventory.catalog.application.CatalogService catalogService(CatalogRepository r,CommandExecutor c){return new br.com.inventory.catalog.application.CatalogService(r,c);}
    @Bean br.com.inventory.catalog.application.ProductRegistrationService productRegistrationService(CatalogRepository r,InventoryService s,CommandExecutor c){return new br.com.inventory.catalog.application.ProductRegistrationService(r,s,c);}
    @Bean br.com.inventory.customers.application.CustomerService customerService(CustomerRepository r,CommandExecutor c){return new br.com.inventory.customers.application.CustomerService(r,c);}
    @Bean InventoryService inventoryService(InventoryRepository s,CatalogRepository p,CommandExecutor c){return new InventoryService(s,p,c);}
    @Bean SalesService salesService(SaleRepository s,CatalogRepository p,CustomerRepository u,CashRepository cash,InventoryService stock,CommandExecutor c){return new SalesService(s,p,u,cash,stock,c);}
    @Bean CashService cashService(CashRepository cash,SaleRepository s,CommandExecutor c){return new CashService(cash,s,c);}
}
