package es.upv.pin.backend.model;

import java.util.UUID;

/** Modelo interno. Nunca se devuelve directamente desde un controlador. */
public class Usuario {
    private final UUID id;
    private final String nombre;
    private final String correo;
    private final String passwordHash;
    private final boolean correoVerificado;

    public Usuario(UUID id, String nombre, String correo, String passwordHash, boolean correoVerificado) {
        this.id = id;
        this.nombre = nombre;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.correoVerificado = correoVerificado;
    }
    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isCorreoVerificado() { return correoVerificado; }
}
