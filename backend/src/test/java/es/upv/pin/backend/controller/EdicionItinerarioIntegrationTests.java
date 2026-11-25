package es.upv.pin.backend.controller;

import es.upv.pin.backend.PostgresTest;
import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.service.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EdicionItinerarioIntegrationTests extends PostgresTest {
    private static final String CLAVE = "Prueba de edición 2026!";
    private static final AtomicInteger IPS = new AtomicInteger();
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder contrasenas;
    @Autowired ViajeService escritura;
    @Autowired ConsultaViajeService consultas;
    @MockitoBean OpenRouteServiceClient cliente;
    private UUID usuario;
    private String correo;
    private MockHttpSession sesion;
    private UUID viaje;
    private LocalDate fecha;
    private ConfiguracionItinerarioDto config;
    private ItinerarioGeneradoDto plan;
    private CandidatoEvaluadoDto a;
    private CandidatoEvaluadoDto b;
    private CandidatoEvaluadoDto c;

    @BeforeEach
    void preparar() throws Exception {
        usuario = crearUsuario();
        correo = usuario + "@example.test";
        sesion = entrar(correo);
        fecha = LocalDate.now().plusDays(30).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        config = new ConfiguracionItinerarioDto(new DestinoDto("valencia", "Valencia", "Valencia", "España", 39.47, -0.37),
                new FechaHoraDto(fecha.atTime(10, 0), fecha.atTime(18, 0), 1), "",
                new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), List.of(), NivelGasto.MODERADO, 3.0),
                new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10));
        a = candidato("a", "Museo Alfa", "Mo 10:00-12:00", 39.471, -0.371, CostLevel.FREE);
        b = candidato("b", "Museo Beta", "24/7", 39.472, -0.372, CostLevel.FREE);
        c = candidato("c", "Museo Alternativo", "24/7", 39.473, -0.373, CostLevel.FREE);
        var caro = candidato("caro", "Museo Caro", "24/7", 39.474, -0.374, CostLevel.HIGH);
        var cerrado = candidato("cerrado", "Museo Cerrado", "Mo off", 39.475, -0.375, CostLevel.FREE);
        var lejos = candidato("lejos", "Museo Lejano", "24/7", 40.8, -3.7, CostLevel.FREE);
        var bloques = List.of(traslado("referencia:inicio", "a", 10, 0, 10, 10),
                visita(a, 10, 10, 11, 10), traslado("a", "b", 11, 10, 11, 20),
                visita(b, 11, 20, 12, 20), simple(TipoBloqueItinerario.TIEMPO_LIBRE, 12, 20, 13, 0),
                simple(TipoBloqueItinerario.COMIDA, 13, 0, 15, 0), simple(TipoBloqueItinerario.TIEMPO_LIBRE, 15, 0, 18, 0));
        plan = new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(fecha, fecha.atTime(10, 0), fecha.atTime(18, 0), bloques)),
                List.of(), false, 6, 6, List.of(a, b, c, caro, cerrado, lejos), false);
        viaje = escritura.guardarNuevo(usuario, new GuardarViajeDto("Viaje editable"), config, plan);
        when(cliente.calcularDesde(any(), anyList())).thenAnswer(invocacion -> {
            List<PuntoRutaDto> destinos = invocacion.getArgument(1);
            return new RespuestaMatrizOrsDto(List.of(Collections.nCopies(destinos.size(), 500.0)),
                    List.of(Collections.nCopies(destinos.size(), 600.0)));
        });
    }

    @Test
    void eliminarPrevisualizaSinGuardarYSeRecuperaTrasVolverAEntrar() throws Exception {
        var anterior = abrir();
        var borrador = iniciar();
        UUID actividad = actividad(anterior, "b");
        var actualizado = cambiar(borrador, "/visitas/" + actividad + "/eliminar", new EliminarActividadDto(1L));

        assertEquals(anterior, abrir());
        assertEquals(List.of("a"), ids(actualizado.propuesta().itinerario()));
        assertTrue(actualizado.propuesta().puedeGuardar());
        assertTrue(actualizado.modificada());
        guardar(actualizado, false).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        mvc.perform(post("/api/auth/logout").session(sesion).with(csrf())).andExpect(status().isOk());
        sesion = entrar(correo);
        assertEquals(List.of("a"), ids(abrir().itinerario()));
        assertEquals(2, versiones());
        assertTrue(jdbc.sql("SELECT EXISTS(SELECT 1 FROM actividad WHERE id=:id)")
                .param("id", actividad).query(Boolean.class).single());
    }

    @Test
    void reordenarDetectaElCierreYPermiteRestaurarUnOrdenValido() throws Exception {
        var anterior = abrir();
        UUID idA = actividad(anterior, "a"), idB = actividad(anterior, "b");
        var cambiado = cambiar(iniciar(), "/reordenar", new ReordenarActividadesDto(1L, fecha, List.of(idB, idA)));

        assertFalse(cambiado.propuesta().puedeGuardar());
        assertFalse(cambiado.propuesta().incidencias().stream()
                .anyMatch(i -> i.mensaje().equals("Visita incompatible con el horario de la fuente: a")));
        assertTrue(cambiado.propuesta().incidencias().stream().anyMatch(i -> i.codigo() == IncidenciaEdicionDto.Codigo.HORARIO_INCOMPATIBLE));
        guardar(cambiado, true).andExpect(status().isConflict());
        assertEquals(anterior, abrir());
        var restaurado = cambiar(cambiado, "/reordenar", new ReordenarActividadesDto(1L, fecha, List.of(idA, idB)));
        assertTrue(restaurado.propuesta().puedeGuardar());
        assertTrue(restaurado.propuesta().incidencias().isEmpty());
        guardar(restaurado, false).andExpect(status().isOk());
        assertEquals(List.of("a", "b"), ids(abrir().itinerario()));
    }

    @Test
    void alternativasNoModificaYLaSustitucionExigeElegirUnaOpcionComprobada() throws Exception {
        var anterior = abrir();
        UUID idB = actividad(anterior, "b");
        var borrador = iniciar();
        var respuesta = mvc.perform(peticion(borrador, "/visitas/" + idB + "/alternativas", new RevisionEdicionDto(1L)))
                .andExpect(status().isOk()).andReturn();
        var alternativas = json.readValue(respuesta.getResponse().getContentAsString(), AlternativasEdicionDto.class);
        assertEquals(List.of("c"), alternativas.candidatos().stream().map(opcion -> opcion.lugar().id()).toList());
        assertEquals(anterior, abrir()); // Cancelar en la interfaz no envía una sustitución.
        mvc.perform(peticion(borrador, "/visitas/" + idB + "/sustituir", new SustituirActividadDto(1L, "caro")))
                .andExpect(status().isBadRequest());
        var propuesta = cambiar(borrador, "/visitas/" + idB + "/sustituir", new SustituirActividadDto(1L, "c"));
        assertEquals(List.of("a", "c"), ids(propuesta.propuesta().itinerario()));
        guardar(propuesta, false).andExpect(status().isOk());
        assertEquals(List.of("a", "c"), ids(abrir().itinerario()));
    }

    @Test
    void eliminarObligatorioExigeAceptarElAvisoYConservaSuPrioridad() throws Exception {
        var obligatoria = new ConfiguracionItinerarioDto(config.destino(), config.disponibilidad(), "", config.preferencias(),
                config.ritmo(), null, List.of(new SeleccionLugarDto(a.lugar(), EstadoLugar.OBLIGATORIO)));
        viaje = escritura.guardarNuevo(usuario, new GuardarViajeDto("Con obligatorio"), obligatoria, plan);
        var anterior = abrir();
        UUID idA = actividad(anterior, "a");
        var borrador = iniciar();
        mvc.perform(peticion(borrador, "/visitas/" + idA + "/alternativas", new RevisionEdicionDto(1L)))
                .andExpect(status().isBadRequest());
        var propuesta = cambiar(borrador, "/visitas/" + idA + "/eliminar", new EliminarActividadDto(1L));
        assertTrue(propuesta.propuesta().puedeGuardar());
        assertTrue(propuesta.propuesta().incidencias().stream().anyMatch(i -> i.codigo() == IncidenciaEdicionDto.Codigo.OBLIGATORIO_INCUMPLIDO));
        guardar(propuesta, false).andExpect(status().isBadRequest());
        assertEquals(anterior, abrir());
        guardar(propuesta, true).andExpect(status().isOk());
        assertEquals(EstadoLugar.OBLIGATORIO, abrir().configuracion().selecciones().getFirst().estado());
        assertTrue(abrir().itinerario().advertencias().stream().anyMatch(aviso -> aviso.contains("obligatorio")));
    }

    @Test
    void rechazaPropuestasAntiguasDeOtraPestanaYRevisionesDesactualizadas() throws Exception {
        var anterior = abrir();
        var primera = iniciar();
        var segunda = iniciar();
        mvc.perform(peticion(primera, "/visitas/" + actividad(anterior, "b") + "/eliminar", new EliminarActividadDto(1L)))
                .andExpect(status().isConflict());
        var propuesta = cambiar(segunda, "/visitas/" + actividad(anterior, "b") + "/eliminar", new EliminarActividadDto(1L));
        escritura.actualizar(usuario, viaje, new ActualizarViajeDto("Cambio en otra sesión", 1L), config, plan);
        guardar(propuesta, false).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REVISION_DESACTUALIZADA"));
        assertEquals("Cambio en otra sesión", abrir().resumen().titulo());
        assertEquals(List.of("a", "b"), ids(abrir().itinerario()));
    }

    @Test
    void protegeTodosLosEndpointsConSesionCsrfYPropiedad() throws Exception {
        var anterior = abrir();
        var borrador = iniciar();
        var otraSesion = entrar(crearUsuario() + "@example.test");
        var acciones = Map.of("/visitas/" + actividad(anterior, "b") + "/eliminar", "{\"revision\":1}",
                "/reordenar", json.writeValueAsString(new ReordenarActividadesDto(1L, fecha, anterior.actividades().stream()
                        .filter(ref -> anterior.itinerario().dias().getFirst().bloques().get(ref.orden() - 1).tipo() == TipoBloqueItinerario.VISITA)
                        .map(ViajeGuardadoDto.ReferenciaActividadDto::id).toList())),
                "/visitas/" + actividad(anterior, "b") + "/alternativas", "{\"revision\":1}",
                "/visitas/" + actividad(anterior, "b") + "/sustituir", "{\"revision\":1,\"candidatoId\":\"c\"}",
                "/recalcular", "{\"revision\":1}",
                "/guardar", "{\"revision\":1,\"aceptarAvisos\":true}");
        for (var accion : acciones.entrySet()) {
            String ruta = base() + "/" + borrador.propuesta().propuestaId() + accion.getKey();
            mvc.perform(post(ruta).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(accion.getValue())).andExpect(status().isUnauthorized());
            mvc.perform(post(ruta).session(sesion).contentType(MediaType.APPLICATION_JSON).content(accion.getValue())).andExpect(status().isForbidden());
            mvc.perform(post(ruta).session(otraSesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(accion.getValue())).andExpect(status().isNotFound());
        }
        mvc.perform(post(base() + "/iniciar").session(otraSesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isNotFound());
        assertEquals(anterior, abrir());
        verifyNoInteractions(cliente);
    }

    @Test
    void falloSqlAlGuardarConservaLaPropuestaYRevierteTodaLaEscritura() throws Exception {
        var anterior = abrir();
        var propuesta = cambiar(iniciar(), "/visitas/" + actividad(anterior, "b") + "/eliminar", new EliminarActividadDto(1L));
        String nombre = "fallo_edicion_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.sql("CREATE FUNCTION " + nombre + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.orden=2 AND EXISTS(SELECT 1 FROM itinerario WHERE id=NEW.itinerario_id AND viaje_id='" + viaje + "') THEN "
                + "RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='SQL privado'; END IF; RETURN NEW; END $$").update();
        jdbc.sql("CREATE TRIGGER " + nombre + " BEFORE INSERT ON actividad FOR EACH ROW EXECUTE FUNCTION " + nombre + "()").update();
        try {
            var respuesta = guardar(propuesta, false).andExpect(status().isServiceUnavailable()).andReturn();
            assertFalse(respuesta.getResponse().getContentAsString().contains("SQL privado"));
            assertEquals(anterior, abrir());
            assertEquals(1, versiones());
        } finally {
            jdbc.sql("DROP TRIGGER " + nombre + " ON actividad").update();
            jdbc.sql("DROP FUNCTION " + nombre + "()").update();
        }
        guardar(propuesta, false).andExpect(status().isOk());
        assertEquals(2, versiones());
    }

    @Test
    void unFalloDeRutasNoPermiteGuardarNiDestruyeElViajeOriginal() throws Exception {
        var anterior = abrir();
        when(cliente.calcularDesde(any(), anyList())).thenThrow(new IllegalStateException("Proveedor simulado no disponible"));
        var propuesta = cambiar(iniciar(), "/visitas/" + actividad(anterior, "a") + "/eliminar", new EliminarActividadDto(1L));
        assertFalse(propuesta.propuesta().puedeGuardar());
        assertTrue(propuesta.propuesta().incidencias().stream().anyMatch(i -> i.codigo() == IncidenciaEdicionDto.Codigo.CONSULTA_RUTAS_FALLIDA));
        guardar(propuesta, true).andExpect(status().isConflict());
        assertEquals(anterior, abrir());
        doAnswer(invocacion -> {
            List<PuntoRutaDto> destinos = invocacion.getArgument(1);
            return new RespuestaMatrizOrsDto(List.of(Collections.nCopies(destinos.size(), 500.0)),
                    List.of(Collections.nCopies(destinos.size(), 600.0)));
        }).when(cliente).calcularDesde(any(), anyList());
        var recuperada = cambiar(propuesta, "/recalcular", new RevisionEdicionDto(1L));
        assertTrue(recuperada.propuesta().puedeGuardar());
        guardar(recuperada, false).andExpect(status().isOk());
        assertEquals(List.of("b"), ids(abrir().itinerario()));
    }

    private EstadoEdicionDto iniciar() throws Exception {
        var respuesta = mvc.perform(post(base() + "/iniciar").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1}")).andExpect(status().isOk()).andReturn();
        return json.readValue(respuesta.getResponse().getContentAsString(), EstadoEdicionDto.class);
    }
    private EstadoEdicionDto cambiar(EstadoEdicionDto estado, String accion, Object cuerpo) throws Exception {
        var respuesta = mvc.perform(peticion(estado, accion, cuerpo)).andExpect(status().isOk()).andReturn();
        assertEquals("no-store", respuesta.getResponse().getHeader("Cache-Control"));
        return json.readValue(respuesta.getResponse().getContentAsString(), EstadoEdicionDto.class);
    }
    private MockHttpServletRequestBuilder peticion(EstadoEdicionDto estado, String accion, Object cuerpo) {
        return post(base() + "/" + estado.propuesta().propuestaId() + accion).session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
    }
    private org.springframework.test.web.servlet.ResultActions guardar(EstadoEdicionDto estado, boolean avisos) throws Exception {
        return mvc.perform(peticion(estado, "/guardar", new ConfirmarEdicionDto(1L, avisos)));
    }
    private String base() { return "/api/viajes/" + viaje + "/edicion"; }
    private ViajeGuardadoDto abrir() { return consultas.recuperar(usuario, viaje); }
    private int versiones() { return jdbc.sql("SELECT count(*) FROM itinerario WHERE viaje_id=:id").param("id", viaje).query(Integer.class).single(); }
    private UUID crearUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO usuario(id,nombre,correo,password_hash,correo_verificado) VALUES(:id,'Edición',:correo,:hash,true)")
                .param("id", id).param("correo", id + "@example.test").param("hash", contrasenas.encode(CLAVE)).update();
        return id;
    }
    private MockHttpSession entrar(String correoCuenta) throws Exception {
        var seguridad = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var actual = (MockHttpSession) seguridad.getRequest().getSession(false);
        String token = json.readTree(seguridad.getResponse().getContentAsString()).get("token").asString();
        mvc.perform(post("/api/auth/login").session(actual).header("X-CSRF-TOKEN", token)
                .with(req -> { req.setRemoteAddr("203.0.113." + IPS.incrementAndGet()); return req; })
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo", correoCuenta, "contrasena", CLAVE))))
                .andExpect(status().isOk());
        return actual;
    }
    private static UUID actividad(ViajeGuardadoDto viaje, String lugar) {
        var dia = viaje.itinerario().dias().getFirst();
        int orden = IntStreamHelper.indice(dia.bloques(), lugar) + 1;
        return viaje.actividades().stream().filter(a -> a.dia().equals(dia.fecha()) && a.orden() == orden).findFirst().orElseThrow().id();
    }
    private static List<String> ids(ItinerarioGeneradoDto plan) { return plan.dias().stream().flatMap(d -> d.bloques().stream())
            .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).map(b -> b.candidato().lugar().id()).toList(); }
    private CandidatoEvaluadoDto candidato(String id, String nombre, String horario, double lat, double lon, CostLevel coste) {
        return new CandidatoEvaluadoDto(new LugarDto(id, nombre, "Valencia", lat, lon,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), horario), 80, 60, "Arte", "Visita cultural",
                coste, "Consulta el precio antes de ir", List.of(), horario, "Geoapify Places", "LOCAL",
                CostConfidence.CONFIRMED, ScheduleStatus.VERIFIED, 0.0, 0.0, "EUR", "Descripción de prueba", "Prueba", 80);
    }
    private BloqueItinerarioDto visita(CandidatoEvaluadoDto c, int h1, int m1, int h2, int m2) {
        return new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, fecha.atTime(h1,m1), fecha.atTime(h2,m2), c,
                null, null, null, null, null, false, "Visita");
    }
    private BloqueItinerarioDto traslado(String origen, String destino, int h1, int m1, int h2, int m2) {
        return new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO, fecha.atTime(h1,m1), fecha.atTime(h2,m2), null,
                origen, destino, 500.0, 600.0, "openrouteservice", false, "Traslado");
    }
    private BloqueItinerarioDto simple(TipoBloqueItinerario tipo, int h1, int m1, int h2, int m2) {
        return new BloqueItinerarioDto(tipo, fecha.atTime(h1,m1), fecha.atTime(h2,m2), null,
                null, null, null, null, null, false, "Pausa");
    }
    private static class IntStreamHelper {
        private static int indice(List<BloqueItinerarioDto> bloques, String lugar) {
            for (int i = 0; i < bloques.size(); i++) if (bloques.get(i).candidato() != null
                    && bloques.get(i).candidato().lugar().id().equals(lugar)) return i;
            throw new IllegalArgumentException("Visita no encontrada");
        }
    }
}
