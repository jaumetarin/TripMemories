package es.upv.pin.backend.exception;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LimiteProveedorExceptionHandler {
    @ExceptionHandler(LimiteProveedorException.class)
    public ResponseEntity<Map<String, String>> limite(LimiteProveedorException error) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(error.getEsperaSegundos()))
                .cacheControl(CacheControl.noStore()).body(Map.of("mensaje", error.getMessage()));
    }
}
