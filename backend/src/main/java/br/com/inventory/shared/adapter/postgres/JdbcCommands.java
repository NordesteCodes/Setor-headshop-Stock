package br.com.inventory.shared.adapter.postgres;
import br.com.inventory.shared.application.CommandExecutor;
import br.com.inventory.shared.domain.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

@Component
public class JdbcCommands implements CommandExecutor {
    private final JdbcTemplate db; private final TransactionTemplate tx; private final ObjectMapper json;
    public JdbcCommands(JdbcTemplate db,TransactionTemplate tx,ObjectMapper json){this.db=db;this.tx=tx;this.json=json;}
    public UUID execute(String scope,UUID key,Object payload,UUID actor,Supplier<UUID> action) {
        if(key==null) throw new BusinessException("VALIDATION","Idempotency-Key obrigatório.");
        final String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(payload))); }
        catch(Exception e){throw new IllegalStateException(e);}
        return tx.execute(status->{
            db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},scope+":"+key);
            var rows=db.queryForList("SELECT fingerprint,result,actor FROM command_receipt WHERE scope=? AND key=?",scope,key);
            if(!rows.isEmpty()) {
                var row=rows.getFirst();
                if(!fingerprint.equals(row.get("fingerprint")) || !actor.equals(row.get("actor")))
                    throw new BusinessException("IDEMPOTENCY_CONFLICT","Esta chave já foi usada para outra operação.");
                return (UUID)row.get("result");
            }
            UUID result=action.get();
            db.update("INSERT INTO command_receipt(scope,key,fingerprint,result,actor) VALUES (?,?,?,?,?)",scope,key,fingerprint,result,actor);
            db.update("INSERT INTO audit_entry(id,actor,action,resource_id) VALUES (?,?,?,?)",UUID.randomUUID(),actor,scope,result);
            return result;
        });
    }
}
