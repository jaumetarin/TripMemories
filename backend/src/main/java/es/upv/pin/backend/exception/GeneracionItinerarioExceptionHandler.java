package es.upv.pin.backend.exception;

import java.util.Map;

import es.upv.pin.backend.controller.GeneracionItinerarioController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = GeneracionItinerarioController.class)
public class GeneracionItinerarioExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> configuracionInvalida(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", mensaje(error, "Configuracion invalida.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> formatoInvalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Envia una configuracion JSON valida."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> proveedorNoDisponible(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("mensaje", mensaje(error, "Servicio no disponible.")));
    }

    private String mensaje(RuntimeException error, String alternativo) {
        return error.getMessage() == null || error.getMessage().isBlank() ? alternativo : error.getMessage();
    }
}
