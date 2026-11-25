package es.upv.pin.backend.service;

/** Evento interno efímero; no se registra ni se devuelve en una respuesta HTTP. */
public record CorreoVerificacion(String correo, String nombre, String enlace) {
    @Override public String toString() { return "CorreoVerificacion[datos reservados]"; }
}
