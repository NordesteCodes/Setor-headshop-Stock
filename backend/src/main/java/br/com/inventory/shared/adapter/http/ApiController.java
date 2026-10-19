package br.com.inventory.shared.adapter.http;
import br.com.inventory.auth.adapter.Accounts.Identity;
import br.com.inventory.catalog.application.CatalogService;
import br.com.inventory.catalog.application.ProductRegistrationService;
import br.com.inventory.catalog.domain.Product;
import br.com.inventory.customers.application.CustomerService;
import br.com.inventory.customers.domain.Customer;
import br.com.inventory.finance.application.*;
import br.com.inventory.inventory.application.InventoryService;
import br.com.inventory.sales.application.SalesService;
import br.com.inventory.reporting.application.Queries;
import br.com.inventory.shared.application.CommandExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static br.com.inventory.shared.domain.Rules.*;

@RestController @RequestMapping("/api")
public class ApiController {
    private final CatalogService catalog;private final ProductRegistrationService productRegistration;private final CustomerService customers;private final InventoryService stock;
    private final SalesService sales;private final CashService cash;private final Queries queries;
    public ApiController(CatalogService catalog,ProductRegistrationService productRegistration,CustomerService customers,InventoryService stock,SalesService sales,CashService cash,Queries queries){this.catalog=catalog;this.productRegistration=productRegistration;this.customers=customers;this.stock=stock;this.sales=sales;this.cash=cash;this.queries=queries;}
    public record Result(UUID id){}
    public record Named(String name){}
    public record Payment(BigDecimal amount){}
    public record Reason(String reason){}
    @GetMapping("/{resource}") public Queries.Page list(@PathVariable String resource,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String filter,@RequestParam(defaultValue="")String sort,@RequestParam(required=false)UUID related){return queries.list(resource,page,size,search,filter,sort,related);}
    @GetMapping("/dashboard") public Map<String,Object> dashboard(){return queries.dashboard();}
    @GetMapping("/customers/{id}") public Customer customer(@PathVariable UUID id){return customers.get(id);}
    @GetMapping("/customers/{id}/summary") public Map<String,Object> customerSummary(@PathVariable UUID id){customers.get(id);return queries.customerSummary(id);}
    @GetMapping("/sales/{id}") public Map<String,Object> sale(@PathVariable UUID id){return queries.sale(id);}
    @PostMapping("/products") public Result product(@RequestHeader("Idempotency-Key")UUID key,@RequestBody ProductRegistrationService.Registration p,@AuthenticationPrincipal Identity actor){return new Result(productRegistration.create(key,p,actor.id()));}
    @PutMapping("/products/{id}") public Result editProduct(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@RequestBody Product p,@AuthenticationPrincipal Identity actor){require(id.equals(p.id()),"ID inválido.");return new Result(catalog.save(key,p,false,actor.id()));}
    @PostMapping("/categories") public Result category(@RequestHeader("Idempotency-Key")UUID key,@RequestBody Named n,@AuthenticationPrincipal Identity actor){return new Result(catalog.named(key,"category",n.name(),actor.id()));}
    @PutMapping("/categories/{id}") public Result editCategory(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@RequestBody Named n,@AuthenticationPrincipal Identity actor){return new Result(catalog.renameCategory(key,id,n.name(),actor.id()));}
    @DeleteMapping("/products/{id}") public Result deleteProduct(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@AuthenticationPrincipal Identity actor){return new Result(catalog.deleteProduct(key,id,actor.id()));}
    @PostMapping("/units") public Result unit(@RequestHeader("Idempotency-Key")UUID key,@RequestBody Named n,@AuthenticationPrincipal Identity actor){return new Result(catalog.named(key,"unit",n.name(),actor.id()));}
    @PostMapping("/customers") public Result customer(@RequestHeader("Idempotency-Key")UUID key,@RequestBody Customer c,@AuthenticationPrincipal Identity actor){require(c.id()!=null && c.version()==0,"ID e versão inicial obrigatórios.");return new Result(customers.save(key,c,true,actor.id()));}
    @PutMapping("/customers/{id}") public Result editCustomer(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@RequestBody Customer c,@AuthenticationPrincipal Identity actor){require(id.equals(c.id()),"ID inválido.");return new Result(customers.save(key,c,false,actor.id()));}
    @PostMapping("/stock-commands") public Result stock(@RequestHeader("Idempotency-Key")UUID key,@RequestBody InventoryService.Adjustment a,@AuthenticationPrincipal Identity actor){return new Result(stock.adjust(key,a,actor.id()));}
    @PostMapping("/sales") public Result sale(@RequestHeader("Idempotency-Key")UUID key,@RequestBody SalesService.NewSale sale,@AuthenticationPrincipal Identity actor){return new Result(sales.create(key,sale,actor.id()));}
    @PostMapping("/sales/{id}/payments") public Result payment(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@RequestBody Payment p,@AuthenticationPrincipal Identity actor){return new Result(sales.payment(key,id,money(p.amount()),actor.id()));}
    @PostMapping("/sales/{id}/delivery") public Result deliver(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@AuthenticationPrincipal Identity actor){return new Result(sales.deliver(key,id,actor.id()));}
    @PostMapping("/cash") public Result cash(@RequestHeader("Idempotency-Key")UUID key,@RequestBody CashService.Manual m,@AuthenticationPrincipal Identity actor){return new Result(cash.manual(key,m,actor.id()));}
    @PostMapping("/cash/{id}/reverse") public Result reverse(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID key,@RequestBody Reason r,@AuthenticationPrincipal Identity actor){return new Result(cash.reverse(key,id,r.reason(),actor.id()));}
}
