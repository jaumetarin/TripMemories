package es.upv.pin.backend.exception;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.upv.pin.backend.controller.PreferenciasController;

@RestControllerAdvice(assignableTypes = PreferenciasController.class)
public class PreferenciasExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> preferenciasInvalidas(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> formatoInvalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje",
                "Envía las listas con códigos del catálogo, un nivel de gasto válido y una distancia numérica en kilómetros."));
    }
}
