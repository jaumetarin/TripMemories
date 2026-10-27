package es.upv.pin.backend.exception;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.upv.pin.backend.controller.FechaHoraController;

@RestControllerAdvice(assignableTypes = FechaHoraController.class)
public class FechaHoraExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> disponibilidadInvalida(
            IllegalArgumentException error) {

        return ResponseEntity.badRequest()
                .body(Map.of("mensaje", error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> formatoInvalido() {

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "mensaje",
                        "Envía una fecha y hora válidas para llegada y salida, con el formato AAAA-MM-DDTHH:mm."));
    }
}
