package es.upv.pin.backend.config;

import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.LimiteProveedorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LimiteProveedorConfig implements WebMvcConfigurer {
    private final LimiteProveedorService limite;
    public LimiteProveedorConfig(LimiteProveedorService limite) { this.limite = limite; }
    @Override public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
                if (!(handler instanceof org.springframework.web.method.HandlerMethod metodo)) return true;
                Class<?> tipo = metodo.getBeanType();
                String nombre = metodo.getMethod().getName();
                boolean protegida = tipo == es.upv.pin.backend.controller.DestinoController.class
                        || tipo == es.upv.pin.backend.controller.AlojamientoController.class
                        || (tipo == es.upv.pin.backend.controller.SeleccionLugaresController.class && nombre.equals("buscar"))
                        || tipo == es.upv.pin.backend.controller.TrazadosController.class
                        || (tipo == es.upv.pin.backend.controller.EdicionItinerarioController.class
                            && java.util.Set.of("alternativas", "recalcular", "sustituir", "eliminar", "restaurar", "reordenar").contains(nombre));
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (!protegida || auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) return true;
                long espera = limite.reservar(usuario.getId(), "http", 120);
                if (espera == 0) return true;
                resp.setStatus(429); resp.setHeader("Retry-After", Long.toString(espera));
                resp.setHeader("Cache-Control", "no-store"); resp.setContentType("application/json");
                resp.getWriter().write("{\"mensaje\":\"Has alcanzado el límite temporal de consultas. Inténtalo más tarde.\"}");
                return false;
            }
        });
    }
}
