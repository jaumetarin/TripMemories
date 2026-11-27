package es.upv.pin.backend.client;

import es.upv.pin.backend.exception.LimiteProveedorException;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.LimiteProveedorService;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Cuenta cada intento HTTP real, después de validar parámetros y consultar cachés. */
@Component
public class ControlLlamadasProveedor implements ClientHttpRequestInterceptor {
    private final LimiteProveedorService limite;
    public ControlLlamadasProveedor(LimiteProveedorService limite) { this.limite = limite; }
    @Override public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
            long espera = limite.reservar(usuario.getId(), "proveedores", 120);
            if (espera > 0) throw new LimiteProveedorException(espera);
        }
        return execution.execute(request, body);
    }
}
