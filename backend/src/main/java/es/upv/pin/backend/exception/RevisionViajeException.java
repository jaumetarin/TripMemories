package es.upv.pin.backend.exception;

public class RevisionViajeException extends RuntimeException {
    public RevisionViajeException() {
        super("El viaje ha cambiado desde que lo abriste. Recarga el viaje antes de guardar. Tus cambios pendientes se conservan en esta pantalla.");
    }
}
