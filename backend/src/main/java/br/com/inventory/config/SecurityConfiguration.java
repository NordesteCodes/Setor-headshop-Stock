package br.com.inventory.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityContextRepository securityContexts(){return new HttpSessionSecurityContextRepository();}
    @Bean SecurityFilterChain security(HttpSecurity http,SecurityContextRepository contexts)throws Exception {
        return http.authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/app.js","/style.css","/api/auth/csrf","/api/auth/session","/api/auth/login","/api/auth/enrollment","/api/auth/confirm","/actuator/health","/error").permitAll().anyRequest().authenticated())
            .securityContext(c->c.securityContextRepository(contexts))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable())
            .logout(c->c.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Entre para continuar.\"}");})
                .accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Sessão ou token expirado. Atualize a página.\"}");}))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
            .build();
    }
}
