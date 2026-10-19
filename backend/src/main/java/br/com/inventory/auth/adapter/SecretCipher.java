package br.com.inventory.auth.adapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretCipher {
    private final SecretKeySpec key;
    public SecretCipher(@Value("${app.encryption-key}")String encoded){
        byte[] bytes=Base64.getDecoder().decode(encoded);if(bytes.length!=32)throw new IllegalArgumentException("APP_ENCRYPTION_KEY deve conter 32 bytes em Base64.");key=new SecretKeySpec(bytes,"AES");
    }
    public String encrypt(String secret){try{
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
        byte[] result=cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+result.length).put(iv).put(result).array());
    }catch(Exception e){throw new IllegalStateException(e);}}
    public String decrypt(String secret){try{
        byte[] bytes=Base64.getDecoder().decode(secret);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,bytes,0,12));return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
    }catch(Exception e){throw new IllegalStateException("Não foi possível abrir o segredo de autenticação.",e);}}
}
