package br.com.inventory.auth.adapter;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Generated locally: the enrollment secret is never sent to a third-party QR service. */
public final class EnrollmentQr {
    private EnrollmentQr(){}
    public static String uri(String username,String secret){
        return "otpauth://totp/"+URLEncoder.encode("Estoque:"+username,StandardCharsets.UTF_8).replace("+","%20")
            +"?secret="+secret+"&issuer=Estoque&algorithm=SHA1&digits=6&period=30";
    }
    public static String png(String uri){
        try {
            var matrix=new QRCodeWriter().encode(uri,BarcodeFormat.QR_CODE,320,320);
            var image=new BufferedImage(320,320,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<320;y++)for(int x=0;x<320;x++)image.setRGB(x,y,matrix.get(x,y)?0x000000:0xffffff);
            var bytes=new ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(Exception e){throw new IllegalStateException("Não foi possível gerar o QR Code.",e);}
    }
}
