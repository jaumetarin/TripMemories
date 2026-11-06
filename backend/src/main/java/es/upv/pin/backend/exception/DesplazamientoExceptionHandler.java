package es.upv.pin.backend.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.upv.pin.backend.controller.DesplazamientoController;

@RestControllerAdvice(assignableTypes = DesplazamientoController.class)
public class DesplazamientoExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> formatoInvalido() {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "mensaje",
                        "Envía una lista válida de puntos con identificador, latitud y longitud."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> entradaInvalida(
            IllegalArgumentException error) {

        return ResponseEntity.badRequest()
                .body(Map.of("mensaje", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> servicioNoDisponible(
            IllegalStateException error) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("mensaje", error.getMessage()));
    }
}