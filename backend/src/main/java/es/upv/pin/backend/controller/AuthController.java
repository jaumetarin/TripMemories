package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService servicio;
    private final LimiteAuthService limites;
    private final AuthenticationManager autenticacion;
    private final SessionAuthenticationStrategy estrategia;
    private final HttpSessionSecurityContextRepository contextos;

    public AuthController(AuthService servicio, LimiteAuthService limites, AuthenticationManager autenticacion,
            SessionAuthenticationStrategy estrategia, HttpSessionSecurityContextRepository contextos) {
        this.servicio = servicio;
        this.limites = limites;
        this.autenticacion = autenticacion;
        this.estrategia = estrategia;
        this.contextos = contextos;
    }
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", token.getToken(), "cabecera", token.getHeaderName()));
    }
    @PostMapping("/registro")
    public ResponseEntity<Map<String, String>> registrar(@Valid @RequestBody RegistroDto entrada, HttpServletRequest peticion) {
        limites.comprobar("registro", peticion.getRemoteAddr(), 5);
        servicio.registrar(entrada);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("mensaje", AuthService.RESPUESTA_REGISTRO));
    }
    @PostMapping("/reenviar")
    public ResponseEntity<Map<String, String>> reenviar(@Valid @RequestBody CorreoDto entrada, HttpServletRequest peticion) {
        limites.comprobar("reenviar", peticion.getRemoteAddr(), 5);
        servicio.reenviar(entrada.correo());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("mensaje", AuthService.RESPUESTA_REGISTRO));
    }
    @PostMapping("/verificar")
    public ResponseEntity<Map<String, String>> verificar(@Valid @RequestBody VerificarCorreoDto entrada, HttpServletRequest peticion) {
        limites.comprobar("verificar", peticion.getRemoteAddr(), 10);
        servicio.verificar(entrada);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("mensaje", "Correo verificado. Ya puedes iniciar sesión."));
    }
    @PostMapping("/login")
    public ResponseEntity<UsuarioDto> login(@Valid @RequestBody LoginDto entrada, HttpServletRequest peticion, HttpServletResponse respuesta) {
        limites.comprobar("login", peticion.getRemoteAddr(), 10);
        if (peticion.getUserPrincipal() != null) throw new IllegalArgumentException("Cierra la sesión actual antes de acceder con otra cuenta.");
        var autenticado = autenticacion.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                AuthService.normalizarCorreo(entrada.correo()), entrada.contrasena()));
        estrategia.onAuthentication(autenticado, peticion, respuesta);
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, peticion, respuesta);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(UsuarioDto.de((UsuarioAutenticado) autenticado.getPrincipal()));
    }
    @GetMapping("/sesion")
    public ResponseEntity<UsuarioDto> sesion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(UsuarioDto.de(usuario));
    }
}
