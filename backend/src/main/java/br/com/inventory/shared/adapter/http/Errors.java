package br.com.inventory.shared.adapter.http;
import br.com.inventory.shared.domain.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestControllerAdvice
public class Errors {
    private ResponseEntity<?> error(int status,String code,String message,HttpServletRequest req){return ResponseEntity.status(status).body(Map.of("code",code,"message",message,"correlationId",String.valueOf(req.getAttribute("correlationId"))));}
    @ExceptionHandler(BusinessException.class) ResponseEntity<?> business(BusinessException e,HttpServletRequest r){return error(e.code().equals("NOT_FOUND")?404:409,e.code(),e.getMessage(),r);}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> constraint(Exception e,HttpServletRequest r){return error(409,"DATA_CONFLICT","Registro duplicado, referência inválida ou operação incompatível com o histórico.",r);}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e,HttpServletRequest r){return error(e.getStatusCode().value(),"REQUEST_REJECTED",e.getReason()==null?"Solicitação rejeitada.":e.getReason(),r);}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    ResponseEntity<?> validation(Exception e,HttpServletRequest r){return error(400,"VALIDATION","Verifique os campos e o formato dos valores enviados.",r);}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e,HttpServletRequest r){org.slf4j.LoggerFactory.getLogger(Errors.class).error("request_failed correlationId={} type={}",r.getAttribute("correlationId"),e.getClass().getSimpleName());return error(500,"INTERNAL_ERROR","Não foi possível concluir. Repita a operação com a mesma chave.",r);}
}
