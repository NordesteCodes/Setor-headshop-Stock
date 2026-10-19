package br.com.inventory.finance.adapter.postgres;
import br.com.inventory.finance.application.CashRepository;
import br.com.inventory.shared.adapter.postgres.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class JdbcCash implements CashRepository {
    private final JdbcTemplate db;public JdbcCash(JdbcTemplate db){this.db=db;}
    public void append(Entry e,UUID actor){db.update("INSERT INTO cash_entry(id,amount,category,description,sale_id,damage_id,reversal_of,actor) VALUES (?,?,?,?,?,?,?,?)",e.id(),e.amount(),e.category(),e.description(),e.saleId(),e.damageId(),e.reversalOf(),actor);}
    public Entry lock(UUID id){return Rows.one(db.query("SELECT * FROM cash_entry WHERE id=? FOR UPDATE",(r,n)->new Entry(id,r.getBigDecimal("amount"),r.getString("category"),r.getString("description"),r.getObject("sale_id",UUID.class),r.getObject("damage_id",UUID.class),r.getObject("reversal_of",UUID.class)),id));}
    public boolean reversed(UUID id){return db.queryForObject("SELECT count(*) FROM cash_entry WHERE reversal_of=?",Long.class,id)>0;}
}
