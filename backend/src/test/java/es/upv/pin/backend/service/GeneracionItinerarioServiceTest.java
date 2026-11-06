package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeneracionItinerarioServiceTest {
    private final ConfiguracionItinerarioService configuraciones =
            mock(ConfiguracionItinerarioService.class);
    private final EvaluacionCandidatosService evaluacion =
            mock(EvaluacionCandidatosService.class);
    private final DesplazamientoService desplazamientos =
            mock(DesplazamientoService.class);
    private final GeneracionItinerarioService servicio =
            new GeneracionItinerarioService(configuraciones, evaluacion, desplazamientos);

    private ConfiguracionItinerarioDto config(
            LocalDateTime llegada, LocalDateTime salida,
            NivelGasto gasto, double distanciaKm) {
        int dias = (int) ChronoUnit.DAYS.between(
                llegada.toLocalDate(), salida.toLocalDate()) + 1;
        var preferencias = new PreferenciasDto(
                List.of(), List.of(), List.of(), gasto, distanciaKm);
        var config = new ConfiguracionItinerarioDto(
                null, new FechaHoraDto(llegada, salida, dias), null, preferencias,
                new RitmoDiarioDto(java.time.LocalTime.of(13, 0), java.time.LocalTime.of(15, 0), 120, 15, 10));
        when(configuraciones.validar(config)).thenReturn(config);
        return config;
    }

    private ConfiguracionItinerarioDto config(int dias) {
        LocalDateTime llegada = LocalDateTime.of(2026, 5, 1, 11, 0);
        return config(llegada, llegada.plusDays(dias - 1).withHour(17),
                NivelGasto.ECONOMICO, 5);
    }

    private CandidatoEvaluadoDto candidato(
            String id, int afinidad, CostLevel coste, Double latitud) {
        var lugar = new LugarDto(
                id, id, null, latitud, 0.0,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);
        return new CandidatoEvaluadoDto(
                lugar, afinidad, 60, null, null, coste, null, List.of(),
                null, null, "GEMINI", CostConfidence.UNKNOWN,
                ScheduleStatus.UNVERIFIED, null, null, null, null, null);
    }

    private void evaluar(
            ConfiguracionItinerarioDto config,
            List<CandidatoEvaluadoDto> candidatos) {
        when(evaluacion.evaluar(config)).thenReturn(
                new EvaluacionCandidatosDto(
                        candidatos, false, List.of("Aviso previo"), 0, 0));
    }

    private DesplazamientoDto ruta(
            String origen, String destino, double metros, double segundos) {
        return new DesplazamientoDto(
                origen, destino, metros, segundos,
                EstadoDesplazamiento.DISPONIBLE, "openrouteservice");
    }

    @Test
    void conservaTodosLosCandidatosYElFallbackDeUnaUnicaEvaluacion() {
        var config = config(1);
        var candidatos = List.of(candidato("programable", 90, CostLevel.FREE, 1.0),
                candidato("sin-coordenadas", 50, CostLevel.UNKNOWN, null));
        when(evaluacion.evaluar(config)).thenReturn(
                new EvaluacionCandidatosDto(candidatos, true, List.of("Valoracion local"), 0, 0));

        var plan = servicio.generar(config);

        assertEquals(candidatos, plan.candidatos());
        assertTrue(plan.evaluacionFallback());
        assertEquals(2, plan.candidatosEvaluados());
        assertEquals(1, plan.candidatosEnMatriz());
        verify(evaluacion, times(1)).evaluar(config);
    }

    @Test
    void reservaComidaCompletaCadaDiaYNoSolapa() {
        var config = config(10);
        evaluar(config, List.of());
        var plan = servicio.generar(config);
        assertEquals(10, plan.dias().size());
        for (var dia : plan.dias()) {
            var comidas = dia.bloques().stream().filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).toList();
            assertEquals(1, comidas.size());
            assertEquals(120, Duration.between(comidas.getFirst().inicio(), comidas.getFirst().fin()).toMinutes());
            for (int i = 1; i < dia.bloques().size(); i++)
                assertFalse(dia.bloques().get(i).inicio().isBefore(dia.bloques().get(i - 1).fin()));
        }
    }

    @Test
    void descansoSeInsertaTrasActividadSinCortarVisitas() {
        var config = config(1);
        evaluar(config, List.of(candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.FREE, 1.0), candidato("c", 88, CostLevel.FREE, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                ruta("a", "b", 100, 60), ruta("a", "c", 100, 60),
                ruta("b", "c", 100, 60), ruta("b", "a", 100, 60),
                ruta("c", "a", 100, 60), ruta("c", "b", 100, 60)));
        var bloques = servicio.generar(config).dias().getFirst().bloques();
        assertTrue(bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.VISITA)
                .allMatch(b -> Duration.between(b.inicio(), b.fin()).toMinutes() == 60));
        assertEquals(1, bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).count());
        assertEquals(bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.TRASLADO).count(),
                bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.MARGEN).count());
    }

    @Test
    void construyeVisitasYTrasladoDirigidoSinSolapes() {
        var config = config(1);
        evaluar(config, List.of(
                candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.LOW, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                ruta("a", "b", 100, 61), ruta("b", "a", 110, 65)));

        var plan = servicio.generar(config);
        var dia = plan.dias().get(0);
        var bloques = dia.bloques();

        assertEquals(List.of(
                TipoBloqueItinerario.VISITA,
                TipoBloqueItinerario.TIEMPO_LIBRE,
                TipoBloqueItinerario.COMIDA,
                TipoBloqueItinerario.TRASLADO,
                TipoBloqueItinerario.MARGEN,
                TipoBloqueItinerario.VISITA,
                TipoBloqueItinerario.TIEMPO_LIBRE),
                bloques.stream().map(BloqueItinerarioDto::tipo).toList());
        assertEquals(2, Duration.between(
                bloques.get(3).inicio(), bloques.get(3).fin()).toMinutes());
        assertEquals("a", bloques.get(3).origenId());
        assertEquals("b", bloques.get(3).destinoId());
        assertEquals("openrouteservice", bloques.get(3).fuente());
        assertTrue(bloques.get(0).condicionada());
        assertEquals(dia.inicioDisponible(), bloques.get(0).inicio());
        assertEquals(dia.finDisponible(), bloques.getLast().fin());
        for (int i = 1; i < bloques.size(); i++) {
            assertEquals(bloques.get(i - 1).fin(), bloques.get(i).inicio());
        }
        assertTrue(plan.advertencias().contains("Aviso previo"));
        verify(evaluacion).evaluar(config);
        verify(desplazamientos).calcular(anyList());
    }

    @Test
    void rutasAusentesProducenPlanParcialDuranteDiezDias() {
        var config = config(10);
        evaluar(config, List.of(
                candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.LOW, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                new DesplazamientoDto(
                        "a", "b", null, null,
                        EstadoDesplazamiento.RUTA_NO_DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "b", "a", null, null,
                        EstadoDesplazamiento.RUTA_NO_DISPONIBLE, "openrouteservice")));

        var plan = servicio.generar(config);
        assertEquals(10, plan.dias().size());
        assertEquals(2, plan.dias().stream()
                .flatMap(d -> d.bloques().stream())
                .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA)
                .count());
        assertTrue(plan.dias().stream()
                .flatMap(d -> d.bloques().stream())
                .noneMatch(b -> b.tipo() == TipoBloqueItinerario.TRASLADO));
        assertTrue(plan.parcial());
        assertTrue(plan.advertencias().stream()
                .anyMatch(a -> a.contains("No hemos podido calcular algunos caminos a pie")));
        assertEquals(plan, servicio.generar(config));
    }

    @Test
    void limitaLaMatrizACincuentaYOmiteLugaresSinCoordenadas() {
        var config = config(1);
        var candidatos = new ArrayList<CandidatoEvaluadoDto>();
        for (int i = 0; i < 55; i++) {
            candidatos.add(candidato("id" + i, 80, CostLevel.UNKNOWN, 1.0));
        }
        candidatos.add(candidato("sin", 100, CostLevel.FREE, null));
        evaluar(config, candidatos);
        when(desplazamientos.calcular(anyList())).thenReturn(List.of());

        var plan = servicio.generar(config);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PuntoRutaDto>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(desplazamientos).calcular(captor.capture());
        assertEquals(50, captor.getValue().size());
        assertTrue(captor.getValue().stream().noneMatch(p -> p.id().equals("sin")));
        assertEquals(56, plan.candidatosEvaluados());
        assertEquals(50, plan.candidatosEnMatriz());
        assertTrue(plan.advertencias().stream()
                .anyMatch(a -> a.contains("no conocemos su ubicación exacta")));
        assertTrue(plan.advertencias().stream()
                .anyMatch(a -> a.contains("Hemos limitado los lugares")));
        assertTrue(plan.advertencias().stream().noneMatch(a -> a.contains("matriz")));
    }

    @Test
    void sinCandidatosDevuelveTiempoLibreSinConsultarRutas() {
        var config = config(1);
        evaluar(config, List.of());

        var plan = servicio.generar(config);
        assertEquals(1, plan.dias().size());
        assertEquals(TipoBloqueItinerario.TIEMPO_LIBRE,
                plan.dias().get(0).bloques().get(0).tipo());
        assertTrue(plan.parcial());
        verifyNoInteractions(desplazamientos);
    }

    @Test
    void falloGlobalDeOrsNoDevuelveUnPlanTemporizado() {
        var config = config(1);
        evaluar(config, List.of(
                candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.LOW, 1.0)));
        when(desplazamientos.calcular(anyList()))
                .thenThrow(new IllegalStateException("ORS no disponible"));

        var error = assertThrows(
                IllegalStateException.class, () -> servicio.generar(config));
        assertEquals("ORS no disponible", error.getMessage());
    }

    @Test
    void gastoEconomicoAjustaUnaAfinidadParecida() {
        var config = config(1);
        evaluar(config, List.of(
                candidato("caro", 90, CostLevel.HIGH, 1.0),
                candidato("gratis", 89, CostLevel.FREE, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of());

        var plan = servicio.generar(config);
        assertEquals("gratis", plan.dias().get(0).bloques().get(0)
                .candidato().lugar().id());
    }

    @Test
    void diferenciaDeAfinidadMayorQueCincoTienePrioridadSobreGasto() {
        var config = config(1);
        evaluar(config, List.of(
                candidato("caro", 100, CostLevel.HIGH, 1.0),
                candidato("gratis", 90, CostLevel.FREE, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of());

        var plan = servicio.generar(config);
        assertEquals("caro", plan.dias().get(0).bloques().get(0)
                .candidato().lugar().id());
    }

    @Test
    void distanciaDeseadaEsPreferenciaEntreAfinidadesParecidas() {
        var llegada = LocalDateTime.of(2026, 5, 1, 10, 0);
        var config = config(llegada, llegada.plusHours(6), NivelGasto.SIN_PREFERENCIA, 3);
        evaluar(config, List.of(
                candidato("primero", 100, CostLevel.FREE, 1.0),
                candidato("cerca", 95, CostLevel.FREE, 1.0),
                candidato("lejos", 95, CostLevel.FREE, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                ruta("primero", "cerca", 2000, 1200),
                ruta("primero", "lejos", 4000, 600),
                ruta("cerca", "primero", 2000, 1200),
                ruta("lejos", "primero", 4000, 600)));

        var plan = servicio.generar(config);
        var visitas = plan.dias().get(0).bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA)
                .map(b -> b.candidato().lugar().id()).toList();
        assertEquals(List.of("primero", "cerca"), visitas);
    }

    @Test
    void comparaSegundosRealesAunqueAmbosTrasladosRedondeenAUnMinuto() {
        var llegada = LocalDateTime.of(2026, 5, 1, 10, 0);
        var config = config(llegada, llegada.plusHours(6), NivelGasto.ECONOMICO, 3);
        evaluar(config, List.of(
                candidato("primero", 100, CostLevel.FREE, 1.0),
                candidato("rapido", 95, CostLevel.HIGH, 1.0),
                candidato("barato", 95, CostLevel.FREE, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                ruta("primero", "rapido", 100, 1),
                ruta("primero", "barato", 100, 59)));

        var plan = servicio.generar(config);
        var visitas = plan.dias().get(0).bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA)
                .map(b -> b.candidato().lugar().id()).toList();
        assertEquals(List.of("primero", "rapido"), visitas);
    }

    @Test
    void sinVentanaDiariaConservaTodosLosDiasSinConsumirProveedores() {
        var llegada = LocalDateTime.of(2026, 5, 1, 20, 0);
        var config = config(llegada, llegada.plusDays(1).withHour(9),
                NivelGasto.MODERADO, 5);

        var plan = servicio.generar(config);
        assertEquals(2, plan.dias().size());
        assertTrue(plan.dias().stream().allMatch(d ->
                d.bloques().isEmpty()
                        && d.inicioDisponible() == null
                        && d.finDisponible() == null));
        assertTrue(plan.parcial());
        assertEquals(0, plan.candidatosEvaluados());
        assertTrue(plan.candidatos().isEmpty());
        assertFalse(plan.evaluacionFallback());
        verifyNoInteractions(evaluacion, desplazamientos);
    }

    @Test
    void visitaPuedeOcuparExactamenteTodaLaDisponibilidad() {
        var llegada = LocalDateTime.of(2026, 5, 1, 10, 30);
        var config = config(llegada, llegada.plusHours(1), NivelGasto.MODERADO, 3);
        evaluar(config, List.of(candidato("unico", 80, CostLevel.UNKNOWN, 1.0)));

        var plan = servicio.generar(config);
        var dia = plan.dias().get(0);
        assertEquals(llegada, dia.inicioDisponible());
        assertEquals(llegada.plusHours(1), dia.finDisponible());
        assertEquals(1, dia.bloques().size());
        assertEquals(TipoBloqueItinerario.VISITA, dia.bloques().get(0).tipo());
        assertEquals(dia.finDisponible(), dia.bloques().get(0).fin());
        assertFalse(plan.parcial());
        verifyNoInteractions(desplazamientos);
    }

    @Test
    void trasladoDeCeroSegundosNoCreaBloqueVacio() {
        var config = config(1);
        evaluar(config, List.of(
                candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.LOW, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                ruta("a", "b", 0, 0), ruta("b", "a", 0, 0)));

        var plan = servicio.generar(config);
        assertEquals(2, plan.dias().get(0).bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA)
                .count());
        assertTrue(plan.dias().get(0).bloques().stream()
                .noneMatch(b -> b.tipo() == TipoBloqueItinerario.TRASLADO));
    }

    @Test
    void gastoModeradoNoTrataDesconocidoComoCosteAltoConfirmado() {
        var llegada = LocalDateTime.of(2026, 5, 1, 11, 0);
        var config = config(llegada, llegada.plusHours(6), NivelGasto.MODERADO, 5);
        evaluar(config, List.of(
                candidato("alto", 90, CostLevel.HIGH, 1.0),
                candidato("desconocido", 89, CostLevel.UNKNOWN, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of());

        var plan = servicio.generar(config);
        assertEquals("desconocido", plan.dias().get(0).bloques().get(0)
                .candidato().lugar().id());
    }
}
