package br.com.inventory.shared.adapter.http;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.annotation.Order;
import java.io.*;
import java.util.UUID;

@Component @Order(-200)
public class RequestFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
        String id=UUID.randomUUID().toString();req.setAttribute("correlationId",id);res.setHeader("X-Correlation-ID",id);
        if(req.getRequestURI().startsWith("/api/"))res.setHeader("Cache-Control","no-store");
        if(req.getContentLengthLong()>65536){res.setStatus(413);res.setContentType("application/json");res.getWriter().write("{\"code\":\"TOO_LARGE\",\"message\":\"Solicitação excede 64 KB.\"}");return;}
        HttpServletRequest bounded=req;
        if(req.getRequestURI().startsWith("/api/") && req.getContentType()!=null && req.getContentType().startsWith("application/json")){
            byte[] body=req.getInputStream().readNBytes(65537);
            if(body.length>65536){res.setStatus(413);res.setContentType("application/json");res.getWriter().write("{\"code\":\"TOO_LARGE\",\"message\":\"Solicitação excede 64 KB.\"}");return;}
            bounded=new HttpServletRequestWrapper(req){
                @Override public ServletInputStream getInputStream(){
                    var input=new ByteArrayInputStream(body);
                    return new ServletInputStream(){
                        public int read(){return input.read();}
                        public boolean isFinished(){return input.available()==0;}
                        public boolean isReady(){return true;}
                        public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous API");}
                    };
                }
                @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
            };
        }
        long started=System.nanoTime();
        try{chain.doFilter(bounded,res);}finally{
            org.slf4j.LoggerFactory.getLogger(RequestFilter.class).info("http_request correlationId={} method={} status={} durationMs={}",id,req.getMethod(),res.getStatus(),(System.nanoTime()-started)/1_000_000);
        }
    }
}
