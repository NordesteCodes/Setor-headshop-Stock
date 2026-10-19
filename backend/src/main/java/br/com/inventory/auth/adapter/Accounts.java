package br.com.inventory.auth.adapter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Component
public class Accounts implements ApplicationRunner {
    private final JdbcTemplate db;private final SecretCipher cipher;private final Environment env;private final TransactionTemplate tx;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final String dummy=passwords.encode(UUID.randomUUID().toString());
    public Accounts(JdbcTemplate db,SecretCipher cipher,Environment env,TransactionTemplate tx){this.db=db;this.cipher=cipher;this.env=env;this.tx=tx;}
    public void run(ApplicationArguments args){
        String username=env.getProperty("BOOTSTRAP_USERNAME","").strip().toLowerCase(Locale.ROOT);
        if(username.isEmpty())return;
        if(db.queryForObject("SELECT count(*) FROM app_user WHERE username=?",Long.class,username)>0)return;
        String password=env.getProperty("BOOTSTRAP_PASSWORD","");String secret=env.getProperty("BOOTSTRAP_TOTP_SECRET","");
        if(!username.matches("[a-z0-9._@-]{3,80}") || password.length()<14 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || (!secret.isEmpty() && !secret.matches("[A-Z2-7]{32}")))throw new IllegalArgumentException("Bootstrap requer usuário válido, senha de 14 a 72 bytes e segredo TOTP opcional em Base32.");
        db.update("INSERT INTO app_user(id,username,password_hash,totp_cipher) VALUES (?,?,?,?) ON CONFLICT(username) DO NOTHING",UUID.randomUUID(),username,passwords.encode(password),secret.isEmpty()?null:cipher.encrypt(secret));
    }
    public record Identity(UUID id,String username) implements java.io.Serializable {}
    /** Server-side password proof, valid only for five minutes; never serialized into an API response. */
    public record Challenge(UUID id,String username,String passwordHash,String pendingCipher,Instant expiresAt) implements java.io.Serializable {
        public boolean enrollment(){return pendingCipher!=null;}
        public boolean valid(){return Instant.now().isBefore(expiresAt);}
    }
    public Optional<Challenge> begin(String username,String password,String ip){
        String normalized=username.strip().toLowerCase(Locale.ROOT);
        return tx.execute(status->{
            if(!allowAttempt(normalized,ip))return Optional.empty();
            var users=db.queryForList("SELECT * FROM app_user WHERE lower(username)=? AND active=true FOR UPDATE",normalized);
            String hash=users.isEmpty()?dummy:(String)users.getFirst().get("password_hash");
            if(!passwords.matches(password,hash)||users.isEmpty())return Optional.empty();
            var row=users.getFirst();String pending=null;
            if(row.get("totp_cipher")==null){
                byte[] bytes=new byte[20];new java.security.SecureRandom().nextBytes(bytes);
                pending=cipher.encrypt(new org.apache.commons.codec.binary.Base32().encodeToString(bytes));
            }
            return Optional.of(new Challenge((UUID)row.get("id"),(String)row.get("username"),hash,pending,Instant.now().plusSeconds(300)));
        });
    }
    public Optional<String> enrollmentSecret(Challenge challenge){
        if(!challenge.valid() || !challenge.enrollment())return Optional.empty();
        Long count=db.queryForObject("SELECT count(*) FROM app_user WHERE id=? AND active=true AND password_hash=? AND totp_cipher IS NULL",Long.class,challenge.id(),challenge.passwordHash());
        return count==1?Optional.of(cipher.decrypt(challenge.pendingCipher())):Optional.empty();
    }
    public Optional<Identity> complete(Challenge challenge,String otp,String ip){
        if(!challenge.valid())return Optional.empty();
        return tx.execute(status->{
            if(!allowAttempt(challenge.username(),ip))return Optional.empty();
            var rows=db.queryForList("SELECT * FROM app_user WHERE id=? AND active=true AND password_hash=? FOR UPDATE",challenge.id(),challenge.passwordHash());
            if(rows.isEmpty())return Optional.empty();
            var row=rows.getFirst();boolean unconfigured=row.get("totp_cipher")==null;
            if(unconfigured!=challenge.enrollment())return Optional.empty();
            String encrypted=unconfigured?challenge.pendingCipher():(String)row.get("totp_cipher");
            long step=Totp.verify(cipher.decrypt(encrypted),otp,Instant.now().getEpochSecond()/30,((Number)row.get("last_totp_step")).longValue());
            if(step<0)return Optional.empty();
            db.update("UPDATE app_user SET totp_cipher=?,last_totp_step=? WHERE id=?",encrypted,step,challenge.id());
            if(unconfigured)db.update("INSERT INTO audit_entry(id,actor,action,resource_id) VALUES (?,?,'auth.totp.enroll',?)",UUID.randomUUID(),challenge.id(),challenge.id());
            recordLogin(challenge.id(),challenge.username());
            return Optional.of(new Identity(challenge.id(),challenge.username()));
        });
    }
    private void recordLogin(UUID id,String username){
        db.update("UPDATE auth_attempt SET attempts=0 WHERE bucket=?","user:"+username.toLowerCase(Locale.ROOT));
        db.update("INSERT INTO audit_entry(id,actor,action,resource_id) VALUES (?,?,'auth.login',?)",UUID.randomUUID(),id,id);
    }
    private boolean allowAttempt(String username,String ip){
        for(String bucket:List.of("ip:"+ip,"user:"+username.toLowerCase(Locale.ROOT))){
            db.update("INSERT INTO auth_attempt(bucket,attempts,window_start) VALUES (?,0,now()) ON CONFLICT DO NOTHING",bucket);
            var row=db.queryForMap("SELECT attempts,window_start FROM auth_attempt WHERE bucket=? FOR UPDATE",bucket);
            var start=((java.sql.Timestamp)row.get("window_start")).toInstant();
            if(start.isBefore(Instant.now().minusSeconds(900)))db.update("UPDATE auth_attempt SET attempts=0,window_start=now() WHERE bucket=?",bucket);
            else if(((Number)row.get("attempts")).intValue()>=(bucket.startsWith("ip:")?50:10))return false;
            db.update("UPDATE auth_attempt SET attempts=attempts+1 WHERE bucket=?",bucket);
        }
        return true;
    }
    public Optional<Identity> authenticate(String username,String password,String otp,String ip){
        String normalized=username.strip().toLowerCase(Locale.ROOT);
        return tx.execute(status->{
            if(!allowAttempt(normalized,ip))return Optional.empty();
            var users=db.queryForList("SELECT * FROM app_user WHERE lower(username)=? AND active=true FOR UPDATE",normalized);
            String hash=users.isEmpty()?dummy:(String)users.getFirst().get("password_hash");
            if(!passwords.matches(password,hash)||users.isEmpty())return Optional.empty();
            var user=users.getFirst();if(user.get("totp_cipher")==null)return Optional.empty();
            long step=Totp.verify(cipher.decrypt((String)user.get("totp_cipher")),otp,Instant.now().getEpochSecond()/30,((Number)user.get("last_totp_step")).longValue());
            if(step<0)return Optional.empty();
            UUID id=(UUID)user.get("id");db.update("UPDATE app_user SET last_totp_step=? WHERE id=?",step,id);
            recordLogin(id,normalized);
            return Optional.of(new Identity(id,normalized));
        });
    }
}
