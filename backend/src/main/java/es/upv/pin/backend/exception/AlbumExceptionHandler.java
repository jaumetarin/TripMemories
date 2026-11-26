package es.upv.pin.backend.exception;

import es.upv.pin.backend.controller.AlbumController;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = AlbumController.class)
public class AlbumExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> operacionRechazada(ResponseStatusException error) {
        return respuesta(error.getStatusCode().value(), error.getReason() == null
                ? "No se puede completar la operación del álbum." : error.getReason());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> datosInvalidos(IllegalArgumentException error) {
        return respuesta(400, error.getMessage() == null ? "Revisa los datos de la foto." : error.getMessage());
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String,String>> peticionIncompleta() {
        return respuesta(400,"Selecciona una foto y un álbum válidos.");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Map<String,String>> almacenamientoNoConfirmado() {
        return respuesta(503,"No se ha podido confirmar la operación. Actualiza el álbum para comprobar su estado antes de reintentar.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> inesperado() {
        return respuesta(500,"No se ha podido completar la operación. Actualiza el álbum antes de reintentar.");
    }

    private static ResponseEntity<Map<String,String>> respuesta(int estado,String mensaje) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore()).body(Map.of("mensaje",mensaje));
    }
}
