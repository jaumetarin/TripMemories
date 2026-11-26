package es.upv.pin.backend.exception;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/** También atiende rechazos del contenedor antes de que se resuelva el controlador. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CargaFotosExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,String>> demasiadoGrande() {
        return ResponseEntity.status(413).cacheControl(CacheControl.noStore()).body(Map.of("mensaje","La foto o la solicitud supera el tamaño permitido. Elige una imagen más pequeña."));
    }
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Map<String,String>> invalida() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("mensaje","No se ha podido leer la subida. Selecciona una sola imagen y reintenta."));
    }
}
