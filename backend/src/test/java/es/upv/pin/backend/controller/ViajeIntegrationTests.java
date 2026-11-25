package es.upv.pin.backend.controller;

import es.upv.pin.backend.PostgresTest;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.exception.RevisionViajeException;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.repository.ViajeRepository;
import es.upv.pin.backend.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ViajeIntegrationTests extends PostgresTest {
    private static final String CLAVE = "Una clave de prueba 2026!";
    private static final AtomicInteger IPS = new AtomicInteger();
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder contrasenas;
    @Autowired ViajeService escritura;
    @Autowired ConsultaViajeService consultas;
    @Autowired ViajeRepository repositorio;
    @MockitoBean GeneracionItinerarioService generador;
    private UUID usuario;
    private String correo;
    private MockHttpSession sesion;
    private ConfiguracionItinerarioDto configuracion;
    private ItinerarioGeneradoDto plan;

    @BeforeEach
    void preparar() throws Exception {
        usuario = UUID.randomUUID();
        correo = usuario + "@example.test";
        jdbc.sql("INSERT INTO usuario (id, nombre, correo, password_hash, correo_verificado) VALUES (:id, 'Prueba', :correo, :hash, true)")
                .param("id", usuario).param("correo", correo).param("hash", contrasenas.encode(CLAVE)).update();
        sesion = entrar();
        var inicio = LocalDate.now(ZoneOffset.UTC).plusDays(30).atTime(10, 0);
        configuracion = new ConfiguracionItinerarioDto(new DestinoDto("ciudad", "Valencia", "Valencia", "España", 39.47, -0.37),
                new FechaHoraDto(inicio, inicio.plusDays(1).plusHours(8), 2), "",
                new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), List.of(), NivelGasto.MODERADO, 3.0),
                new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10));
        plan = plan("Original");
        when(generador.generar(any())).thenReturn(plan);
    }

    @Test
    void guardaRecuperaTrasCerrarSesionYRenameSinRegenerar() throws Exception {
        UUID generacion = generar(configuracion);
        UUID viaje = crear(generacion);
        var original = abrir(viaje);
        assertEquals(plan, original.itinerario());
        assertEquals(2, original.itinerario().dias().size());
        assertTrue(original.itinerario().dias().getLast().bloques().isEmpty());
        assertEquals(6, original.actividades().size());
        mvc.perform(get("/api/viajes").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/auth/logout").session(sesion).with(csrf())).andExpect(status().isOk());
        sesion = entrar();
        assertEquals(original, abrir(viaje));
        mvc.perform(put("/api/viajes/" + viaje).session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ActualizarViajeDto("Nuevo título", 1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        var actualizado = abrir(viaje);
        assertEquals("Nuevo título", actualizado.resumen().titulo());
        assertEquals(original.configuracion(), actualizado.configuracion());
        assertEquals(original.itinerario(), actualizado.itinerario());
        assertEquals(1, consultas.listarPropios(usuario).size());
        assertEquals(2, versiones(viaje));
        verify(generador, times(1)).generar(any());
    }

    @Test
    void actualizaDesdeGeneracionYRechazaLaRevisionAntigua() throws Exception {
        UUID viaje = crear(generar(configuracion));
        var anterior = abrir(viaje);
        var nuevaConfiguracion = conDistancia(4.0);
        var nuevoPlan = plan("Actualizado");
        when(generador.generar(any())).thenReturn(nuevoPlan);
        UUID generacion = generar(nuevaConfiguracion);
        mvc.perform(actualizar(viaje, generacion, 1, "Actualizado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(viaje.toString())).andExpect(jsonPath("$.revision").value(2));
        var actualizado = abrir(viaje);
        assertEquals(4.0, actualizado.configuracion().preferencias().distanciaKm());
        assertEquals(nuevoPlan, actualizado.itinerario());
        assertEquals(2, versiones(viaje));
        assertEquals(1, jdbc.sql("SELECT count(*) FROM itinerario WHERE viaje_id = :id AND es_actual")
                .param("id", viaje).query(Integer.class).single());
        assertTrue(jdbc.sql("SELECT EXISTS(SELECT 1 FROM actividad WHERE id = :id)")
                .param("id", anterior.actividades().getFirst().id()).query(Boolean.class).single());
        mvc.perform(actualizar(viaje, generacion, 1, "Obsoleto")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REVISION_DESACTUALIZADA"));
        assertEquals(actualizado, abrir(viaje));
        verify(generador, times(2)).generar(any());
    }

    @Test
    void falloDeActualizacionConservaTituloRevisionItinerarioYActividades() throws Exception {
        UUID viaje = crear(generar(configuracion));
        var anterior = abrir(viaje);
        when(generador.generar(any())).thenReturn(plan("Pendiente"));
        UUID generacion = generar(conDistancia(5.0));
        String nombre = "fallo_ut23_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.sql("CREATE FUNCTION " + nombre + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.orden = 2 AND EXISTS(SELECT 1 FROM itinerario WHERE id = NEW.itinerario_id AND viaje_id = '" + viaje + "') THEN "
                + "RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'detalle SQL privado'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER " + nombre + " BEFORE INSERT ON actividad FOR EACH ROW EXECUTE FUNCTION " + nombre + "()").update();
        try {
            var fallo = mvc.perform(actualizar(viaje, generacion, 1, "No confirmado"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("GUARDADO_NO_CONFIRMADO"))
                    .andReturn();
            assertFalse(fallo.getResponse().getContentAsString().contains("detalle SQL"));
            assertEquals(anterior, abrir(viaje));
            assertEquals(1, versiones(viaje));
        } finally {
            jdbc.sql("DROP TRIGGER " + nombre + " ON actividad").update();
            jdbc.sql("DROP FUNCTION " + nombre + "()").update();
        }
        mvc.perform(actualizar(viaje, generacion, 1, "Confirmado")).andExpect(status().isOk());
        assertEquals(2, abrir(viaje).resumen().revision());
    }

    @Test
    void deniegaAccesosAjenosAnonimosSinCsrfYEntradasInvalidas() throws Exception {
        UUID generacion = generar(configuracion);
        UUID viaje = crear(generacion);
        var original = abrir(viaje);
        mvc.perform(get("/api/viajes/" + viaje)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/viajes/" + viaje).session(sesion).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Otro\",\"revision\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));
        for (String cuerpo : List.of("{\"titulo\":\" \" ,\"revision\":1}", "{\"titulo\":\"Otro\"}",
                "{\"titulo\":\"Otro\",\"revision\":0}")) {
            mvc.perform(put("/api/viajes/" + viaje).session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        var otra = new MockHttpSession();
        var principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(new Usuario(UUID.randomUUID(), "Ajeno", "ajeno@example.test", "simulado", true)), null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USUARIO")));
        var contexto = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(principal);
        otra.setAttribute("SPRING_SECURITY_CONTEXT", contexto);
        String ajeno = mvc.perform(get("/api/viajes/" + viaje).session(otra)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String ausente = mvc.perform(get("/api/viajes/" + UUID.randomUUID()).session(otra)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals(ajeno, ausente);
        mvc.perform(put("/api/viajes/" + viaje).session(otra).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Ajeno\",\"revision\":1}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/viajes/desde-generacion/" + generacion).session(otra).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Ajeno\"}")).andExpect(status().isConflict());
        assertEquals(original, abrir(viaje));
    }

    @Test
    void dosActualizacionesSimultaneasSoloConfirmanUnaRevision() throws Exception {
        UUID viaje = crear(generar(configuracion));
        var base = abrir(viaje);
        var empezar = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var tareas = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                String titulo = "Concurrente " + i;
                tareas.add(hilos.submit(() -> {
                    empezar.await();
                    try {
                        escritura.actualizar(usuario, viaje, new ActualizarViajeDto(titulo, 1L), base.configuracion(), base.itinerario());
                        return true;
                    } catch (RevisionViajeException esperado) { return false; }
                }));
            }
            empezar.countDown();
            int confirmadas = 0;
            for (var tarea : tareas) if (tarea.get(15, TimeUnit.SECONDS)) confirmadas++;
            assertEquals(1, confirmadas);
        }
        assertEquals(2, abrir(viaje).resumen().revision());
        assertEquals(2, versiones(viaje));
    }

    @Test
    void reabrirRestauraLosLugaresGuardadosEnUnaSesionNuevaSinProveedores() throws Exception {
        var lugar = new LugarDto("prado", "Museo del Prado", "Madrid", 40.4, -3.7,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);
        var conSeleccion = new ConfiguracionItinerarioDto(configuracion.destino(), configuracion.disponibilidad(), "",
                configuracion.preferencias(), configuracion.ritmo(), null,
                List.of(new SeleccionLugarDto(lugar, EstadoLugar.OBLIGATORIO)));
        UUID viaje = escritura.guardarNuevo(usuario, new GuardarViajeDto("Con selección"), conSeleccion, plan);
        var recuperado = abrir(viaje);
        mvc.perform(post("/api/lugares/revisar").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(recuperado.configuracion())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selecciones[0].lugar.id").value("prado"));
        verifyNoInteractions(generador);
    }

    @ParameterizedTest
    @CsvSource({
            "VISITA, 200",
            "VISITA, 201",
            "VISITA, 250",
            "COMIDA, 200",
            "COMIDA, 201",
            "COMIDA, 250"
    })
    void guardaYRecuperaNombresCompletos(
            TipoBloqueItinerario tipo,
            int longitud) throws Exception {

        String nombre = "X".repeat(longitud);

        var lugar = new LugarDto(
                "lugar-prueba", nombre, "Dirección de prueba",
                39.47, -0.37,
                List.of(CategoriaTuristica.ARTE_MUSEOS),
                List.of(), null);

        var candidato = new CandidatoEvaluadoDto(
                lugar, 80, 60, null, null,
                CostLevel.UNKNOWN, null, List.of(),
                null, null, "LOCAL",
                CostConfidence.UNKNOWN, ScheduleStatus.UNVERIFIED,
                null, null, null, null, null, null);

        var restaurante = new OpcionComidaDto(
                "restaurante-prueba", nombre,
                39.47, -0.37, "Dirección de prueba");

        var inicio = configuracion.disponibilidad().llegada()
                .withHour(tipo == TipoBloqueItinerario.COMIDA ? 13 : 10);

        var bloque = new BloqueItinerarioDto(
                tipo,
                inicio,
                inicio.plusHours(tipo == TipoBloqueItinerario.COMIDA ? 2 : 1),
                tipo == TipoBloqueItinerario.VISITA ? candidato : null,
                null, null, null, null, null,
                true,
                "Actividad de prueba",
                tipo == TipoBloqueItinerario.COMIDA ? restaurante : null,
                List.of());

        var dias = new ArrayList<>(plan.dias());
        var primero = dias.getFirst();

        dias.set(0, new DiaItinerarioDto(
                primero.fecha(),
                primero.inicioDisponible(),
                primero.finDisponible(),
                List.of(bloque)));

        List<CandidatoEvaluadoDto> candidatos =
                tipo == TipoBloqueItinerario.VISITA
                        ? List.of(candidato)
                        : List.of();

        var propuesta = new ItinerarioGeneradoDto(
                dias, List.of(), false,
                candidatos.size(), candidatos.size(),
                candidatos, false);

        when(generador.generar(any())).thenReturn(propuesta);

        UUID viaje = crear(generar(configuracion));

        assertEquals(propuesta, abrir(viaje).itinerario());

        String nombreGuardado = jdbc.sql("""
                SELECT a.lugar_nombre
                FROM actividad a
                JOIN itinerario i ON i.id = a.itinerario_id
                WHERE i.viaje_id = :viajeId AND i.es_actual
                """)
                .param("viajeId", viaje)
                .query(String.class)
                .single();

        assertEquals(nombre, nombreGuardado);
    }

    private MockHttpSession entrar() throws Exception {
        var seguridad = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var actual = (MockHttpSession) seguridad.getRequest().getSession(false);
        String token = json.readTree(seguridad.getResponse().getContentAsString()).get("token").asString();
        mvc.perform(post("/api/auth/login").session(actual).header("X-CSRF-TOKEN", token)
                .with(r -> { r.setRemoteAddr("198.51.100." + IPS.incrementAndGet()); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo", correo, "contrasena", CLAVE))))
                .andExpect(status().isOk());
        return actual;
    }
    private UUID generar(ConfiguracionItinerarioDto datos) throws Exception {
        var respuesta = mvc.perform(post("/api/itinerarios/generar").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos))).andExpect(status().isOk()).andReturn();
        return UUID.fromString(respuesta.getResponse().getHeader("X-Itinerario-Id"));
    }
    private UUID crear(UUID generacion) throws Exception {
        var respuesta = mvc.perform(post("/api/viajes/desde-generacion/" + generacion).session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Viaje de prueba\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(respuesta.getResponse().getContentAsString()).get("id").asString());
    }
    private ViajeGuardadoDto abrir(UUID id) throws Exception {
        return json.readValue(mvc.perform(get("/api/viajes/" + id).session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), ViajeGuardadoDto.class);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder actualizar(UUID viaje, UUID generacion, long revision, String titulo) {
        return post("/api/viajes/" + viaje + "/desde-generacion/" + generacion).session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ActualizarViajeDto(titulo, revision)));
    }
    private int versiones(UUID viaje) {
        return jdbc.sql("SELECT count(*) FROM itinerario WHERE viaje_id = :id").param("id", viaje).query(Integer.class).single();
    }
    private ConfiguracionItinerarioDto conDistancia(double distancia) {
        var p = configuracion.preferencias();
        return new ConfiguracionItinerarioDto(configuracion.destino(), configuracion.disponibilidad(), "",
                new PreferenciasDto(p.intereses(), p.categoriasExcluidas(), p.subcategoriasExcluidas(), p.nivelGasto(), distancia), configuracion.ritmo());
    }
    private ItinerarioGeneradoDto plan(String descripcion) {
        var inicio = configuracion.disponibilidad().llegada();
        var bloques = new ArrayList<BloqueItinerarioDto>();
        int posicion = 0;
        for (var tipo : TipoBloqueItinerario.values()) {
            var desde = inicio.plusHours(posicion++);
            bloques.add(new BloqueItinerarioDto(tipo, desde, desde.plusSeconds(tipo == TipoBloqueItinerario.MARGEN ? 30 : 1200),
                    null, null, null, null, null, null, false, descripcion));
        }
        return new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(inicio.toLocalDate(), inicio, inicio.plusHours(8), bloques),
                new DiaItinerarioDto(inicio.toLocalDate().plusDays(1), inicio.plusDays(1), inicio.plusDays(1).plusHours(8), List.of())),
                List.of("Horario pendiente"), true, 0, 0, List.of(), false);
    }
}
