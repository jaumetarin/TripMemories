package es.upv.pin.backend.dto;

import java.util.UUID;
import es.upv.pin.backend.model.UsuarioAutenticado;

public record UsuarioDto(UUID id, String nombre, String correo) {
    public static UsuarioDto de(UsuarioAutenticado usuario) {
        return new UsuarioDto(usuario.getId(), usuario.getNombre(), usuario.getUsername());
    }
}
