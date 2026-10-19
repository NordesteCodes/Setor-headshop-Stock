package br.com.inventory.auth.adapter;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final Accounts accounts;private final SecurityContextRepository contexts;
    public AuthController(Accounts accounts,SecurityContextRepository contexts){this.accounts=accounts;this.contexts=contexts;}
    public record Login(@NotBlank @Size(max=254)String username,@NotBlank @Size(max=72)String password,@Pattern(regexp="[0-9]{6}")String otp){}
    public record Confirmation(@NotBlank @Pattern(regexp="[0-9]{6}")String otp){}
    private static final String PENDING="auth.pending";
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"header",token.getHeaderName());}
    @PostMapping("/login") public Object login(@Valid @RequestBody Login input,HttpServletRequest req,HttpServletResponse res){
        req.getSession().removeAttribute(PENDING);
        if(input.otp()==null){
            var challenge=accounts.begin(input.username(),input.password(),req.getRemoteAddr()).orElseThrow(this::invalid);
            req.changeSessionId();
            var empty=SecurityContextHolder.createEmptyContext();SecurityContextHolder.setContext(empty);contexts.saveContext(empty,req,res);
            req.getSession().setAttribute(PENDING,challenge);
            return Map.of("status",challenge.enrollment()?"ENROLLMENT_REQUIRED":"OTP_REQUIRED");
        }
        var user=accounts.authenticate(input.username(),input.password(),input.otp(),req.getRemoteAddr()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credenciais inválidas ou limite temporário atingido."));
        return signedIn(user,req,res);
    }
    @GetMapping("/enrollment") public Map<String,String> enrollment(HttpServletRequest req){
        var challenge=challenge(req);
        var secret=accounts.enrollmentSecret(challenge).orElseThrow(this::invalid);
        return Map.of("secret",secret,"qrDataUrl",EnrollmentQr.png(EnrollmentQr.uri(challenge.username(),secret)));
    }
    @PostMapping("/confirm") public Accounts.Identity confirm(@Valid @RequestBody Confirmation input,HttpServletRequest req,HttpServletResponse res){
        var user=accounts.complete(challenge(req),input.otp(),req.getRemoteAddr()).orElseThrow(this::invalid);
        req.getSession().removeAttribute(PENDING);
        return signedIn(user,req,res);
    }
    private Accounts.Challenge challenge(HttpServletRequest req){
        var session=req.getSession(false);
        if(session==null || !(session.getAttribute(PENDING) instanceof Accounts.Challenge c))throw invalid();
        if(!c.valid()){session.removeAttribute(PENDING);throw invalid();}
        return c;
    }
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credenciais ou código inválidos, sessão expirada ou limite temporário atingido.");}
    private Accounts.Identity signedIn(Accounts.Identity user,HttpServletRequest req,HttpServletResponse res){
        req.getSession();req.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("INTERNAL"))));
        SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);
        new HttpSessionCsrfTokenRepository().saveToken(null,req,res);
        return user;
    }
    @GetMapping("/me") public Accounts.Identity me(Authentication auth){return (Accounts.Identity)auth.getPrincipal();}
}
