package br.com.inventory.customers.adapter.postgres;
import br.com.inventory.customers.application.CustomerRepository;
import br.com.inventory.customers.domain.Customer;
import br.com.inventory.shared.adapter.postgres.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class JdbcCustomers implements CustomerRepository {
    private final JdbcTemplate db; public JdbcCustomers(JdbcTemplate db){this.db=db;}
    public Customer customer(UUID id){return Rows.one(db.query("SELECT * FROM customer WHERE id=?",(r,n)->new Customer(id,r.getString("name"),r.getString("document"),r.getString("phone"),r.getString("email"),new Customer.Address(r.getString("street"),r.getString("number"),r.getString("district"),r.getString("city"),r.getString("state"),r.getString("postal_code"),r.getString("complement"),r.getString("reference")),r.getLong("version")),id));}
    public void create(Customer c){var a=c.address();db.update("INSERT INTO customer(id,name,document,phone,email,street,number,district,city,state,postal_code,complement,reference) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",c.id(),c.name(),c.document(),c.phone(),c.email(),a.street(),a.number(),a.district(),a.city(),a.state(),a.postalCode().isBlank()?null:a.postalCode(),a.complement(),a.reference());}
    public void save(Customer c){var a=c.address();Rows.updated(db.update("UPDATE customer SET name=?,document=?,phone=?,email=?,street=?,number=?,district=?,city=?,state=?,postal_code=?,complement=?,reference=?,version=version+1 WHERE id=? AND version=?",c.name(),c.document(),c.phone(),c.email(),a.street(),a.number(),a.district(),a.city(),a.state(),a.postalCode().isBlank()?null:a.postalCode(),a.complement(),a.reference(),c.id(),c.version()));}
}
