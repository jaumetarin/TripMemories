package es.upv.pin.backend.service;

import es.upv.pin.backend.client.BrevoClient;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.repository.UsuarioRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
    public static final String RESPUESTA_REGISTRO = "Si la solicitud puede completarse, recibirás un enlace de verificación. Revisa también el correo no deseado. Si ya tienes cuenta, inicia sesión.";
    public static final String ENLACE_INVALIDO = "No se ha podido verificar la cuenta. Comprueba el enlace y la contraseña elegida al registrarte. Si el enlace ha caducado, solicita otro.";
    private final UsuarioRepository usuarios;
    private final PasswordEncoder contrasenas;
    private final BrevoClient correo;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transacciones;
    private final String frontend;
    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock reloj;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(UsuarioRepository usuarios, PasswordEncoder contrasenas, BrevoClient correo,
            ApplicationEventPublisher eventos, TransactionTemplate transacciones, @Value("${auth.frontend-url}") String frontend) {
        this(usuarios, contrasenas, correo, eventos, transacciones, frontend, Clock.systemUTC());
    }
    AuthService(UsuarioRepository usuarios, PasswordEncoder contrasenas, BrevoClient correo,
            ApplicationEventPublisher eventos, TransactionTemplate transacciones, String frontend, Clock reloj) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.correo = correo;
        this.eventos = eventos;
        this.transacciones = transacciones;
        var url = URI.create(frontend);
        boolean local = "localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost());
        if (url.getHost() == null || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null
                || !("https".equals(url.getScheme()) || local && "http".equals(url.getScheme())))
            throw new IllegalArgumentException("APP_FRONTEND_URL debe ser HTTPS, o HTTP en localhost para desarrollo.");
        this.frontend = frontend.replaceAll("/+$", "");
        this.reloj = reloj;
    }
    public void registrar(RegistroDto entrada) {
        if (!entrada.contrasena().equals(entrada.confirmacionContrasena()))
            throw new IllegalArgumentException("Las contraseñas no coinciden.");
        if (entrada.contrasena().isBlank()) throw new IllegalArgumentException("La contraseña no puede estar formada solo por espacios.");
        comprobarCorreo();
        // También se calcula para duplicados; no se distingue la existencia por un hash omitido.
        String hash = contrasenas.encode(entrada.contrasena());
        String email = normalizarCorreo(entrada.correo());
        transacciones.executeWithoutResult(estado -> {
            usuarios.crearSiNoExiste(UUID.randomUUID(), entrada.nombre().strip(), email, hash);
            var usuario = usuarios.bloquearPorCorreo(email).orElseThrow();
            if (usuario.isCorreoVerificado() || !usuarios.puedeReenviar(usuario.getId(), reloj.instant())) return;
            // Solo una solicitud aún no verificada puede renovar sus credenciales.
            usuarios.actualizarPendiente(usuario.getId(), entrada.nombre().strip(), hash);
            emitirVerificacion(usuario.getId(), email, entrada.nombre().strip());
        });
    }
    public void reenviar(String direccion) {
        comprobarCorreo();
        transacciones.executeWithoutResult(estado -> usuarios.bloquearPorCorreo(normalizarCorreo(direccion)).ifPresent(usuario -> {
            if (!usuario.isCorreoVerificado() && usuarios.puedeReenviar(usuario.getId(), reloj.instant()))
                emitirVerificacion(usuario.getId(), usuario.getCorreo(), usuario.getNombre());
        }));
    }
    public void verificar(VerificarCorreoDto entrada) {
        Instant ahora = reloj.instant();
        String hash = hashToken(entrada.token());
        transacciones.executeWithoutResult(estado -> {
            var id = usuarios.usuarioDelToken(hash, ahora).orElseThrow(() -> new IllegalArgumentException(ENLACE_INVALIDO));
            var usuario = usuarios.bloquearPorId(id).orElseThrow(() -> new IllegalArgumentException(ENLACE_INVALIDO));
            // Volver a comprobar después del bloqueo evita usar a la vez un enlace consumido o reenviado.
            if (usuario.isCorreoVerificado() || usuarios.usuarioDelToken(hash, reloj.instant()).filter(id::equals).isEmpty()
                    || !contrasenas.matches(entrada.contrasena(), usuario.getPasswordHash()))
                throw new IllegalArgumentException(ENLACE_INVALIDO);
            usuarios.verificar(id);
        });
    }
    private void emitirVerificacion(UUID usuarioId, String direccion, String nombre) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        usuarios.guardarVerificacion(usuarioId, hashToken(token), reloj.instant());
        // Fragmento: el token no se envía como URL de petición ni como Referer.
        eventos.publishEvent(new CorreoVerificacion(direccion, nombre, frontend + "/#verificar-correo=" + token));
    }
    private void comprobarCorreo() {
        if (!correo.disponible()) throw new IllegalStateException("El registro y el reenvío de correos no están disponibles temporalmente. Inténtalo más tarde.");
    }
    public static String normalizarCorreo(String correo) { return correo.strip().toLowerCase(Locale.ROOT); }
    public static String hashToken(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("No se puede preparar la verificación."); }
    }
}
