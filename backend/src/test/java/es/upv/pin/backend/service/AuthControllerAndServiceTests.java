package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import es.upv.pin.backend.client.BrevoClient;
import es.upv.pin.backend.controller.AuthController;
import es.upv.pin.backend.dto.RegistroDto;
import es.upv.pin.backend.dto.VerificarCorreoDto;
import es.upv.pin.backend.exception.AuthExceptionHandler;
import es.upv.pin.backend.model.Usuario;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AuthControllerAndServiceTests {
    private static final Instant AHORA = Instant.parse("2026-11-26T10:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String CLAVE_VALIDA = "ViajeSeguro2026!";

    private static TransactionTemplate transaccionDirecta() {
        return new TransactionTemplate() {
            @Override
            public void executeWithoutResult(Consumer<TransactionStatus> action) {
                action.accept(null);
            }
        };
    }

    @Test
    void registroValidoGuardaTokenHasheadoYPublicaEventoConFragmento() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        BrevoClient brevo = mock(BrevoClient.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        AuthService servicio = new AuthService(
                repo, encoder, brevo, eventos, transaccionDirecta(), "http://localhost:5173/", RELOJ);

        when(brevo.disponible()).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(repo.bloquearPorCorreo("ana@upv.es"))
                .thenReturn(Optional.of(new Usuario(id, "Ana", "ana@upv.es", "hash", false)));
        when(repo.puedeReenviar(id, AHORA)).thenReturn(true);

        servicio.registrar(new RegistroDto("  Ana  ", "  ANA@UPV.ES ", CLAVE_VALIDA, CLAVE_VALIDA));

        verify(repo).crearSiNoExiste(any(), eq("Ana"), eq("ana@upv.es"), anyString());
        verify(repo).actualizarPendiente(eq(id), eq("Ana"), anyString());
        verify(repo).guardarVerificacion(eq(id), argThat(h -> h.length() == 64), eq(AHORA));
        verify(eventos).publishEvent(argThat((CorreoVerificacion ev) ->
                ev.correo().equals("ana@upv.es")
                        && ev.enlace().startsWith("http://localhost:5173/#verificar-correo=")
                        && ev.toString().contains("datos reservados")));
    }

    @Test
    void registroRechazaContrasenasDistintasBlancosYBrevoNoDisponible() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        BrevoClient brevo = mock(BrevoClient.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        AuthService servicio = new AuthService(
                repo, encoder, brevo, eventos, transaccionDirecta(), "https://rute.example.com", RELOJ);

        assertThrows(IllegalArgumentException.class, () ->
                servicio.registrar(new RegistroDto("Ana", "ana@upv.es", CLAVE_VALIDA, "OtraClave2026!")));
        assertThrows(IllegalArgumentException.class, () ->
                servicio.registrar(new RegistroDto("Ana", "ana@upv.es", "        ", "        ")));

        when(brevo.disponible()).thenReturn(false);
        assertThrows(IllegalStateException.class, () ->
                servicio.registrar(new RegistroDto("Ana", "ana@upv.es", CLAVE_VALIDA, CLAVE_VALIDA)));
        assertThrows(IllegalStateException.class, () -> servicio.reenviar("ana@upv.es"));
    }

    @Test
    void verificarValidaTokenYContrasenaYRechazaTokenInvalido() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        BrevoClient brevo = mock(BrevoClient.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        AuthService servicio = new AuthService(
                repo, encoder, brevo, eventos, transaccionDirecta(), "https://rute.example.com", RELOJ);

        String token = "A".repeat(43);
        String hashToken = AuthService.hashToken(token);
        UUID id = UUID.randomUUID();
        String hashClave = encoder.encode(CLAVE_VALIDA);

        when(repo.usuarioDelToken(hashToken, AHORA)).thenReturn(Optional.of(id));
        when(repo.bloquearPorId(id)).thenReturn(Optional.of(new Usuario(id, "Ana", "ana@upv.es", hashClave, false)));

        servicio.verificar(new VerificarCorreoDto(token, CLAVE_VALIDA));
        verify(repo).verificar(id);

        when(repo.usuarioDelToken(hashToken, AHORA)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                servicio.verificar(new VerificarCorreoDto(token, CLAVE_VALIDA)));
    }

    @Test
    void reenvioSoloEmiteParaCuentasPendientesEnVentanaPermitida() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        BrevoClient brevo = mock(BrevoClient.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        AuthService servicio = new AuthService(
                repo, encoder, brevo, eventos, transaccionDirecta(), "http://127.0.0.1:5173", RELOJ);

        when(brevo.disponible()).thenReturn(true);
        UUID idVerificada = UUID.randomUUID();
        when(repo.bloquearPorCorreo("verificada@upv.es"))
                .thenReturn(Optional.of(new Usuario(idVerificada, "Eva", "verificada@upv.es", "h", true)));
        servicio.reenviar("verificada@upv.es");
        verify(eventos, never()).publishEvent(any());

        UUID idPendiente = UUID.randomUUID();
        when(repo.bloquearPorCorreo("pendiente@upv.es"))
                .thenReturn(Optional.of(new Usuario(idPendiente, "Leo", "pendiente@upv.es", "h", false)));
        when(repo.puedeReenviar(idPendiente, AHORA)).thenReturn(true);
        servicio.reenviar("pendiente@upv.es");
        verify(eventos).publishEvent(any(CorreoVerificacion.class));
    }

    @Test
    void envioVerificacionToleraErroresDelClienteYDelEjecutor() {
        BrevoClient brevo = mock(BrevoClient.class);
        doThrow(new IllegalStateException("fallo de red")).when(brevo).enviarVerificacion(any(), any(), any());
        Executor directo = Runnable::run;
        var envio = new EnvioVerificacionService(brevo, directo);
        assertDoesNotThrow(() -> envio.enviarTrasConfirmar(
                new CorreoVerificacion("a@b.com", "Ana", "http://localhost:5173/#verificar-correo=tok")));

        Executor saturado = r -> { throw new RuntimeException("cola llena"); };
        var envioSaturado = new EnvioVerificacionService(brevo, saturado);
        assertDoesNotThrow(() -> envioSaturado.enviarTrasConfirmar(
                new CorreoVerificacion("a@b.com", "Ana", "http://localhost:5173/#verificar-correo=tok")));
    }

    @Test
    void controladorAuthMapeaRespuestasYErroresHttp() throws Exception {
        AuthService authService = mock(AuthService.class);
        LimiteAuthService limites = new LimiteAuthService(RELOJ);
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        SessionAuthenticationStrategy estrategia = mock(SessionAuthenticationStrategy.class);
        HttpSessionSecurityContextRepository contextos = mock(HttpSessionSecurityContextRepository.class);

        AuthController controller = new AuthController(authService, limites, authManager, estrategia, contextos);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthExceptionHandler())
                .build();

        var respuestaCsrf = controller.csrf(new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "tok-123"));
        assertEquals(200, respuestaCsrf.getStatusCode().value());
        assertEquals("tok-123", respuestaCsrf.getBody().get("token"));
        assertEquals("X-CSRF-TOKEN", respuestaCsrf.getBody().get("cabecera"));

        UsuarioAutenticado principalPrueba = new UsuarioAutenticado(
                new Usuario(UUID.randomUUID(), "Ana", "ana@upv.es", "hash", true));
        var respuestaSesion = controller.sesion(principalPrueba);
        assertEquals(200, respuestaSesion.getStatusCode().value());
        assertEquals("ana@upv.es", respuestaSesion.getBody().correo());

        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"ana@upv.es\",\"contrasena\":\"Viaje2026!\",\"confirmacionContrasena\":\"Viaje2026!\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(AuthService.RESPUESTA_REGISTRO));

        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"correo\":\"no-correo\",\"contrasena\":\"corta\",\"confirmacionContrasena\":\"otra\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());

        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{json-malo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        mvc.perform(post("/api/auth/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@upv.es\"}"))
                .andExpect(status().isAccepted());

        String token = "B".repeat(43);
        mvc.perform(post("/api/auth/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"contrasena\":\"Viaje2026!\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"corto\",\"contrasena\":\"Viaje2026!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(AuthService.ENLACE_INVALIDO));

        UsuarioAutenticado principal = new UsuarioAutenticado(
                new Usuario(UUID.randomUUID(), "Ana", "ana@upv.es", "hash", true));
        when(authManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@upv.es\",\"contrasena\":\"Viaje2026!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("ana@upv.es"))
                .andExpect(jsonPath("$.nombre").value("Ana"));
        verify(estrategia).onAuthentication(any(), any(HttpServletRequest.class), any(HttpServletResponse.class));

        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@upv.es\",\"contrasena\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"\",\"contrasena\":\"\"}"))
                .andExpect(status().isUnauthorized());

        doThrow(new IllegalStateException("Brevo caido")).when(authService).reenviar(anyString());
        mvc.perform(post("/api/auth/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@upv.es\"}"))
                .andExpect(status().isServiceUnavailable());

        // Agotar limite de reenvio (5 peticiones totales desde la misma IP) -> 429 con Retry-After.
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/reenviar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"correo\":\"ana@upv.es\"}"));
        }
        mvc.perform(post("/api/auth/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@upv.es\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
    }
}
