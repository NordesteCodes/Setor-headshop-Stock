package br.com.inventory;

import br.com.inventory.auth.adapter.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** Real cookies and requests across independent servers, not MockHttpSession. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistentSessionHttpTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    final ObjectMapper json=new ObjectMapper();
    final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
    ServletWebServerApplicationContext first,second;
    String csrfHeader,csrfToken;

    ServletWebServerApplicationContext start(){
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(InventoryApplication.class).run(
            "--server.port=0","--spring.datasource.url="+postgres.getJdbcUrl(),
            "--spring.datasource.username="+postgres.getUsername(),"--spring.datasource.password="+postgres.getPassword(),
            "--app.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "--server.servlet.session.cookie.secure=false");
    }
    @BeforeAll void startServers(){first=start();second=start();}
    @AfterAll void stopServers(){if(first!=null)first.close();if(second!=null)second.close();}
    HttpResponse<String> request(ServletWebServerApplicationContext context,String path,String body,boolean csrf)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+context.getWebServer().getPort()+path)).timeout(Duration.ofSeconds(20));
        if(body!=null){request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));if(csrf)request.header(csrfHeader,csrfToken);}
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode bootstrap(ServletWebServerApplicationContext context)throws Exception{
        var response=request(context,"/api/auth/session",null,false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var result=json.readTree(response.body());
        csrfHeader=result.path("csrf").path("header").asText();csrfToken=result.path("csrf").path("token").asText();
        assertThat(csrfToken).isNotBlank();return result;
    }
    @Test void enrollmentLoginAndLogoutSurviveInstanceChangesAndRestart()throws Exception{
        var db=first.getBean(JdbcTemplate.class);
        db.queryForObject("SELECT provision_internal_user(?,?)",java.util.UUID.class,"session-test@example.test","synthetic-session-password");
        assertThat(bootstrap(first).path("user").isNull()).isTrue();
        var login=json.writeValueAsString(Map.of("username","session-test@example.test","password","synthetic-session-password"));
        assertThat(request(first,"/api/auth/login",login,false).statusCode()).isEqualTo(403);
        var challenge=request(first,"/api/auth/login",login,true);
        assertThat(challenge.statusCode()).isEqualTo(200);
        assertThat(json.readTree(challenge.body()).path("status").asText()).isEqualTo("ENROLLMENT_REQUIRED");
        var enrollment=request(second,"/api/auth/enrollment",null,false);
        assertThat(enrollment.statusCode()).isEqualTo(200);
        var secret=json.readTree(enrollment.body()).path("secret").asText();
        assertThat(secret).isNotBlank();
        // A server that did not handle login must recover the same challenge after a restart.
        first.close();first=start();
        var recovered=request(first,"/api/auth/enrollment",null,false);
        assertThat(recovered.statusCode()).isEqualTo(200);
        assertThat(json.readTree(recovered.body()).path("secret").asText()).isEqualTo(secret);
        var code=Totp.code(secret,Instant.now().getEpochSecond()/30);
        var confirmation=json.writeValueAsString(Map.of("otp",code));
        assertThat(request(second,"/api/auth/confirm",confirmation,true).statusCode()).isEqualTo(200);
        assertThat(request(first,"/api/auth/me",null,false).statusCode()).isEqualTo(200);
        assertThat(bootstrap(second).path("user").path("username").asText()).isEqualTo("session-test@example.test");
        var before=System.nanoTime();
        var dashboard=request(first,"/api/dashboard",null,false);
        assertThat(dashboard.statusCode()).isEqualTo(200);
        var data=json.readTree(dashboard.body());
        assertThat(data.path("stock_quantity").asInt()).isZero();
        assertThat(data.path("month_realized_profit").decimalValue()).isZero();
        assertThat(data.path("daily_profit").size()).isEqualTo(LocalDate.now(ZoneId.of("America/Sao_Paulo")).getDayOfMonth());
        System.out.println("ISOLATED_EMPTY_DASHBOARD_MS="+Duration.ofNanos(System.nanoTime()-before).toMillis());
        assertThat(request(first,"/api/auth/confirm",confirmation,true).statusCode()).isEqualTo(401);
        assertThat(request(second,"/api/auth/logout","",true).statusCode()).isEqualTo(204);
        assertThat(request(first,"/api/auth/me",null,false).statusCode()).isEqualTo(401);
        assertThat(first.getBean(JdbcTemplate.class).queryForObject("SELECT count(*) FROM audit_entry WHERE action='auth.totp.enroll'",Long.class)).isEqualTo(1);
    }
}
