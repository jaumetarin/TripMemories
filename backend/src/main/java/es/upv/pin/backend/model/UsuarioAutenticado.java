package es.upv.pin.backend.model;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring borra el hash de este principal después de autenticar. */
public class UsuarioAutenticado extends User {
    private static final long serialVersionUID = 1L;
    private final UUID id;
    private final String nombre;

    public UsuarioAutenticado(Usuario usuario) {
        super(usuario.getCorreo(), usuario.getPasswordHash(), List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        id = usuario.getId();
        nombre = usuario.getNombre();
    }
    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
}
