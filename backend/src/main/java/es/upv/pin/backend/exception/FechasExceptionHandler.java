package es.upv.pin.backend.exception;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.upv.pin.backend.controller.FechasController;

@RestControllerAdvice(assignableTypes = FechasController.class)
public class FechasExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> fechasInvalidas(
            IllegalArgumentException error) {

        return ResponseEntity.badRequest()
                .body(Map.of("mensaje", error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> formatoInvalido() {

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "mensaje",
                        "Envía fechas válidas con el formato AAAA-MM-DD."));
    }
}
