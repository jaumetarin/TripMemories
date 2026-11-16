package es.upv.pin.backend.exception;

import org.springframework.http.HttpStatus;

public class ControlGeneracionException extends RuntimeException {
    private final HttpStatus estado;
    private final long esperaSegundos;

    public ControlGeneracionException(HttpStatus estado, String mensaje, long esperaSegundos) {
        super(mensaje);
        this.estado = estado;
        this.esperaSegundos = esperaSegundos;
    }

    public HttpStatus getEstado() { return estado; }
    public long getEsperaSegundos() { return esperaSegundos; }
}
