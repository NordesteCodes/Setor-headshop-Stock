package br.com.inventory.sales.adapter.postgres;
import br.com.inventory.sales.application.SaleRepository;
import br.com.inventory.sales.domain.Sale;
import br.com.inventory.customers.domain.Customer.Address;
import br.com.inventory.shared.adapter.postgres.Rows;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class JdbcSales implements SaleRepository {
    private final JdbcTemplate db;private final ObjectMapper json;
    public JdbcSales(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    public Sale lock(UUID id){
        return Rows.one(db.query("SELECT * FROM sale WHERE id=? FOR UPDATE",(r,n)->{
            Address a;try{a=json.readValue(r.getString("address"),Address.class);}catch(Exception e){throw new IllegalStateException(e);}
            var lines=db.query("SELECT * FROM sale_line WHERE sale_id=? ORDER BY product_id",(s,i)->new Sale.Line(s.getObject("product_id",UUID.class),s.getString("product_name"),s.getLong("quantity"),s.getBigDecimal("price"),s.getBigDecimal("delivered_cost")),id);
            var delivery=r.getTimestamp("delivered_at");
            return new Sale(id,r.getObject("customer_id",UUID.class),r.getString("customer_name"),a,r.getBigDecimal("total"),r.getBigDecimal("paid"),delivery==null?null:delivery.toInstant(),lines);
        },id));
    }
    public void create(Sale s,UUID actor){
        String address;try{address=json.writeValueAsString(s.address());}catch(Exception e){throw new IllegalStateException(e);}
        db.update("INSERT INTO sale(id,customer_id,customer_name,address,total,actor) VALUES (?,?,?,?::jsonb,?,?)",s.id(),s.customerId(),s.customerName(),address,s.total(),actor);
        for(var l:s.lines()) db.update("INSERT INTO sale_line(sale_id,product_id,product_name,quantity,price) VALUES (?,?,?,?,?)",s.id(),l.productId(),l.productName(),l.quantity(),l.price());
    }
    public void addPayment(UUID id,BigDecimal amount){db.update("UPDATE sale SET paid=paid+?,settled_at=CASE WHEN paid+?=total THEN now() ELSE NULL END WHERE id=?",amount,amount,id);}
    public void delivered(UUID id){db.update("UPDATE sale SET delivered_at=now() WHERE id=?",id);}
    public void lineCost(UUID sale,UUID product,BigDecimal cost){db.update("UPDATE sale_line SET delivered_cost=? WHERE sale_id=? AND product_id=?",cost,sale,product);}
}
