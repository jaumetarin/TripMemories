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
        var acciones = Map.of("/visitas/" + actividad(anterior, "b") + "/restaurar", "{\"revision\":1}", "/recuperar", "{\"revision\":1}", "/visitas/" + actividad(anterior, "b") + "/eliminar", "{\"revision\":1}",
                "/reordenar", json.writeValueAsString(new ReordenarActividadesDto(1L, fecha, anterior.actividades().stream()
                        .filter(ref -> anterior.itinerario().dias().getFirst().bloques().get(ref.orden() - 1).tipo() == TipoBloqueItinerario.VISITA)
                        .map(ViajeGuardadoDto.ReferenciaActividadDto::id).toList())),
                "/visitas/" + actividad(anterior, "b") + "/alternativas", "{\"revision\":1}",
                "/visitas/" + actividad(anterior, "b") + "/sustituir", "{\"revision\":1,\"candidatoId\":\"c\"}",
                "/recalcular", "{\"revision\":1}",
                "/guardar", "{\"revision\":1,\"aceptarAvisos\":true}");
        for (var accion : acciones.entrySet()) {
            String ruta = accion.getKey().equals("/recuperar") ? base()+"/recuperar" : base() + "/" + borrador.propuesta().propuestaId() + accion.getKey();
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

    @Test
    void unFalloEnRutasNoUsadasNoBloqueaUnDiaValido() throws Exception {
        var r = new OpcionComidaDto("restaurante", "Restaurante", 39.474, -0.374, "Valencia", "24/7", "Geoapify", null, null);
        var bloques = new ArrayList<>(plan.dias().getFirst().bloques().subList(0, 4));
        bloques.add(traslado("b", "restaurante", 12, 20, 12, 30));
        bloques.add(simple(TipoBloqueItinerario.TIEMPO_LIBRE, 12, 30, 13, 0));
        bloques.add(new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA, fecha.atTime(13,0), fecha.atTime(15,0), null,
                null,null,null,null,null,false,"Comida",r,List.of()));
        bloques.add(simple(TipoBloqueItinerario.TIEMPO_LIBRE,15,0,18,0));
        plan = new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(fecha,fecha.atTime(10,0),fecha.atTime(18,0),bloques)),
                List.of(),false,6,6,plan.candidatos(),false);
        viaje = escritura.guardarNuevo(usuario,new GuardarViajeDto("Restaurante"),config,plan);
        doAnswer(inv -> {
            PuntoRutaDto origen=inv.getArgument(0);
            if (origen.id().equals("restaurante")) throw new IllegalStateException("Fallo en rutas no utilizadas");
            List<PuntoRutaDto> destinos=inv.getArgument(1);
            return new RespuestaMatrizOrsDto(List.of(Collections.nCopies(destinos.size(),500.0)),List.of(Collections.nCopies(destinos.size(),600.0)));
        }).when(cliente).calcularDesde(any(),anyList());
        var anterior=abrir();
        var propuesta=cambiar(iniciar(),"/reordenar",new ReordenarActividadesDto(1L,fecha,List.of(actividad(anterior,"a"),actividad(anterior,"b"))));
        assertEquals(List.of("a","b"),ids(propuesta.propuesta().itinerario()));
        assertTrue(new ValidadorPlanService().validar(propuesta.propuesta().itinerario().dias(),config.preferencias()).incidencias().isEmpty());
        assertTrue(propuesta.propuesta().puedeGuardar());
        assertTrue(propuesta.propuesta().incidencias().isEmpty());
        guardar(propuesta,false).andExpect(status().isOk());
    }

    @Test
    void unConflictoEnOtroDiaNoOcultaAlternativas() throws Exception {
        var d=candidato("d","Otra alternativa","24/7",39.474,-0.374,CostLevel.FREE);
        var dia2=fecha.plusDays(1);
        var bloque=new BloqueItinerarioDto(TipoBloqueItinerario.VISITA,dia2.atTime(10,0),dia2.atTime(11,0),c,
                null,null,null,null,null,false,"Visita");
        var dias=List.of(plan.dias().getFirst(),new DiaItinerarioDto(dia2,dia2.atTime(10,0),dia2.atTime(18,0),List.of(bloque)));
        plan=new ItinerarioGeneradoDto(dias,List.of(),false,4,4,List.of(a,b,c,d),false);
        config=new ConfiguracionItinerarioDto(config.destino(),new FechaHoraDto(fecha.atTime(10,0),dia2.atTime(18,0),2),"",config.preferencias(),config.ritmo());
        viaje=escritura.guardarNuevo(usuario,new GuardarViajeDto("Dos dias"),config,plan);
        var anterior=abrir();
        var idC=anterior.actividades().stream().filter(ref->ref.dia().equals(dia2)).findFirst().orElseThrow().id();
        var inicio=iniciar();
        var buenas=mvc.perform(peticion(inicio,"/visitas/"+idC+"/alternativas",new RevisionEdicionDto(1L))).andExpect(status().isOk()).andReturn();
        assertEquals(List.of("d"),json.readValue(buenas.getResponse().getContentAsString(),AlternativasEdicionDto.class).candidatos().stream().map(x->x.lugar().id()).toList());
        var conflicto=cambiar(inicio,"/reordenar",new ReordenarActividadesDto(1L,fecha,List.of(actividad(anterior,"b"),actividad(anterior,"a"))));
        var ocultas=mvc.perform(peticion(conflicto,"/visitas/"+idC+"/alternativas",new RevisionEdicionDto(1L))).andExpect(status().isOk()).andReturn();
        assertEquals(List.of("d"),json.readValue(ocultas.getResponse().getContentAsString(),AlternativasEdicionDto.class).candidatos().stream().map(x->x.lugar().id()).toList());
        assertFalse(conflicto.propuesta().puedeGuardar());
    }


    @Test
    void bloqueaGuardarUnTrayectoDeDoceKilometros() throws Exception {
        doAnswer(inv -> {
            List<PuntoRutaDto> destinos=inv.getArgument(1);
            return new RespuestaMatrizOrsDto(List.of(Collections.nCopies(destinos.size(),12000.0)),List.of(Collections.nCopies(destinos.size(),2400.0)));
        }).when(cliente).calcularDesde(any(),anyList());
        var propuesta=cambiar(iniciar(),"/visitas/"+actividad(abrir(),"a")+"/eliminar",new EliminarActividadDto(1L));
        assertFalse(propuesta.propuesta().puedeGuardar());
        assertTrue(propuesta.propuesta().itinerario().dias().stream().flatMap(x->x.bloques().stream())
                .anyMatch(x->x.tipo()==TipoBloqueItinerario.TRASLADO && x.distanciaMetros()==12000.0));
        guardar(propuesta,false).andExpect(status().isConflict());
        assertTrue(propuesta.propuesta().incidencias().stream().anyMatch(i->i.codigo()==IncidenciaEdicionDto.Codigo.DISTANCIA_EXCEDIDA));
    }

    @Test
    void respetaLaDistanciaElegidaAunqueSeaInferiorADiezKilometros() throws Exception {
        doAnswer(inv -> {
            List<PuntoRutaDto> destinos=inv.getArgument(1);
            return new RespuestaMatrizOrsDto(List.of(Collections.nCopies(destinos.size(),3500.0)),List.of(Collections.nCopies(destinos.size(),2400.0)));
        }).when(cliente).calcularDesde(any(),anyList());
        var propuesta=cambiar(iniciar(),"/visitas/"+actividad(abrir(),"a")+"/eliminar",new EliminarActividadDto(1L));
        assertFalse(propuesta.propuesta().puedeGuardar());
        assertTrue(propuesta.propuesta().itinerario().dias().stream().flatMap(x->x.bloques().stream())
                .anyMatch(x->x.tipo()==TipoBloqueItinerario.TRASLADO && x.distanciaMetros()==3500.0));
        guardar(propuesta,false).andExpect(status().isConflict());
        assertTrue(propuesta.propuesta().incidencias().stream().anyMatch(i->i.codigo()==IncidenciaEdicionDto.Codigo.DISTANCIA_EXCEDIDA));
    }

    @Test
    void recuperaElBorradorTrasUnaRespuestaPerdida() throws Exception {
        var anterior=abrir();
        var inicial=iniciar();
        // Simula que el servidor procesa el cambio pero el navegador no recibe la respuesta.
        cambiar(inicial,"/visitas/"+actividad(anterior,"b")+"/eliminar",new EliminarActividadDto(1L));
        mvc.perform(peticion(inicial,"/recalcular",new RevisionEdicionDto(1L))).andExpect(status().isConflict());
        mvc.perform(peticion(inicial,"/guardar",new ConfirmarEdicionDto(1L,true))).andExpect(status().isConflict());
        var resultado=mvc.perform(post(base()+"/recuperar").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1}")).andExpect(status().isOk()).andReturn();
        var recuperada=json.readValue(resultado.getResponse().getContentAsString(),EstadoEdicionDto.class);
        assertTrue(recuperada.modificada());
        assertEquals(List.of("a"),ids(recuperada.propuesta().itinerario()));
        guardar(recuperada,false).andExpect(status().isOk());
    }


    @Test
    void conservaAvisosAntiguosSinMetadatosEnDiasIntactos() throws Exception {
        var incierta=candidato("incierta","Horario desconocido",null,39.474,-0.374,CostLevel.FREE);
        var dia2=fecha.plusDays(1);
        var bloque=new BloqueItinerarioDto(TipoBloqueItinerario.VISITA,dia2.atTime(10,0),dia2.atTime(11,0),incierta,
                null,null,null,null,null,true,"Horario pendiente de confirmar");
        String aviso="Edición: Confirma el horario de «Horario desconocido» antes de ir.";
        plan=new ItinerarioGeneradoDto(List.of(plan.dias().getFirst(),new DiaItinerarioDto(dia2,dia2.atTime(10,0),dia2.atTime(18,0),List.of(bloque))),
                List.of(aviso),true,3,3,List.of(a,b,incierta),false);
        config=new ConfiguracionItinerarioDto(config.destino(),new FechaHoraDto(fecha.atTime(10,0),dia2.atTime(18,0),2),"",config.preferencias(),config.ritmo());
        viaje=escritura.guardarNuevo(usuario,new GuardarViajeDto("Avisos"),config,plan);
        assertTrue(abrir().itinerario().advertencias().contains(aviso));
        var inicial=iniciar();
        assertTrue(inicial.propuesta().itinerario().advertencias().contains(aviso));
        var editada=cambiar(inicial,"/visitas/"+actividad(abrir(),"b")+"/eliminar",new EliminarActividadDto(1L));
        guardar(editada,false).andExpect(status().isOk());
        assertTrue(abrir().itinerario().advertencias().contains(aviso));
        assertTrue(abrir().itinerario().dias().getLast().bloques().getFirst().condicionada());
    }


    @Test
    void conservaAvisosEstructuradosYLosReemplazaAlRecalcularSuDia() throws Exception {
        var incierta=candidato("incierta","Horario desconocido",null,39.474,-0.374,CostLevel.FREE);
        var dia2=fecha.plusDays(1);
        var bloque=new BloqueItinerarioDto(TipoBloqueItinerario.VISITA,dia2.atTime(10,0),dia2.atTime(11,0),incierta,
                null,null,null,null,null,true,"Horario pendiente de confirmar");
        String aviso="Edición: Confirma el horario de «Horario desconocido» antes de ir.";
        plan=new ItinerarioGeneradoDto(List.of(plan.dias().getFirst(),new DiaItinerarioDto(dia2,dia2.atTime(10,0),dia2.atTime(18,0),List.of(bloque))),
                List.of(aviso),true,3,3,List.of(a,b,incierta),false,List.of(new IncidenciaEdicionDto(
                        IncidenciaEdicionDto.Nivel.AVISO,IncidenciaEdicionDto.Codigo.HORARIO_NO_CONFIRMADO,
                        dia2,null,"incierta",aviso.substring("Edici\u00f3n: ".length()))));
        config=new ConfiguracionItinerarioDto(config.destino(),new FechaHoraDto(fecha.atTime(10,0),dia2.atTime(18,0),2),"",config.preferencias(),config.ritmo());
        viaje=escritura.guardarNuevo(usuario,new GuardarViajeDto("Avisos"),config,plan);
        assertTrue(abrir().itinerario().advertencias().contains(aviso));
        var inicial=iniciar();
        assertTrue(inicial.propuesta().itinerario().advertencias().contains(aviso));
        var editada=cambiar(inicial,"/visitas/"+actividad(abrir(),"b")+"/eliminar",new EliminarActividadDto(1L));
        guardar(editada,true).andExpect(status().isOk());
        assertTrue(abrir().itinerario().advertencias().contains(aviso));
        assertTrue(abrir().itinerario().dias().getLast().bloques().getFirst().condicionada());
        assertEquals(dia2,abrir().itinerario().incidenciasEdicion().getFirst().dia());
        var reinicio=mvc.perform(post(base()+"/iniciar").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":2}")).andExpect(status().isOk()).andReturn();
        var siguiente=json.readValue(reinicio.getResponse().getContentAsString(),EstadoEdicionDto.class);
        UUID inciertaId=abrir().actividades().stream().filter(ref->ref.dia().equals(dia2)).findFirst().orElseThrow().id();
        var sinVisita=cambiar(siguiente,"/visitas/"+inciertaId+"/eliminar",new EliminarActividadDto(2L));
        assertFalse(sinVisita.propuesta().itinerario().advertencias().contains(aviso));
        assertTrue(sinVisita.propuesta().itinerario().incidenciasEdicion().isEmpty());
    }


    @Test
    void deshacerRecuperaSoloLaVisitaEliminadaYConservaLaSustitucion() throws Exception {
        var anterior = abrir();
        UUID idA = actividad(anterior, "a"), idB = actividad(anterior, "b");
        var eliminada = cambiar(iniciar(), "/visitas/" + idB + "/eliminar", new EliminarActividadDto(1L));
        assertEquals(idB, eliminada.eliminadas().getFirst().visita().actividadId());
        var consulta = mvc.perform(peticion(eliminada, "/visitas/" + idA + "/alternativas", new RevisionEdicionDto(1L)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(json.readValue(consulta.getResponse().getContentAsString(), AlternativasEdicionDto.class)
                .candidatos().stream().anyMatch(candidato -> candidato.lugar().id().equals("c")));
        var sustituida = cambiar(eliminada, "/visitas/" + idA + "/sustituir", new SustituirActividadDto(1L,"c"));
        assertEquals(1, sustituida.eliminadas().size());
        var restaurada = cambiar(sustituida, "/visitas/" + idB + "/restaurar", new RevisionEdicionDto(1L));
        assertTrue(restaurada.eliminadas().isEmpty());
        assertEquals(List.of("c", "b"), ids(restaurada.propuesta().itinerario()));
        assertEquals(anterior, abrir());
        mvc.perform(peticion(sustituida, "/visitas/" + idB + "/restaurar", new RevisionEdicionDto(1L)))
                .andExpect(status().isConflict());
        mvc.perform(peticion(restaurada, "/visitas/" + idB + "/restaurar", new RevisionEdicionDto(1L)))
                .andExpect(status().isBadRequest());
        guardar(restaurada, false).andExpect(status().isOk());
        assertEquals(List.of("c", "b"), ids(abrir().itinerario()));
    }

    @Test
    void deshacerVariasEliminacionesRestauraElOrdenYElObligatorio() throws Exception {
        var obligatoria = new ConfiguracionItinerarioDto(config.destino(), config.disponibilidad(), "", config.preferencias(),
                config.ritmo(), null, List.of(new SeleccionLugarDto(a.lugar(), EstadoLugar.OBLIGATORIO)));
        viaje = escritura.guardarNuevo(usuario, new GuardarViajeDto("Restaurar obligatorio"), obligatoria, plan);
        var anterior = abrir();
        UUID idA = actividad(anterior, "a"), idB = actividad(anterior, "b");
        var sinA = cambiar(iniciar(), "/visitas/" + idA + "/eliminar", new EliminarActividadDto(1L));
        var vacio = cambiar(sinA, "/visitas/" + idB + "/eliminar", new EliminarActividadDto(1L));
        assertEquals(2, vacio.eliminadas().size());
        var recuperacion = mvc.perform(post(base()+"/recuperar").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1}")).andExpect(status().isOk()).andReturn();
        assertEquals(2, json.readValue(recuperacion.getResponse().getContentAsString(),EstadoEdicionDto.class).eliminadas().size());
        var conA = cambiar(vacio, "/visitas/" + idA + "/restaurar", new RevisionEdicionDto(1L));
        assertTrue(conA.dias().getFirst().visitas().getFirst().obligatorio());
        assertTrue(conA.propuesta().incidencias().stream().noneMatch(i -> i.codigo()==IncidenciaEdicionDto.Codigo.OBLIGATORIO_INCUMPLIDO));
        var conAmbas = cambiar(conA, "/visitas/" + idB + "/restaurar", new RevisionEdicionDto(1L));
        assertEquals(List.of("a", "b"), ids(conAmbas.propuesta().itinerario()));
        assertEquals(List.of(idA,idB), conAmbas.dias().getFirst().visitas().stream().map(EstadoEdicionDto.VisitaEditableDto::actividadId).toList());
        assertTrue(conAmbas.propuesta().puedeGuardar());
    }

    @Test
    void deshacerTresVisitasEnOtroOrdenConservaElOrdenOriginal() throws Exception {
        var bloques = new ArrayList<>(plan.dias().getFirst().bloques());
        bloques.add(visita(c,15,20,16,20));
        // Eliminar huecos del final del plan de prueba para incluir la tercera visita.
        bloques.removeIf(x -> x.tipo()==TipoBloqueItinerario.TIEMPO_LIBRE && x.inicio().getHour()==15);
        bloques.sort(Comparator.comparing(BloqueItinerarioDto::inicio));
        plan = new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(fecha,fecha.atTime(10,0),fecha.atTime(18,0),bloques)),
                List.of(),false,3,3,List.of(a,b,c),false);
        viaje = escritura.guardarNuevo(usuario,new GuardarViajeDto("Tres visitas"),config,plan);
        var anterior=abrir();
        UUID idA=actividad(anterior,"a"), idB=actividad(anterior,"b"), idC=actividad(anterior,"c");
        var estado=cambiar(iniciar(),"/visitas/"+idA+"/eliminar",new EliminarActividadDto(1L));
        estado=cambiar(estado,"/visitas/"+idB+"/eliminar",new EliminarActividadDto(1L));
        estado=cambiar(estado,"/visitas/"+idC+"/eliminar",new EliminarActividadDto(1L));
        estado=cambiar(estado,"/visitas/"+idA+"/restaurar",new RevisionEdicionDto(1L));
        estado=cambiar(estado,"/visitas/"+idC+"/restaurar",new RevisionEdicionDto(1L));
        estado=cambiar(estado,"/visitas/"+idB+"/restaurar",new RevisionEdicionDto(1L));
        assertEquals(List.of("a","b","c"),estado.dias().getFirst().visitas().stream().map(x->x.candidato().lugar().id()).toList());
        assertTrue(estado.propuesta().puedeGuardar());
        guardar(estado,false).andExpect(status().isOk());
    }

    @Test
    void comidaAlInicioPermiteEditarCuandoCoincideConElPuntoInicial() throws Exception {
        var r=new OpcionComidaDto("restaurante","Restaurante en el alojamiento",39.47,-0.37,"Valencia","24/7","Geoapify",null,null);
        var comida=new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA,fecha.atTime(13,30),fecha.atTime(15,0),null,
                null,null,null,null,null,true,"Comida",r,List.of());
        var bloques=List.of(comida,traslado("restaurante","b",15,0,15,10),visita(b,15,20,16,20));
        plan=new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(fecha,fecha.atTime(13,30),fecha.atTime(18,0),bloques)),
                List.of(),false,2,2,List.of(b,c),false);
        config=new ConfiguracionItinerarioDto(config.destino(),new FechaHoraDto(fecha.atTime(13,30),fecha.atTime(18,0),1),"",config.preferencias(),config.ritmo());
        viaje=escritura.guardarNuevo(usuario,new GuardarViajeDto("Inicio durante comida"),config,plan);
        var anterior=abrir();
        assertTrue(new ValidadorPlanService().validar(anterior.itinerario().dias(),config.preferencias()).incidencias().isEmpty());
        var estado=cambiar(iniciar(),"/visitas/"+actividad(anterior,"b")+"/eliminar",new EliminarActividadDto(1L));
        assertTrue(estado.propuesta().puedeGuardar());
        guardar(estado,false).andExpect(status().isOk());
        assertTrue(estado.propuesta().incidencias().isEmpty());
        assertEquals("restaurante",abrir().itinerario().dias().getFirst().bloques().getFirst().restaurante().id());
    }


    private EstadoEdicionDto iniciar() throws Exception {
        var respuesta = mvc.perform(post(base() + "/iniciar").session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1}")).andExpect(status().isOk()).andReturn();
        return json.readValue(respuesta.getResponse().getContentAsString(), EstadoEdicionDto.class);
    }

    private void geometriaDisponible() {
        doAnswer(i -> {
            PuntoRutaDto origen = i.getArgument(0), destino = i.getArgument(1);
            return new GeometriaTramoDto(List.of(List.of(origen.longitud(), origen.latitud()),
                    List.of(destino.longitud(), destino.latitud())), "openrouteservice");
        }).when(cliente).calcularGeometria(any(), any());
    }
    private TrazadosDiaDto trazados(String ruta, long revision) throws Exception {
        var resultado = mvc.perform(post(ruta).session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new SolicitudTrazadosDto(fecha, revision))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return json.readValue(resultado.getResponse().getContentAsString(), TrazadosDiaDto.class);
    }

    @Test void guardaTrazadosYLosRecuperaEnOtraSesionSinCambiarLaRevisionNiElPlan() throws Exception {
        geometriaDisponible();
        var antes = abrir();
        var obtenidos = trazados("/api/viajes/" + viaje + "/trazados", 1);
        assertEquals(2, obtenidos.tramos().size());
        assertEquals(2, abrir().itinerario().trazados().size());
        assertEquals(antes.itinerario().dias(), abrir().itinerario().dias());
        assertEquals(1, abrir().resumen().revision());
        assertEquals(1, versiones());
        mvc.perform(post("/api/auth/logout").session(sesion).with(csrf())).andExpect(status().isOk());
        sesion = entrar(correo);
        assertEquals(obtenidos, trazados("/api/viajes/" + viaje + "/trazados", 1));
        verify(cliente, times(2)).calcularGeometria(any(), any());
    }

    @Test void alEliminarUnaVisitaRetiraSusTrazadosYSoloPersisteElBorradorAlGuardar() throws Exception {
        geometriaDisponible();
        var original = trazados("/api/viajes/" + viaje + "/trazados", 1);
        var anterior = abrir();
        var inicial = iniciar();
        var cambiado = cambiar(inicial, "/visitas/" + actividad(anterior, "a") + "/eliminar", new EliminarActividadDto(1L));
        assertTrue(cambiado.propuesta().itinerario().trazados().isEmpty());
        assertEquals(2, abrir().itinerario().trazados().size());
        var actuales = trazados(base() + "/" + cambiado.propuesta().propuestaId() + "/trazados", 1);
        assertEquals(1, actuales.tramos().size());
        assertEquals("b", actuales.tramos().getFirst().destinoId());
        assertTrue(original.tramos().stream().noneMatch(t -> t.clave().equals(actuales.tramos().getFirst().clave())));
        guardar(cambiado, true).andExpect(status().isOk());
        assertEquals(1, abrir().itinerario().trazados().size());
        assertEquals(Set.of(actuales.tramos().getFirst().clave()), abrir().itinerario().trazados().keySet());
    }

    @Test void noConsultaGeometriasParaUnaPropuestaObsoletaOtroUsuarioONingunCsrf() throws Exception {
        var inicial = iniciar();
        var cambiado = cambiar(inicial, "/visitas/" + actividad(abrir(), "b") + "/eliminar", new EliminarActividadDto(1L));
        var cuerpo = json.writeValueAsString(new SolicitudTrazadosDto(fecha, 1L));
        mvc.perform(post(base() + "/" + inicial.propuesta().propuestaId() + "/trazados")
                .session(sesion).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isConflict());
        mvc.perform(post("/api/viajes/" + viaje + "/trazados").session(sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        var otro = entrar(crearUsuario() + "@example.test");
        mvc.perform(post("/api/viajes/" + viaje + "/trazados").session(otro).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        mvc.perform(post(base() + "/" + cambiado.propuesta().propuestaId() + "/trazados").session(otro).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        verify(cliente, never()).calcularGeometria(any(), any());
    }

    @Test void geometriaNoDisponibleMantieneElPlanYPermiteReintentar() throws Exception {
        var antes = abrir();
        when(cliente.calcularGeometria(any(), any())).thenThrow(new IllegalStateException("Proveedor no disponible"));
        var incompleto = trazados("/api/viajes/" + viaje + "/trazados", 1);
        assertTrue(incompleto.tramos().stream().allMatch(t -> t.estado().equals("NO_DISPONIBLE") && t.geometria() == null));
        assertEquals(antes.itinerario().dias(), abrir().itinerario().dias());
        assertTrue(abrir().itinerario().trazados().isEmpty());
        geometriaDisponible();
        assertTrue(trazados("/api/viajes/" + viaje + "/trazados", 1).tramos().stream().allMatch(t -> t.estado().equals("DISPONIBLE")));
    }

    @Test void admiteUnViajeConElCampoOpcionalDeTrazadosNulo() throws Exception {
        jdbc.sql("UPDATE itinerario SET datos_generacion=jsonb_set(datos_generacion,'{trazados}','null'::jsonb) WHERE viaje_id=:id")
                .param("id", viaje).update();
        assertTrue(abrir().itinerario().trazados().isEmpty());
        geometriaDisponible();
        trazados("/api/viajes/" + viaje + "/trazados", 1);
        assertEquals(2, abrir().itinerario().trazados().size());
    }

    @Test void reordenarCambiaElSentidoDeLosTramosYNoReutilizaGeometriasInversas() throws Exception {
        a = candidato("a", "Museo Alfa", "24/7", 39.471, -.371, CostLevel.FREE);
        var bloques = plan.dias().getFirst().bloques().stream()
                .map(bloque -> bloque.candidato() != null && bloque.candidato().lugar().id().equals("a")
                        ? visita(a, 10, 10, 11, 10) : bloque).toList();
        plan = new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(fecha, fecha.atTime(10, 0), fecha.atTime(18, 0), bloques)),
                List.of(), false, 3, 3, List.of(a, b, c), false);
        viaje = escritura.guardarNuevo(usuario, new GuardarViajeDto("Orden flexible"), config, plan);
        geometriaDisponible();
        var originales = trazados("/api/viajes/" + viaje + "/trazados", 1);
        var anterior = abrir();
        var cambio = cambiar(iniciar(), "/reordenar", new ReordenarActividadesDto(1L, fecha,
                List.of(actividad(anterior, "b"), actividad(anterior, "a"))));
        assertTrue(cambio.propuesta().puedeGuardar());
        assertTrue(cambio.propuesta().itinerario().trazados().isEmpty());
        var nuevos = trazados(base() + "/" + cambio.propuesta().propuestaId() + "/trazados", 1);
        assertEquals(List.of("b", "a"), nuevos.tramos().stream().map(TrazadosDiaDto.TramoDto::destinoId).toList());
        assertTrue(nuevos.tramos().stream().noneMatch(n -> originales.tramos().stream().anyMatch(o -> o.clave().equals(n.clave()))));
        guardar(cambio, false).andExpect(status().isOk());
        assertEquals(TrazadosService.disponibles(nuevos), abrir().itinerario().trazados());
        verify(cliente, times(4)).calcularGeometria(any(), any());
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
