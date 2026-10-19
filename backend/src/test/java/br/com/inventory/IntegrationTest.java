package br.com.inventory;
import br.com.inventory.auth.adapter.*;
import br.com.inventory.catalog.application.*;
import br.com.inventory.catalog.domain.Product;
import br.com.inventory.customers.application.*;
import br.com.inventory.customers.domain.Customer;
import br.com.inventory.finance.application.*;
import br.com.inventory.inventory.application.InventoryService;
import br.com.inventory.sales.application.SalesService;
import br.com.inventory.shared.application.CommandExecutor;
import br.com.inventory.shared.domain.BusinessException;
import br.com.inventory.reporting.application.Queries;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc @Testcontainers
class IntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @Autowired JdbcTemplate db;@Autowired CatalogService catalog;@Autowired CustomerService customers;@Autowired InventoryService stock;@Autowired SalesService sales;@Autowired CashService cash;@Autowired CommandExecutor commands;@Autowired Queries queries;@Autowired MockMvc mvc;@Autowired SecretCipher cipher;@Autowired ObjectMapper json;
    @Autowired Accounts accounts;
    @Autowired ProductRegistrationService productRegistration;
    UUID actor,category,customer;static final UUID UNIT=UUID.fromString("00000000-0000-0000-0000-000000000001");
    final String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    @BeforeEach void setup(){
        db.execute("TRUNCATE audit_entry,command_receipt,cash_entry,stock_movement,sale_line,sale,customer,stock,product,category,annual_goal,auth_attempt,app_user CASCADE");
        actor=UUID.randomUUID();db.update("INSERT INTO app_user(id,username,password_hash,totp_cipher) VALUES (?,?,?,?)",actor,"tester",new BCryptPasswordEncoder(4).encode("local-test-password"),cipher.encrypt(secret));
        category=catalog.named(UUID.randomUUID(),"category","Geral",actor);
        customer=UUID.randomUUID();customers.save(UUID.randomUUID(),new Customer(customer,"Cliente teste","","","",new Customer.Address("Rua Teste","10","Centro","São Paulo","SP","01001000","",""),0),true,actor);
    }
    UUID product(long quantity){
        UUID id=UUID.randomUUID();catalog.save(UUID.randomUUID(),new Product(id,"Produto "+id,null,"",category,UNIT,new BigDecimal("20.00"),new BigDecimal("10.00"),5,true,0),true,actor);
        if(quantity>0)stock.adjust(UUID.randomUUID(),new InventoryService.Adjustment(id,quantity,new BigDecimal("10.00"),"INBOUND","Compra"),actor);return id;
    }
    @Test void productRegistrationAtomicallyCreatesOpeningStockAndIsIdempotent(){
        var key=UUID.randomUUID();
        var product=new Product(UUID.randomUUID(),"Produto inicial QA",null,"",category,UNIT,new BigDecimal("20.00"),new BigDecimal("4.25"),0,true,0);
        var command=new ProductRegistrationService.Registration(product,8);
        UUID id=productRegistration.create(key,command,actor);
        assertThat(quantity(id)).isEqualTo(8);
        assertThat(db.queryForObject("SELECT average_cost FROM stock WHERE product_id=?",BigDecimal.class,id)).isEqualByComparingTo("4.25");
        assertThat(db.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id=?",Long.class,id)).isEqualTo(1);
        assertThat(productRegistration.create(key,command,actor)).isEqualTo(id);
        assertThat(db.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id=?",Long.class,id)).isEqualTo(1);
        var empty=new Product(UUID.randomUUID(),"Produto zerado QA",null,"",category,UNIT,new BigDecimal("1.00"),new BigDecimal("0.40"),0,true,0);
        var emptyCommand=new ProductRegistrationService.Registration(empty,0);
        UUID emptyId=productRegistration.create(UUID.randomUUID(),emptyCommand,actor);
        assertThat(quantity(emptyId)).isZero();
        assertThat(db.queryForObject("SELECT purchase_price FROM product WHERE id=?",BigDecimal.class,emptyId)).isEqualByComparingTo("0.40");
        assertThat(db.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id=?",Long.class,emptyId)).isZero();
    }
    @Test void productSearchCoversCategoryQuantityCostsPriceAndSituationAndSortsQuantity(){
        var smaller=product(1234567);var larger=product(7654321);
        var byCategory=queries.list("products",0,20,"Geral","active","",null);
        assertThat(byCategory.total()).isEqualTo(2);
        assertThat(queries.list("products",0,20,"1234567","active","",null).items()).extracting(r->r.get("id")).contains(smaller);
        assertThat(queries.list("products",0,20,"10.000000","active","",null).total()).isEqualTo(2); // Average cost text also includes this value.
        assertThat(queries.list("products",0,20,"20.00","active","",null).total()).isEqualTo(2);
        assertThat(queries.list("products",0,20,"ativo","active","",null).total()).isEqualTo(2);
        assertThat(queries.list("products",0,20,"inativo","active","",null).total()).isZero();
        var descending=queries.list("products",0,20,"","active","quantity",null);
        var ascending=queries.list("products",0,20,"","active","quantity-asc",null);
        assertThat(descending.items().getFirst().get("id")).isEqualTo(larger);
        assertThat(ascending.items().getFirst().get("id")).isEqualTo(smaller);
    }
    @Test void productsCanBeArchivedWithStockHistoryAndCategoriesCanBeRenamed(){
        var empty=UUID.randomUUID();
        productRegistration.create(UUID.randomUUID(),new ProductRegistrationService.Registration(new Product(empty,"Descartável",null,"",category,UNIT,new BigDecimal("1.00"),BigDecimal.ZERO,0,true,0),0),actor);
        var movement=product(1);var pendingSale=sale(movement,1,"0");
        assertThat(catalog.deleteProduct(UUID.randomUUID(),movement,actor)).isEqualTo(movement);
        assertThat(quantity(movement)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id=?",Long.class,movement)).isEqualTo(1);
        sales.deliver(UUID.randomUUID(),pendingSale,actor);
        assertThat(quantity(movement)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM sale_line WHERE sale_id=?",Long.class,pendingSale)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT active FROM product WHERE id=?",Boolean.class,movement)).isFalse();
        assertThat(queries.list("products",0,20,"Produto "+movement,"","",null).total()).isZero();
        assertThat(catalog.deleteProduct(UUID.randomUUID(),empty,actor)).isEqualTo(empty);
        assertThat(queries.list("products",0,20,"Descartável","","",null).total()).isZero();
        product(0);
        catalog.renameCategory(UUID.randomUUID(),category,"Mercearia",actor);
        assertThat(queries.list("products",0,20,"Mercearia","","",null).total()).isEqualTo(1);
    }
    UUID sale(UUID product,long quantity,String payment){return sales.create(UUID.randomUUID(),new SalesService.NewSale(customer,List.of(new SalesService.Item(product,quantity,new BigDecimal("20.00"))),new BigDecimal(payment)),actor);}
    long quantity(UUID id){return db.queryForObject("SELECT quantity FROM stock WHERE product_id=?",Long.class,id);}
    BigDecimal cashBalance(){return db.queryForObject("SELECT coalesce(sum(amount),0) FROM cash_entry",BigDecimal.class);}
    List<Boolean> race(Runnable a,Runnable b)throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Boolean>>();for(var action:List.of(a,b))futures.add(executor.submit(()->{ready.countDown();start.await();try{action.run();return true;}catch(BusinessException e){return false;}}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        }
    }
    @Test void concurrentDeductionsNeverOversell()throws Exception {
        var p=product(10);var a=sale(p,7,"0");var b=sale(p,7,"0");
        assertThat(race(()->sales.deliver(UUID.randomUUID(),a,actor),()->sales.deliver(UUID.randomUUID(),b,actor))).containsExactlyInAnyOrder(true,false);
        assertThat(quantity(p)).isEqualTo(3);assertThat(cashBalance()).isZero();
    }
    @Test void concurrentIdenticalRetriesAndConflictingPayload()throws Exception {
        var p=product(0);var key=UUID.randomUUID();var input=new InventoryService.Adjustment(p,10,new BigDecimal("12.00"),"INBOUND","Compra");
        assertThat(race(()->stock.adjust(key,input,actor),()->stock.adjust(key,input,actor))).containsOnly(true);
        assertThat(quantity(p)).isEqualTo(10);
        assertThat(db.queryForObject("SELECT count(*) FROM stock_movement",Long.class)).isEqualTo(1);
        assertThatThrownBy(()->stock.adjust(key,new InventoryService.Adjustment(p,11,new BigDecimal("12.00"),"INBOUND","Compra"),actor)).isInstanceOf(BusinessException.class);
    }
    @Test void retryAfterLostResponseReturnsSameOutcome(){
        var p=product(10);var s=sale(p,4,"0");var key=UUID.randomUUID();
        UUID first=sales.deliver(key,s,actor); // Response intentionally discarded by simulated client.
        assertThat(sales.deliver(key,s,actor)).isEqualTo(first);assertThat(quantity(p)).isEqualTo(6);
        assertThatThrownBy(()->sales.deliver(UUID.randomUUID(),s,actor)).isInstanceOf(BusinessException.class);
    }
    @Test void multiItemDeliveryRollsBackAllItems(){
        var p1=product(10);var p2=product(0);var s=sales.create(UUID.randomUUID(),new SalesService.NewSale(customer,List.of(new SalesService.Item(p1,3,new BigDecimal("20")),new SalesService.Item(p2,1,new BigDecimal("20"))),BigDecimal.ZERO),actor);
        assertThatThrownBy(()->sales.deliver(UUID.randomUUID(),s,actor)).isInstanceOf(BusinessException.class);
        assertThat(quantity(p1)).isEqualTo(10);assertThat(quantity(p2)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM stock_movement WHERE sale_id=?",Long.class,s)).isZero();
    }
    @Test void failureAfterMutationRollsBackBalanceAndReceipt(){
        var p=product(4);var key=UUID.randomUUID();
        assertThatThrownBy(()->commands.execute("test.failure",key,p,actor,()->{stock.deliver(p,2,null,actor);throw new IllegalStateException("simulated failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(quantity(p)).isEqualTo(4);assertThat(db.queryForObject("SELECT count(*) FROM command_receipt WHERE key=?",Long.class,key)).isZero();
    }
    @Test void paymentDeliveryAndReversalAreIndependent(){
        var p=product(10);var s=sale(p,5,"20");assertThat(quantity(p)).isEqualTo(10);assertThat(cashBalance()).isEqualByComparingTo("20");
        sales.deliver(UUID.randomUUID(),s,actor);assertThat(quantity(p)).isEqualTo(5);assertThat(queries.dashboard()).doesNotContainKeys("month_margin","forecast_margin","month_reimbursements");
        var payment=sales.payment(UUID.randomUUID(),s,new BigDecimal("80"),actor);assertThat(cashBalance()).isEqualByComparingTo("100");
        assertThat(queries.dashboard()).doesNotContainKeys("month_margin","forecast_margin","month_reimbursements");
        cash.reverse(UUID.randomUUID(),payment,"Pagamento lançado incorretamente",actor);assertThat(cashBalance()).isEqualByComparingTo("20");assertThat(quantity(p)).isEqualTo(5);
        assertThat(db.queryForObject("SELECT paid FROM sale WHERE id=?",BigDecimal.class,s)).isEqualByComparingTo("20");
    }
    @Test void dashboardProfitUsesOnlyDeliveredAndFullyPaidSalesInCurrentMonth(){
        var p=product(10);var completed=sale(p,2,"0");sales.deliver(UUID.randomUUID(),completed,actor);sales.payment(UUID.randomUUID(),completed,new BigDecimal("40"),actor);
        var credit=sale(p,1,"0");sales.deliver(UUID.randomUUID(),credit,actor);
        stock.adjust(UUID.randomUUID(),new InventoryService.Adjustment(p,2,BigDecimal.ZERO,"DAMAGE","Dano de teste"),actor);
        var dashboard=queries.dashboard();
        assertThat((BigDecimal)dashboard.get("month_realized_profit")).isEqualByComparingTo("20");
        assertThat(((Number)dashboard.get("month_outbound")).longValue()).isEqualTo(-3);
        assertThat(((Number)dashboard.get("month_damage")).longValue()).isEqualTo(-2);
        var daily=(List<Map<String,Object>>)dashboard.get("daily_profit");
        assertThat(daily).hasSize(LocalDate.now(ZoneId.of("America/Sao_Paulo")).getDayOfMonth());
        assertThat(daily.stream().map(x->(BigDecimal)x.get("gross_profit")).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("20");
    }
    @Test void duplicateAndConcurrentPaymentsCannotOverpay()throws Exception {
        var s=sale(product(5),5,"0");var key=UUID.randomUUID();
        assertThat(race(()->sales.payment(key,s,new BigDecimal("60"),actor),()->sales.payment(key,s,new BigDecimal("60"),actor))).containsOnly(true);
        assertThat(cashBalance()).isEqualByComparingTo("60");
        assertThat(race(()->sales.payment(UUID.randomUUID(),s,new BigDecimal("40"),actor),()->sales.payment(UUID.randomUUID(),s,new BigDecimal("40"),actor))).containsExactlyInAnyOrder(true,false);
        assertThat(cashBalance()).isEqualByComparingTo("100");
    }
    @Test void addressAndHistoricCostsAreSnapshots(){
        var p=product(10);var s=sale(p,2,"40");sales.deliver(UUID.randomUUID(),s,actor);
        var c=customers.get(customer);customers.save(UUID.randomUUID(),new Customer(c.id(),c.name(),"","","",new Customer.Address("Rua Liberdade - 890","","Centro","Aracaju-SE","","","",""),c.version()),false,actor);
        assertThat(customers.get(customer).address().postalCode()).isEmpty();
        stock.adjust(UUID.randomUUID(),new InventoryService.Adjustment(p,10,new BigDecimal("30"),"INBOUND","Reposição"),actor);
        assertThat(queries.sale(s).get("address").toString()).contains("Rua Teste");
        assertThat(db.queryForObject("SELECT delivered_cost FROM sale_line WHERE sale_id=?",BigDecimal.class,s)).isEqualByComparingTo("10");
    }
    @Test void databasePreventsTamperingAndNegativeStock(){
        var p=product(2);
        assertThatThrownBy(()->db.update("UPDATE stock SET quantity=-1 WHERE product_id=?",p)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("DELETE FROM stock_movement WHERE product_id=?",p)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        cash.manual(UUID.randomUUID(),new CashService.Manual(new BigDecimal("10"),"CONTRIBUTION","Aporte",null),actor);
        assertThatThrownBy(()->db.update("UPDATE cash_entry SET amount=20")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void reimbursementIsOutsideCurrentScope(){
        var p=product(4);var damage=stock.adjust(UUID.randomUUID(),new InventoryService.Adjustment(p,2,null,"DAMAGE","Quebra"),actor);
        assertThatThrownBy(()->cash.manual(UUID.randomUUID(),new CashService.Manual(new BigDecimal("15"),"REIMBURSEMENT","Fornecedor ressarciu",damage),actor)).isInstanceOf(BusinessException.class);
        assertThat(quantity(p)).isEqualTo(2);assertThat(cashBalance()).isZero();
    }
    @Test void archivePreservesBalanceAndActor(){
        db.update("INSERT INTO cash_entry(id,amount,category,description,actor,created_at) VALUES (?,100,'CONTRIBUTION','Aporte antigo',?,now()-interval '3 years')",UUID.randomUUID(),actor);
        cash.manual(UUID.randomUUID(),new CashService.Manual(new BigDecimal("25"),"EXPENSE","Frete atual",null),actor);
        assertThat(cashBalance()).isEqualByComparingTo("75");
        var archived=queries.list("cash",0,20,"","archive","",null);
        assertThat(archived.total()).isEqualTo(1);assertThat(archived.items().getFirst().get("actor")).isEqualTo(actor);
        assertThat(queries.list("cash",0,20,"","","",null).total()).isEqualTo(1);
    }
    @Test void staleProductUpdateAndDeactivationPreserveHistory(){
        var id=product(3);var changed=new Product(id,"Editado",null,"",category,UNIT,new BigDecimal("25"),new BigDecimal("10"),1,false,0);
        catalog.save(UUID.randomUUID(),changed,false,actor);
        assertThatThrownBy(()->catalog.save(UUID.randomUUID(),changed,false,actor)).isInstanceOf(BusinessException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id=?",Long.class,id)).isEqualTo(1);
        assertThatThrownBy(()->stock.adjust(UUID.randomUUID(),new InventoryService.Adjustment(id,1,BigDecimal.ONE,"INBOUND","Teste"),actor)).isInstanceOf(BusinessException.class);
    }
    MockHttpSession login()throws Exception {
        var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("username","tester","password","local-test-password","otp",Totp.code(secret,Instant.now().getEpochSecond()/30))))).andExpect(status().isOk()).andReturn();
        return (MockHttpSession)result.getRequest().getSession();
    }
    @Test void apiRequiresSessionCsrfAndTwoFactors()throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"tester\",\"password\":\"local-test-password\",\"otp\":\"000000\"}")).andExpect(status().isUnauthorized());
        var session=login();mvc.perform(get("/api/products").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/categories").session(session).contentType("application/json").content("{\"name\":\"Test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("username","tester","password","local-test-password","otp",Totp.code(secret,Instant.now().getEpochSecond()/30))))).andExpect(status().isUnauthorized());
    }
    @Test void apiValidationIdempotencyAndOpenApi()throws Exception {
        var session=login();String key=UUID.randomUUID().toString();
        var first=mvc.perform(post("/api/categories").session(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content("{\"name\":\"Nova\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/categories").session(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content("{\"name\":\"Nova\"}")).andExpect(status().isOk()).andExpect(content().json(first));
        mvc.perform(post("/api/categories").session(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content("{\"name\":\"Outra\"}")).andExpect(status().isConflict());
        mvc.perform(get("/v3/api-docs").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/customers?size=1000").session(session)).andExpect(status().isConflict());
    }
    @Test void databaseProvisioningAndFirstLoginRequireConfirmedTotp()throws Exception {
        UUID id=db.queryForObject("SELECT provision_internal_user(?,?)",UUID.class,"Owner@Example.test","chosen-test-password");
        assertThat(db.queryForObject("SELECT username FROM app_user WHERE id=?",String.class,id)).isEqualTo("owner@example.test");
        assertThat(db.queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,id)).startsWith("$2a$");
        mvc.perform(get("/api/auth/enrollment")).andExpect(status().isUnauthorized());
        var start=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"owner@example.test\",\"password\":\"chosen-test-password\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENROLLMENT_REQUIRED")).andReturn();
        var session=(MockHttpSession)start.getRequest().getSession();
        mvc.perform(get("/api/products").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        String response=mvc.perform(get("/api/auth/enrollment").session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        var enrollment=json.readTree(response);String seed=enrollment.get("secret").asText();
        byte[] png=Base64.getDecoder().decode(enrollment.get("qrDataUrl").asText().split(",",2)[1]);
        var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        var source=new com.google.zxing.RGBLuminanceSource(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));
        String decoded=new com.google.zxing.qrcode.QRCodeReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source))).getText();
        assertThat(decoded).isEqualTo(EnrollmentQr.uri("owner@example.test",seed));
        String invalid=String.format("%06d",(Integer.parseInt(Totp.code(seed,Instant.now().getEpochSecond()/30))+9)%1_000_000);
        mvc.perform(post("/api/auth/confirm").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("otp",invalid)))).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("SELECT totp_cipher FROM app_user WHERE id=?",String.class,id)).isNull();
        String code=Totp.code(seed,Instant.now().getEpochSecond()/30);
        mvc.perform(post("/api/auth/confirm").session(session).contentType("application/json").content(json.writeValueAsBytes(Map.of("otp",code)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/confirm").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("otp",code)))).andExpect(status().isOk());
        mvc.perform(get("/api/products").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/enrollment").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/confirm").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("otp",code)))).andExpect(status().isUnauthorized());
        assertThat(cipher.decrypt(db.queryForObject("SELECT totp_cipher FROM app_user WHERE id=?",String.class,id))).isEqualTo(seed);
        assertThat(db.queryForObject("SELECT count(*) FROM audit_entry WHERE actor=? AND action='auth.totp.enroll'",Long.class,id)).isEqualTo(1);
        var next=accounts.begin("owner@example.test","chosen-test-password","127.0.0.1").orElseThrow();
        assertThat(next.enrollment()).isFalse();assertThat(accounts.complete(next,code,"127.0.0.1")).isEmpty();
    }
    @Test void expiredChangedPasswordAndCompetingEnrollmentCannotTakeOver(){
        UUID id=db.queryForObject("SELECT provision_internal_user(?,?)",UUID.class,"brother@example.test","chosen-test-password");
        var first=accounts.begin("brother@example.test","chosen-test-password","127.0.0.1").orElseThrow();
        var second=accounts.begin("brother@example.test","chosen-test-password","127.0.0.1").orElseThrow();
        var expired=new Accounts.Challenge(first.id(),first.username(),first.passwordHash(),first.pendingCipher(),Instant.now().minusSeconds(1));
        String seed=accounts.enrollmentSecret(first).orElseThrow();
        assertThat(accounts.enrollmentSecret(expired)).isEmpty();assertThat(accounts.complete(expired,Totp.code(seed,Instant.now().getEpochSecond()/30),"127.0.0.1")).isEmpty();
        assertThat(accounts.complete(first,Totp.code(seed,Instant.now().getEpochSecond()/30),"127.0.0.1")).isPresent();
        assertThat(accounts.enrollmentSecret(second)).isEmpty();
        assertThat(accounts.complete(second,Totp.code(cipher.decrypt(second.pendingCipher()),Instant.now().getEpochSecond()/30),"127.0.0.1")).isEmpty();
        var challenge=accounts.begin("brother@example.test","chosen-test-password","127.0.0.1").orElseThrow();
        db.update("UPDATE app_user SET password_hash=? WHERE id=?",new BCryptPasswordEncoder(4).encode("changed-password"),id);
        assertThat(accounts.complete(challenge,Totp.code(seed,Instant.now().getEpochSecond()/30+1),"127.0.0.1")).isEmpty();
    }
    @Test void enrollmentConfirmationIsRateLimited(){
        db.queryForObject("SELECT provision_internal_user(?,?)",UUID.class,"limited@example.test","chosen-test-password");
        var challenge=accounts.begin("limited@example.test","chosen-test-password","127.0.0.1").orElseThrow();
        for(int i=0;i<12;i++)assertThat(accounts.complete(challenge,"invalid","127.0.0.1")).isEmpty();
        String seed=cipher.decrypt(challenge.pendingCipher());
        assertThat(accounts.complete(challenge,Totp.code(seed,Instant.now().getEpochSecond()/30),"127.0.0.1")).isEmpty();
    }
}
