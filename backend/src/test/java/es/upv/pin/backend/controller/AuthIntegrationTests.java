package es.upv.pin.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import es.upv.pin.backend.PostgresTest;
import es.upv.pin.backend.client.BrevoClient;
import es.upv.pin.backend.repository.UsuarioRepository;
import es.upv.pin.backend.service.AuthService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTests extends PostgresTest {
    private static final String CLAVE = "Una frase larga de prueba 2026!";
    private static final AtomicInteger IPS = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @MockitoBean BrevoClient brevo;
    private final ConcurrentLinkedQueue<CorreoPrueba> correos = new ConcurrentLinkedQueue<>();
    private String ip;

    @BeforeEach
    void correoSimuladoSinLlamarAlProveedor() {
        ip = "192.0.2." + IPS.incrementAndGet();
        correos.clear();
        when(brevo.disponible()).thenReturn(true);
        doAnswer(invocacion -> {
            correos.add(new CorreoPrueba(invocacion.getArgument(0), invocacion.getArgument(2)));
            return null;
        }).when(brevo).enviarVerificacion(anyString(), anyString(), anyString());
    }

    @Test
    void registroVerificacionLoginYLogoutConPersistenciaRealYCsrfReal() throws Exception {
        String correo = nuevoCorreo();
        MvcResult alta = enviar("registro", registro(correo), 202);
        assertFalse(alta.getResponse().getContentAsString().contains(correo));
        String token = esperarToken(correo);
        var usuario = usuarios.buscarPorCorreo(correo).orElseThrow();
        assertFalse(usuario.isCorreoVerificado());
        assertTrue(usuario.getPasswordHash().startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$"));
        assertTrue(encoder.matches(CLAVE, usuario.getPasswordHash()));
        assertNotEquals(CLAVE, usuario.getPasswordHash());
        String guardado = jdbc.sql("SELECT token_hash FROM verificacion_correo WHERE usuario_id = :id")
                .param("id", usuario.getId()).query(String.class).single();
        assertEquals(AuthService.hashToken(token), guardado);
        assertNotEquals(token, guardado);
        enviar("login", Map.of("correo", correo, "contrasena", CLAVE), 401);
        enviar("verificar", Map.of("token", token, "contrasena", CLAVE), 200);

        // Se usa el mismo contrato CSRF que el navegador, sin el atajo csrf() de los otros tests.
        MvcResult seguridad = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var datosCsrf = JSON.readTree(seguridad.getResponse().getContentAsString());
        MockHttpSession sesion = (MockHttpSession) seguridad.getRequest().getSession(false);
        String idAnterior = sesion.getId();
        sesion.setAttribute("datos-previos", "conservados");
        MvcResult acceso = mvc.perform(post("/api/auth/login").session(sesion)
                        .with(r -> { r.setRequestedSessionId(idAnterior); r.setRequestedSessionIdValid(true); return r; })
                        .header(datosCsrf.get("cabecera").asString(), datosCsrf.get("token").asString())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("correo", correo.toUpperCase(), "contrasena", CLAVE))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.contrasena").doesNotExist()).andReturn();
        assertNotEquals(idAnterior, sesion.getId(), "El login debe renovar el identificador de sesión");
        assertEquals("conservados", sesion.getAttribute("datos-previos"));
        mvc.perform(get("/api/auth/sesion").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId().toString()));
        String cuerpo = acceso.getResponse().getContentAsString();
        assertFalse(cuerpo.contains(CLAVE));
        assertFalse(cuerpo.contains("argon2"));

        // El token anterior al login ya no sirve; el frontend obtiene uno nuevo.
        mvc.perform(post("/api/auth/logout").session(sesion)
                .header("X-CSRF-TOKEN", datosCsrf.get("token").asString())).andExpect(status().isForbidden());
        var nuevoCsrf = JSON.readTree(mvc.perform(get("/api/auth/csrf").session(sesion)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").session(sesion).header("X-CSRF-TOKEN", nuevoCsrf.get("token").asString()))
                .andExpect(status().isOk());
        assertTrue(sesion.isInvalid());
        mvc.perform(get("/api/auth/sesion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/destinos").param("texto", "Madrid")).andExpect(status().isUnauthorized());
        enviar("verificar", Map.of("token", token, "contrasena", CLAVE), 400);
    }

    @Test
    void rechazaCamposInvalidosSinCrearUsuarios() throws Exception {
        String correo = nuevoCorreo();
        for (Map<String, ?> entrada : java.util.List.of(
                Map.of("nombre", "", "correo", correo, "contrasena", CLAVE, "confirmacionContrasena", CLAVE),
                Map.of("nombre", "Ana", "correo", "correo-invalido", "contrasena", CLAVE, "confirmacionContrasena", CLAVE),
                Map.of("nombre", "Ana", "correo", correo, "contrasena", "corta", "confirmacionContrasena", "corta"),
                Map.of("nombre", "Ana", "correo", correo, "contrasena", CLAVE, "confirmacionContrasena", "otra"),
                Map.of("nombre", "Ana", "correo", correo, "contrasena", " ".repeat(20), "confirmacionContrasena", " ".repeat(20)),
                Map.of("nombre", "Ana", "correo", correo, "contrasena", "x".repeat(129), "confirmacionContrasena", "x".repeat(129))
        )) enviar("registro", entrada, 400);
        assertTrue(usuarios.buscarPorCorreo(correo).isEmpty());
        verify(brevo, never()).enviarVerificacion(anyString(), anyString(), anyString());
    }

    @Test
    void duplicadoNoRevelaCuentaNiCambiaLaContrasenaVerificada() throws Exception {
        String correo = crearVerificada();
        var original = usuarios.buscarPorCorreo(correo).orElseThrow();
        String respuesta = enviar("registro", registro(correo.toUpperCase()), 202).getResponse().getContentAsString();
        String otro = nuevoCorreo();
        assertEquals(respuesta, enviar("registro", registro(otro), 202).getResponse().getContentAsString());
        esperarToken(otro);
        assertEquals(original.getPasswordHash(), usuarios.buscarPorCorreo(correo).orElseThrow().getPasswordHash());
        assertEquals(1, jdbc.sql("SELECT count(*) FROM usuario WHERE lower(correo) = :correo").param("correo", correo).query(Integer.class).single());
    }

    @Test
    void loginFallidoNoDistingueAusentePendienteNiContrasenaErronea() throws Exception {
        String existente = crearVerificada();
        String pendiente = nuevoCorreo();
        enviar("registro", registro(pendiente), 202);
        esperarToken(pendiente);
        String error = enviar("login", Map.of("correo", existente, "contrasena", "incorrecta"), 401).getResponse().getContentAsString();
        assertEquals(error, enviar("login", Map.of("correo", pendiente, "contrasena", CLAVE), 401).getResponse().getContentAsString());
        assertEquals(error, enviar("login", Map.of("correo", nuevoCorreo(), "contrasena", CLAVE), 401).getResponse().getContentAsString());
        assertEquals(error, enviar("login", Map.of("correo", "", "contrasena", ""), 401).getResponse().getContentAsString());
        enviar("login", Map.of("correo", existente, "contrasena", CLAVE), 200);
    }

    @Test
    void enlaceCaducadoReutilizadoOConContrasenaAjenaNoActivaLaCuenta() throws Exception {
        String correo = nuevoCorreo();
        enviar("registro", registro(correo), 202);
        String token = esperarToken(correo);
        enviar("verificar", Map.of("token", token, "contrasena", "No elegi esta contrasena"), 400);
        assertFalse(usuarios.buscarPorCorreo(correo).orElseThrow().isCorreoVerificado());
        jdbc.sql("UPDATE verificacion_correo SET expira_en = :expira WHERE token_hash = :hash")
                .param("expira", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(5))
                .param("hash", AuthService.hashToken(token)).update();
        enviar("verificar", Map.of("token", token, "contrasena", CLAVE), 400);
        assertFalse(usuarios.buscarPorCorreo(correo).orElseThrow().isCorreoVerificado());
    }

    @Test
    void reenvioRespetaLaEsperaEInvalidaElEnlaceAnterior() throws Exception {
        String correo = nuevoCorreo();
        enviar("registro", registro(correo), 202);
        String anterior = esperarToken(correo);
        String respuesta = enviar("reenviar", Map.of("correo", correo), 202).getResponse().getContentAsString();
        assertEquals(respuesta, enviar("reenviar", Map.of("correo", nuevoCorreo()), 202).getResponse().getContentAsString());
        assertTrue(correos.isEmpty(), "No debe reenviar antes de sesenta segundos");
        jdbc.sql("UPDATE verificacion_correo SET ultimo_envio_en = :ultimoEnvio WHERE token_hash = :hash")
                .param("ultimoEnvio", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(65))
                .param("hash", AuthService.hashToken(anterior)).update();
        enviar("reenviar", Map.of("correo", correo), 202);
        String nuevo = esperarToken(correo);
        assertNotEquals(anterior, nuevo);
        enviar("verificar", Map.of("token", anterior, "contrasena", CLAVE), 400);
        enviar("verificar", Map.of("token", nuevo, "contrasena", CLAVE), 200);
    }

    @Test
    void altasSimultaneasNoDuplicanUsuariosNiEnvios() throws Exception {
        String correo = nuevoCorreo();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var primera = executor.submit(() -> enviar("registro", registro(correo), 202));
            var segunda = executor.submit(() -> enviar("registro", registro(correo.toUpperCase()), 202));
            primera.get(20, TimeUnit.SECONDS); segunda.get(20, TimeUnit.SECONDS);
        }
        esperarToken(correo);
        assertEquals(1, jdbc.sql("SELECT count(*) FROM usuario WHERE lower(correo) = :correo").param("correo", correo).query(Integer.class).single());
        verify(brevo, times(1)).enviarVerificacion(eq(correo), anyString(), anyString());
    }

    @Test
    void sinCsrfOSinSesionNoSeAccedeALosRecursosPrivados() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(registro(nuevoCorreo()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));
        for (String ruta : java.util.List.of("/api/auth/sesion", "/api/destinos?texto=Mad", "/api/candidatos/turisticos?destinoId=x"))
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/itinerarios/generar").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(brevo);
    }

    @Test
    void limitaLosIntentosAntesDeAutenticar() throws Exception {
        Map<String, Object> datos = Map.of("correo", nuevoCorreo(), "contrasena", CLAVE);
        for (int intento = 0; intento < 10; intento++) enviar("login", datos, 401);
        enviar("login", datos, 429);
    }

    @Test
    void noCreaCuentaSiBrevoNoEstaConfigurado() throws Exception {
        when(brevo.disponible()).thenReturn(false);
        String correo = nuevoCorreo();
        enviar("registro", registro(correo), 503);
        assertTrue(usuarios.buscarPorCorreo(correo).isEmpty());
    }

    @Test
    void permiteLosDosNombresLocalesPeroRechazaUnOrigenAjeno() throws Exception {
        for (String origen : java.util.List.of("http://localhost:5173", "http://127.0.0.1:5173")) {
            mvc.perform(get("/api/auth/csrf").header("Origin", origen)).andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origen));
            mvc.perform(post("/api/auth/registro").with(csrf()).header("Origin", origen)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/registro").with(csrf()).header("Origin", "https://ajeno.example")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void falloDeEntregaPermiteConservarSolicitudYReenviarSinExponerDetalles() throws Exception {
        doThrow(new IllegalStateException("datos del proveedor que no deben salir"))
                .when(brevo).enviarVerificacion(anyString(), anyString(), anyString());
        String correo = nuevoCorreo();
        enviar("registro", registro(correo), 202);
        verify(brevo, timeout(2000)).enviarVerificacion(eq(correo), anyString(), anyString());
        assertFalse(usuarios.buscarPorCorreo(correo).orElseThrow().isCorreoVerificado());
        assertEquals(1, jdbc.sql("SELECT count(*) FROM verificacion_correo WHERE usuario_id = :id")
                .param("id", usuarios.buscarPorCorreo(correo).orElseThrow().getId()).query(Integer.class).single());
    }

    @Test
    void unHashBcryptAnteriorSeActualizaSoloTrasUnaAutenticacionValida() throws Exception {
        String correo = nuevoCorreo();
        String claveAnterior = "Una frase anterior sin simbolos ni numeros";
        String anterior = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(claveAnterior);
        usuarios.crearSiNoExiste(UUID.randomUUID(), "Cuenta anterior", correo, anterior);
        jdbc.sql("UPDATE usuario SET correo_verificado = true WHERE correo = :correo").param("correo", correo).update();
        enviar("login", Map.of("correo", correo, "contrasena", "incorrecta"), 401);
        assertEquals(anterior, usuarios.buscarPorCorreo(correo).orElseThrow().getPasswordHash());
        enviar("login", Map.of("correo", correo, "contrasena", claveAnterior), 200);
        assertTrue(usuarios.buscarPorCorreo(correo).orElseThrow().getPasswordHash().startsWith("{argon2id}"));
    }

    @Test
    void registroBloqueaContrasenasSinCombinacionAunqueSeOmitaElFrontend() throws Exception {
        String correo = nuevoCorreo();
        for (String clave : java.util.List.of("Abc12!x", "Abcdefgh", "12345678!", "Abcdef12", "Abc1234 ", "Árbol123", "a".repeat(127) + "1!")) {
            mvc.perform(post("/api/auth/registro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(Map.of("nombre", "Ana", "correo", correo,
                            "contrasena", clave, "confirmacionContrasena", clave))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.contrasena").isString());
        }
        assertTrue(usuarios.buscarPorCorreo(correo).isEmpty());
        verify(brevo, never()).enviarVerificacion(anyString(), anyString(), anyString());
    }

    @Test
    void unaContrasenaDeOchoCaracteresConLaCombinacionPermiteCrearYUsarLaCuenta() throws Exception {
        String correo = nuevoCorreo();
        String clave = "ruta123!";
        enviar("registro", Map.of("nombre", "Ana", "correo", correo, "contrasena", clave, "confirmacionContrasena", clave), 202);
        enviar("verificar", Map.of("token", esperarToken(correo), "contrasena", clave), 200);
        enviar("login", Map.of("correo", correo, "contrasena", clave), 200);
        assertTrue(encoder.matches(clave, usuarios.buscarPorCorreo(correo).orElseThrow().getPasswordHash()));
    }

    @Test
    void verificarUnRegistroAnteriorNoExigeLaNuevaCombinacion() throws Exception {
        String correo = nuevoCorreo();
        UUID id = UUID.randomUUID();
        String claveAnterior = "Una frase anterior sin simbolos ni numeros";
        usuarios.crearSiNoExiste(id, "Ana", correo, encoder.encode(claveAnterior));
        String token = "A".repeat(43);
        usuarios.guardarVerificacion(id, AuthService.hashToken(token), java.time.Instant.now());
        enviar("verificar", Map.of("token", token, "contrasena", claveAnterior), 200);
        enviar("login", Map.of("correo", correo, "contrasena", claveAnterior), 200);
    }

    @Test
    void registroAdmiteTildesSimbolosYElLimiteDeCientoVeintiochoCaracteres() throws Exception {
        for (String clave : java.util.List.of("Árbol12!", "a".repeat(126) + "1!")) {
            String correo = nuevoCorreo();
            enviar("registro", Map.of("nombre", "Ana", "correo", correo, "contrasena", clave, "confirmacionContrasena", clave), 202);
            esperarToken(correo);
            assertTrue(encoder.matches(clave, usuarios.buscarPorCorreo(correo).orElseThrow().getPasswordHash()));
        }
    }

    private MvcResult enviar(String operacion, Map<String, ?> cuerpo, int estado) throws Exception {
        return mvc.perform(post("/api/auth/" + operacion).with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(cuerpo)))
                .andExpect(status().is(estado)).andReturn();
    }
    private Map<String, Object> registro(String correo) {
        return Map.of("nombre", "Ana", "correo", correo, "contrasena", CLAVE, "confirmacionContrasena", CLAVE);
    }
    private String crearVerificada() throws Exception {
        String correo = nuevoCorreo();
        enviar("registro", registro(correo), 202);
        enviar("verificar", Map.of("token", esperarToken(correo), "contrasena", CLAVE), 200);
        return correo;
    }
    private String esperarToken(String correo) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            CorreoPrueba mensaje = correos.poll();
            if (mensaje != null) {
                assertEquals(correo, mensaje.correo());
                assertTrue(mensaje.enlace().startsWith("http://localhost:5173/#verificar-correo="));
                return mensaje.enlace().substring(mensaje.enlace().indexOf('=') + 1);
            }
            Thread.sleep(10);
        }
        throw new AssertionError("No se recibió el correo simulado");
    }
    private String nuevoCorreo() { return "prueba-" + UUID.randomUUID() + "@example.test"; }
    private record CorreoPrueba(String correo, String enlace) { }
}
