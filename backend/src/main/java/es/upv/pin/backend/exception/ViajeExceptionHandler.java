package es.upv.pin.backend.exception;

import es.upv.pin.backend.controller.ViajeController;
import es.upv.pin.backend.controller.EdicionItinerarioController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {ViajeController.class, EdicionItinerarioController.class})
public class ViajeExceptionHandler {

    @ExceptionHandler(RevisionViajeException.class)
    public ResponseEntity<Map<String, Object>> revisionDesactualizada(RevisionViajeException error) {
        return respuesta(409, "REVISION_DESACTUALIZADA", error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> camposInvalidos(
            MethodArgumentNotValidException error
    ) {
        Map<String, String> campos = new LinkedHashMap<>();

        error.getBindingResult().getFieldErrors().forEach(
                campo -> campos.putIfAbsent(
                        campo.getField(),
                        campo.getDefaultMessage()
                )
        );

        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "codigo", "DATOS_INVALIDOS",
                        "mensaje", "Revisa los campos indicados.",
                        "errores", campos
                ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, Object>> formatoInvalido() {
        return respuesta(
                400,
                "FORMATO_INVALIDO",
                "Envía un identificador y unos datos de viaje válidos."
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> resultadoNoDisponible(
            ResponseStatusException error
    ) {
        String codigo = error.getStatusCode().value() == 404
                ? "VIAJE_NO_ENCONTRADO"
                : "RESULTADO_NO_DISPONIBLE";

        return respuesta(
                error.getStatusCode().value(),
                codigo,
                error.getReason() == null
                        ? "El recurso solicitado no está disponible."
                        : error.getReason()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> datosInvalidos(
            IllegalArgumentException error
    ) {
        return respuesta(
                400,
                "DATOS_INVALIDOS",
                error.getMessage() == null
                        ? "Revisa los datos del viaje."
                        : error.getMessage()
        );
    }

    @ExceptionHandler({
            DataAccessException.class,
            TransactionException.class
    })
    public ResponseEntity<Map<String, Object>> falloPersistencia(
            HttpServletRequest peticion
    ) {
        boolean consulta = "GET".equals(peticion.getMethod())
                || "HEAD".equals(peticion.getMethod());

        if (consulta) {
            return respuesta(
                    503,
                    "CONSULTA_NO_DISPONIBLE",
                    "No hemos podido consultar tus viajes. "
                            + "Vuelve a intentarlo más tarde."
            );
        }

        return respuesta(
                503,
                "GUARDADO_NO_CONFIRMADO",
                "No hemos podido confirmar el guardado. "
                        + "Comprueba Mis viajes antes de volver a intentarlo."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> falloInesperado() {
        return respuesta(
                500,
                "ERROR_OPERACION_VIAJE",
                "No hemos podido completar la operación del viaje."
        );
    }

    private static ResponseEntity<Map<String, Object>> respuesta(
            int estado,
            String codigo,
            String mensaje
    ) {
        return ResponseEntity.status(estado)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "codigo", codigo,
                        "mensaje", mensaje
                ));
    }
}
