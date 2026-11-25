package es.upv.pin.backend.exception;

import es.upv.pin.backend.controller.AuthController;
import es.upv.pin.backend.service.AuthService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> credenciales() {
        return respuesta(401, Map.of("mensaje", "Correo o contraseña incorrectos. Si acabas de registrarte, verifica primero tu correo."));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> campos(MethodArgumentNotValidException error) {
        String ruta = error.getParameter().getMethod().getName();
        if ("login".equals(ruta)) return credenciales();
        if ("verificar".equals(ruta)) return respuesta(400, Map.of("mensaje", AuthService.ENLACE_INVALIDO));
        Map<String, String> campos = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return respuesta(400, Map.of("mensaje", "Revisa los campos indicados.", "errores", campos));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> entrada(IllegalArgumentException error) { return respuesta(400, Map.of("mensaje", error.getMessage())); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> formato() { return respuesta(400, Map.of("mensaje", "Envía los datos del formulario con un formato válido.")); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> limite(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore()).header("Retry-After", "900")
                .body(Map.of("mensaje", error.getReason()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> noDisponible(IllegalStateException error) {
        return respuesta(503, Map.of("mensaje", "El servicio de cuentas no está disponible temporalmente. Inténtalo más tarde."));
    }
    private static ResponseEntity<Map<String, Object>> respuesta(int codigo, Map<String, Object> datos) {
        return ResponseEntity.status(codigo).cacheControl(CacheControl.noStore()).body(datos);
    }
}
