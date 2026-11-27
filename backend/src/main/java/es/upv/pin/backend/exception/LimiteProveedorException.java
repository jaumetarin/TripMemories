package es.upv.pin.backend.exception;

import org.springframework.http.HttpStatus;

public class LimiteProveedorException extends ControlGeneracionException {
    public LimiteProveedorException(long espera) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Has alcanzado el límite temporal de llamadas a proveedores. Inténtalo más tarde.", espera);
    }
}
