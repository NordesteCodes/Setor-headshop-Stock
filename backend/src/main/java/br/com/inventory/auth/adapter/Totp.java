package br.com.inventory.auth.adapter;
import org.apache.commons.codec.binary.Base32;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Totp {
    private Totp(){}
    public static String code(String secret,long step){
        try {
            var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(new Base32().decode(secret),"HmacSHA1"));
            byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array()); int offset=hash[hash.length-1]&15;
            int number=ByteBuffer.wrap(hash,offset,4).getInt()&0x7fffffff;
            return String.format(java.util.Locale.ROOT,"%06d",number%1_000_000);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static long verify(String secret,String submitted,long current,long previous){
        if(submitted==null || !submitted.matches("[0-9]{6}"))return -1;
        for(long step=current-1;step<=current+1;step++)if(step>previous && MessageDigest.isEqual(code(secret,step).getBytes(StandardCharsets.US_ASCII),submitted.getBytes(StandardCharsets.US_ASCII)))return step;
        return -1;
    }
}
