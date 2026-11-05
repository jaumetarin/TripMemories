package es.upv.pin.backend.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.upv.pin.backend.controller.CandidatoController;
import es.upv.pin.backend.controller.PreparacionCandidatosController;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = {CandidatoController.class, PreparacionCandidatosController.class, es.upv.pin.backend.controller.EvaluacionCandidatosController.class})
public class CandidatoExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> formatoInvalido() {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Envía una configuración de viaje válida para buscar candidatos."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> consultaInvalida(
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
