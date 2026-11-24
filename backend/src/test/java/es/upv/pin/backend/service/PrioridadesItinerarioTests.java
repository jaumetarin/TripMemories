package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.exception.ObligatoriosPendientesException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;
import static es.upv.pin.backend.model.CategoriaTuristica.*;

class PrioridadesItinerarioTests {
    private final ConfiguracionItinerarioService configuraciones = mock(ConfiguracionItinerarioService.class);
    private final EvaluacionCandidatosService evaluacion = mock(EvaluacionCandidatosService.class);
    private final DesplazamientoService rutas = mock(DesplazamientoService.class);
    private final GeneracionItinerarioService servicio = new GeneracionItinerarioService(configuraciones, evaluacion, rutas);

    private CandidatoEvaluadoDto candidato(String id, CategoriaTuristica categoria, int prioridad) {
        return candidato(id, categoria, prioridad, null, false, 0);
    }
    private CandidatoEvaluadoDto candidato(String id, CategoriaTuristica categoria, int prioridad, String horario, boolean documentado, int idiomas) {
        var lugar = new LugarDto(id, id, "Ciudad", 40.4, -3.7, List.of(categoria), List.of(), horario,
                TipoLugar.OTRO, documentado, idiomas);
        return new CandidatoEvaluadoDto(lugar, prioridad, 60, "Visita", "Motivo", CostLevel.UNKNOWN,
                null, List.of(), horario, horario == null ? null : "Geoapify / OpenStreetMap", "LOCAL",
                CostConfidence.UNKNOWN, ScheduleStatus.UNVERIFIED, null, null, null, null, null, null);
    }
    private ConfiguracionItinerarioDto viaje(LocalDateTime inicio, LocalDateTime fin, List<CategoriaTuristica> intereses,
            List<SeleccionLugarDto> selecciones) {
        var config = new ConfiguracionItinerarioDto(new DestinoDto("ciudad", "Ciudad", null, "España", 40.4, -3.7),
                new FechaHoraDto(inicio, fin, (int) java.time.temporal.ChronoUnit.DAYS.between(inicio.toLocalDate(), fin.toLocalDate()) + 1), "",
                new PreferenciasDto(intereses, List.of(), List.of(), NivelGasto.SIN_PREFERENCIA, 3.0),
                new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 240, 15, 0), selecciones);
        when(configuraciones.validar(config)).thenReturn(config);
        return config;
    }
    private ConfiguracionItinerarioDto viaje(int horas, List<CategoriaTuristica> intereses, SeleccionLugarDto... selecciones) {
        var inicio = LocalDateTime.of(2026, 5, 1, 10, 0);
        return viaje(inicio, inicio.plusHours(horas), intereses, List.of(selecciones));
    }
    private void escenario(
            ConfiguracionItinerarioDto config,
            List<CandidatoEvaluadoDto> candidatos) {

        when(evaluacion.evaluar(config)).thenReturn(
                new EvaluacionCandidatosDto(
                        candidatos, true, List.of(), 0, 0));

        List<DesplazamientoDto> tramos = new ArrayList<>();

        for (var candidato : candidatos) {
            tramos.add(new DesplazamientoDto(
                    "referencia:inicio",
                    candidato.lugar().id(),
                    0.0,
                    0.0,
                    EstadoDesplazamiento.DISPONIBLE,
                    "openrouteservice"));
        }

        for (var origen : candidatos) {
            for (var destino : candidatos) {
                if (origen == destino) {
                    continue;
                }

                tramos.add(new DesplazamientoDto(
                        origen.lugar().id(),
                        destino.lugar().id(),
                        0.0,
                        0.0,
                        EstadoDesplazamiento.DISPONIBLE,
                        "openrouteservice"));
            }
        }

        when(rutas.calcular(anyList())).thenReturn(tramos);
    }
    private List<BloqueItinerarioDto> visitas(ItinerarioGeneradoDto plan) {
        return plan.dias().stream().flatMap(d -> d.bloques().stream()).filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).toList();
    }

    @Test void equilibraCategoriasAunqueUnaTengaCienDeAfinidadYLasDemasDiez() {
        var config = viaje(3, List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES));
        var datos = List.of(candidato("museo1", ARTE_MUSEOS, 100), candidato("museo2", ARTE_MUSEOS, 100),
                candidato("monumento", HISTORIA_ARQUITECTURA, 10), candidato("parque", NATURALEZA_PARQUES, 10));
        escenario(config, datos);
        var elegidos = visitas(servicio.generar(config));
        assertEquals(3, elegidos.size());
        assertEquals(Set.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES),
                elegidos.stream().map(b -> b.candidato().lugar().categorias().getFirst()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void equilibraElViajeCompletoAlCambiarDeDiaEnVezDeReiniciarLasCategorias() {
        var inicio = LocalDateTime.of(2026, 5, 1, 16, 0);
        var config = viaje(inicio, inicio.plusDays(1).withHour(12), List.of(ARTE_MUSEOS, NATURALEZA_PARQUES), List.of());
        var datos = List.of(candidato("m1", ARTE_MUSEOS, 100), candidato("m2", ARTE_MUSEOS, 100),
                candidato("p1", NATURALEZA_PARQUES, 10), candidato("p2", NATURALEZA_PARQUES, 10));
        escenario(config, datos);
        var elegidos = visitas(servicio.generar(config));
        assertEquals(4, elegidos.size());
        assertEquals(2, elegidos.stream().filter(b -> b.candidato().lugar().categorias().contains(NATURALEZA_PARQUES)).count());
        assertEquals(2, elegidos.stream().filter(b -> b.candidato().lugar().categorias().contains(ARTE_MUSEOS)).count());
    }

    @Test void obligatoriosYPreferidosNoConsumenLosTurnosDeLasVisitasAutomaticas() {
        var a = candidato("obligatorio", ARTE_MUSEOS, 1);
        var b = candidato("preferido", ARTE_MUSEOS, 1);
        var inicio = LocalDateTime.of(2026, 5, 1, 10, 0);
        var config = viaje(inicio, inicio.withHour(18), List.of(ARTE_MUSEOS, NATURALEZA_PARQUES),
                List.of(new SeleccionLugarDto(a.lugar(), EstadoLugar.OBLIGATORIO), new SeleccionLugarDto(b.lugar(), EstadoLugar.PREFERIDO)));
        escenario(config, List.of(a, b, candidato("museo", ARTE_MUSEOS, 100), candidato("parque", NATURALEZA_PARQUES, 10)));
        var elegidos = visitas(servicio.generar(config));
        assertEquals(List.of("obligatorio", "preferido", "museo", "parque"), elegidos.stream().map(v -> v.candidato().lugar().id()).toList());
        assertTrue(elegidos.stream().allMatch(BloqueItinerarioDto::condicionada));
        assertTrue(elegidos.stream().allMatch(bloque -> bloque.candidato().horario() == null));
    }

    @Test void unReferenteDocumentadoSeAdelantaASuAlternativaDeLaMismaCategoria() {
        var config = viaje(1, List.of(NATURALEZA_PARQUES));
        escenario(config, List.of(candidato("a-jardin", NATURALEZA_PARQUES, 60, null, false, 0),
                candidato("z-parque-emblematico", NATURALEZA_PARQUES, 60, null, true, 7)));
        assertEquals("z-parque-emblematico", visitas(servicio.generar(config)).getFirst().candidato().lugar().id());
    }

    @Test void protegeLaAperturaLimitadaAunqueOtroObligatorioYaSePuedaVisitar() {
        var limitado = candidato("museo-limitado", ARTE_MUSEOS, 1, "Fr 11:00-12:00", false, 0);
        var flexible = candidato("plaza-flexible", HISTORIA_ARQUITECTURA, 100);
        var config = viaje(3, List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA),
                new SeleccionLugarDto(limitado.lugar(), EstadoLugar.OBLIGATORIO), new SeleccionLugarDto(flexible.lugar(), EstadoLugar.OBLIGATORIO));
        escenario(config, List.of(flexible, limitado));
        // Visitar la plaza primero impediría llegar a las 11:00; el orden inverso sí cabe.
        when(rutas.calcular(anyList())).thenReturn(List.of(
                new DesplazamientoDto(
                        "referencia:inicio", "museo-limitado",
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "referencia:inicio", "plaza-flexible",
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "plaza-flexible", "museo-limitado",
                        100.0, 600.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "museo-limitado", "plaza-flexible",
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice")));
        var plan = servicio.generar(config);
        assertEquals(List.of("museo-limitado", "plaza-flexible"), visitas(plan).stream().map(b -> b.candidato().lugar().id()).toList());
        assertEquals(LocalTime.of(11, 0), visitas(plan).getFirst().inicio().toLocalTime());
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test void unCierreConocidoImpideAceptarElPlanPeroNoSeInventaUnHorarioAlternativo() {
        var cerrado = candidato("Museo cerrado", ARTE_MUSEOS, 50, "Fr off", false, 0);
        var config = viaje(3, List.of(ARTE_MUSEOS), new SeleccionLugarDto(cerrado.lugar(), EstadoLugar.OBLIGATORIO));
        escenario(config, List.of(cerrado));
        var error = assertThrows(ObligatoriosPendientesException.class, () -> servicio.generar(config));
        assertTrue(error.getProblemas().getFirst().contains("Museo cerrado"));
        assertTrue(error.getProblemas().getFirst().contains("horario"));
        verifyNoInteractions(rutas);
    }

    @Test void sinCaminoEntreObligatoriosNoDevuelveUnItinerarioQueOmitaAlguno() {
        var a = candidato("Torre", HISTORIA_ARQUITECTURA, 80);
        var b = candidato("Museo", ARTE_MUSEOS, 80);
        var config = viaje(3, List.of(HISTORIA_ARQUITECTURA, ARTE_MUSEOS),
                new SeleccionLugarDto(a.lugar(), EstadoLugar.OBLIGATORIO), new SeleccionLugarDto(b.lugar(), EstadoLugar.OBLIGATORIO));
        escenario(config, List.of(a, b));
        when(rutas.calcular(anyList())).thenReturn(List.of(
                new DesplazamientoDto(
                        "referencia:inicio", a.lugar().id(),
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "referencia:inicio", b.lugar().id(),
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice")));
        var error = assertThrows(ObligatoriosPendientesException.class, () -> servicio.generar(config));
        assertTrue(error.getProblemas().stream().anyMatch(p -> p.contains("trayectos")));
    }

    @Test void explicaLaFaltaDeVariedadCuandoSoloUnaCategoriaTieneVisitasFactibles() {
        var config = viaje(3, List.of(ARTE_MUSEOS, NATURALEZA_PARQUES));
        escenario(config, List.of(candidato("m1", ARTE_MUSEOS, 80), candidato("m2", ARTE_MUSEOS, 80), candidato("m3", ARTE_MUSEOS, 80)));
        var plan = servicio.generar(config);
        assertEquals(1, visitas(plan).size());
        assertTrue(plan.advertencias().stream().anyMatch(a -> a.contains("tiempo libre") && a.contains("reparto equilibrado")));
    }

    @Test void reservaLaCategoriaQueAbreMasTardeSinRellenarTodosLosHuecosConMuseos() {
        var config = viaje(6, List.of(ARTE_MUSEOS, NATURALEZA_PARQUES));
        escenario(config, List.of(candidato("m1", ARTE_MUSEOS, 100), candidato("m2", ARTE_MUSEOS, 100),
                candidato("m3", ARTE_MUSEOS, 100), candidato("parque", NATURALEZA_PARQUES, 10, "Fr 15:00-16:00", false, 0)));
        var plan = servicio.generar(config);
        assertEquals(List.of("m1", "parque"), visitas(plan).stream().map(b -> b.candidato().lugar().id()).toList());
        assertEquals(LocalTime.of(15, 0), visitas(plan).getLast().inicio().toLocalTime());
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test @SuppressWarnings("unchecked") void laMuestraDeRutasConservaTodasLasCategoriasAntesDelLimiteDeCincuenta() {
        var config = viaje(3, List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, CULTURA_LOCAL, NATURALEZA_PARQUES));
        List<CandidatoEvaluadoDto> candidatos = new ArrayList<>();
        for (var categoria : config.preferencias().intereses()) for (int i = 0; i < 70; i++)
            candidatos.add(candidato(categoria.name() + i, categoria, categoria == ARTE_MUSEOS ? 100 : 10));
        escenario(config, candidatos);
        servicio.generar(config);
        org.mockito.ArgumentCaptor<List<PuntoRutaDto>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rutas).calcular(captor.capture());
        assertEquals(50, captor.getValue().size());
        for (var categoria : config.preferencias().intereses()) {
            long cantidad = captor.getValue().stream().filter(p -> p.id().startsWith(categoria.name())).count();
            assertTrue(cantidad >= 12 && cantidad <= 13, categoria + ": " + cantidad);
        }
    }
}
