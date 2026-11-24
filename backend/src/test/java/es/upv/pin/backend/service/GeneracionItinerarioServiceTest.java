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
                new DestinoDto(
                        "madrid",
                        "Madrid",
                        "Comunidad de Madrid",
                        "España",
                        40.4168,
                        -3.7038),
                new FechaHoraDto(llegada, salida, dias),
                null,
                preferencias,
                new RitmoDiarioDto(
                        java.time.LocalTime.of(13, 0),
                        java.time.LocalTime.of(15, 0),
                        120,
                        15,
                        10));
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
        return candidato(id, afinidad, coste, latitud, null);
    }

    private CandidatoEvaluadoDto candidato(
            String id, int afinidad, CostLevel coste, Double latitud, Integer relevancia) {
        var lugar = new LugarDto(
                id, id, null, latitud, 0.0,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);
        return new CandidatoEvaluadoDto(
                lugar, afinidad, 60, null, null, coste, null, List.of(),
                null, null, "GEMINI", CostConfidence.UNKNOWN,
                ScheduleStatus.UNVERIFIED, null, null, null, null, null, relevancia);
    }

    @Test
    void noProgramaDosVecesElMismoRecintoConNombresEIdsDiferentes() {
        var config = config(2);
        evaluar(config, List.of(candidato("Parque del Laberinto de Horta", 80, CostLevel.UNKNOWN, 1.0),
                candidato("Laberinto de Horta", 80, CostLevel.UNKNOWN, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(
        conRutasDesdeInicio(
                List.of(),
                "Parque del Laberinto de Horta",
                "Laberinto de Horta"));
        var plan = servicio.generar(config);
        assertEquals(1, plan.dias().stream().flatMap(d -> d.bloques().stream())
                .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).count());
    }

    @Test
    void priorizaUnReferenteAunqueSuIdSeaPosteriorYNoPermiteSolapes() {
        var llegada = LocalDateTime.of(2026, 5, 1, 11, 0);

        var config = config(
                llegada,
                llegada.plusMinutes(70),
                NivelGasto.MODERADO,
                5);

        evaluar(config, List.of(
                candidato("a-secundario", 80, CostLevel.UNKNOWN, 1.0, 20),
                candidato("z-referente", 80, CostLevel.UNKNOWN, 1.0, 95)));

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(
                        List.of(),
                        "a-secundario",
                        "z-referente"));

        var plan = servicio.generar(config);

        var visitas = plan.dias().getFirst().bloques().stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                .toList();

        assertEquals(1, visitas.size());
        assertEquals("z-referente", visitas.getFirst().candidato().lugar().id());

        assertEquals(
                llegada.plusMinutes(config.ritmo().margenMinutos()),
                visitas.getFirst().inicio());

        assertEquals(
                config.disponibilidad().salida(),
                visitas.getFirst().fin());
        }
    @Test
    @SuppressWarnings("unchecked")
    void unReferenteNoSePierdeAntesDeCalcularLaMatrizDeRutas() {
        var config = config(1);
        List<CandidatoEvaluadoDto> candidatos = new ArrayList<>();
        for (int i = 0; i < 60; i++) candidatos.add(candidato("a" + i, 80, CostLevel.UNKNOWN, 1.0, 20));
        candidatos.add(candidato("z-referente", 80, CostLevel.UNKNOWN, 1.0, 95));
        evaluar(config, candidatos);
        when(desplazamientos.calcular(anyList())).thenReturn(List.of());
        servicio.generar(config);
        ArgumentCaptor<List<PuntoRutaDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(desplazamientos).calcular(captor.capture());
        assertEquals(50, captor.getValue().size());
        assertTrue(captor.getValue().stream().anyMatch(p -> p.id().equals("z-referente")));
    }

    private void evaluar(
            ConfiguracionItinerarioDto config,
            List<CandidatoEvaluadoDto> candidatos) {
        when(evaluacion.evaluar(config)).thenReturn(
                new EvaluacionCandidatosDto(
                        candidatos, false, List.of("Aviso previo"), 0, 0));
    }

    private List<DesplazamientoDto> conRutasDesdeInicio(
            List<DesplazamientoDto> rutas,
            String... destinos) {

        var resultado = new ArrayList<>(rutas);

        for (String destino : destinos) {
            resultado.add(ruta(
                    "referencia:inicio",
                    destino,
                    0,
                    0));
        }

        return List.copyOf(resultado);
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
        var llegada = LocalDateTime.of(2026, 5, 1, 10, 0);

        var config = config(
                llegada,
                llegada.withHour(18),
                NivelGasto.ECONOMICO,
                5);

        evaluar(config, List.of(
                candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.FREE, 1.0),
                candidato("c", 88, CostLevel.FREE, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(
                        List.of(
                                ruta("a", "b", 100, 60),
                                ruta("a", "c", 100, 60),
                                ruta("b", "c", 100, 60),
                                ruta("b", "a", 100, 60),
                                ruta("c", "a", 100, 60),
                                ruta("c", "b", 100, 60)),
                        "a",
                        "b",
                        "c"));

        var bloques = servicio.generar(config).dias().getFirst().bloques();

        var visitas = bloques.stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                .toList();

        assertEquals(3, visitas.size());

        assertTrue(visitas.stream().allMatch(visita ->
                Duration.between(visita.inicio(), visita.fin()).toMinutes() == 60));

        var descansos = bloques.stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.DESCANSO)
                .toList();

        assertEquals(1, descansos.size());

        assertEquals(
                15,
                Duration.between(
                        descansos.getFirst().inicio(),
                        descansos.getFirst().fin()).toMinutes());

        assertEquals(
                1,
                bloques.stream()
                        .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.COMIDA)
                        .count());

        long numeroTraslados = bloques.stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.TRASLADO)
                .count();

        long numeroMargenes = bloques.stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.MARGEN)
                .count();

        assertEquals(numeroTraslados + 1, numeroMargenes);
}
    @Test
    void construyeVisitasYTrasladoDirigidoSinSolapes() {
        var config = config(1);

        evaluar(config, List.of(
                candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.LOW, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(
                        List.of(
                                ruta("a", "b", 100, 61),
                                ruta("b", "a", 110, 65)),
                        "a",
                        "b"));

        var plan = servicio.generar(config);
        var dia = plan.dias().getFirst();
        var bloques = dia.bloques();

        assertEquals(
                List.of(
                        TipoBloqueItinerario.MARGEN,
                        TipoBloqueItinerario.VISITA,
                        TipoBloqueItinerario.TIEMPO_LIBRE,
                        TipoBloqueItinerario.COMIDA,
                        TipoBloqueItinerario.TRASLADO,
                        TipoBloqueItinerario.MARGEN,
                        TipoBloqueItinerario.VISITA,
                        TipoBloqueItinerario.TIEMPO_LIBRE),
                bloques.stream().map(BloqueItinerarioDto::tipo).toList());

        var traslado = bloques.stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.TRASLADO)
                .findFirst()
                .orElseThrow();

        assertEquals(
                2,
                Duration.between(traslado.inicio(), traslado.fin()).toMinutes());

        assertEquals("a", traslado.origenId());
        assertEquals("b", traslado.destinoId());
        assertEquals("openrouteservice", traslado.fuente());

        var primeraVisita = bloques.stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                .findFirst()
                .orElseThrow();

        assertTrue(primeraVisita.condicionada());

        assertEquals(
                dia.inicioDisponible().plusMinutes(config.ritmo().margenMinutos()),
                primeraVisita.inicio());

        assertEquals(dia.inicioDisponible(), bloques.getFirst().inicio());
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

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(
                        List.of(
                                new DesplazamientoDto(
                                        "a", "b", null, null,
                                        EstadoDesplazamiento.RUTA_NO_DISPONIBLE,
                                        "openrouteservice"),
                                new DesplazamientoDto(
                                        "b", "a", null, null,
                                        EstadoDesplazamiento.RUTA_NO_DISPONIBLE,
                                        "openrouteservice")),
                        "a",
                        "b"));

        var plan = servicio.generar(config);

        assertEquals(10, plan.dias().size());

        assertEquals(
                2,
                plan.dias().stream()
                        .flatMap(dia -> dia.bloques().stream())
                        .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                        .count());

        for (int i = 0; i < 2; i++) {
                assertEquals(
                        1,
                        plan.dias().get(i).bloques().stream()
                                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                                .count());
        }

        assertTrue(plan.dias().stream()
                .flatMap(dia -> dia.bloques().stream())
                .noneMatch(bloque -> bloque.tipo() == TipoBloqueItinerario.TRASLADO));

        assertTrue(plan.parcial());

        assertTrue(plan.advertencias().stream()
                .anyMatch(aviso -> aviso.contains(
                        "No hemos podido calcular algunos caminos a pie")));

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
        assertEquals(49, plan.candidatosEnMatriz());
        assertTrue(captor.getValue().stream()
                .anyMatch(punto -> punto.id().equals("referencia:inicio")));
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

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(List.of(), "caro", "gratis"));

        var plan = servicio.generar(config);

        var primeraVisita = plan.dias().getFirst().bloques().stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                .findFirst()
                .orElseThrow();

        assertEquals("gratis", primeraVisita.candidato().lugar().id());
    }

    @Test
    void diferenciaDeAfinidadMayorQueCincoTienePrioridadSobreGasto() {
        var config = config(1);

        evaluar(config, List.of(
                candidato("caro", 100, CostLevel.HIGH, 1.0),
                candidato("gratis", 90, CostLevel.FREE, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(List.of(), "caro", "gratis"));

        var plan = servicio.generar(config);

        var primeraVisita = plan.dias().getFirst().bloques().stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                .findFirst()
                .orElseThrow();

        assertEquals("caro", primeraVisita.candidato().lugar().id());
    }

    @Test
    void distanciaDeseadaEsPreferenciaEntreAfinidadesParecidas() {
        var llegada = LocalDateTime.of(2026, 5, 1, 10, 0);
        var config = config(llegada, llegada.plusHours(6), NivelGasto.SIN_PREFERENCIA, 3);
        evaluar(config, List.of(
                candidato("primero", 100, CostLevel.FREE, 1.0),
                candidato("cerca", 95, CostLevel.FREE, 1.0),
                candidato("lejos", 95, CostLevel.FREE, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(
                        List.of(
                                ruta("primero", "cerca", 2000, 1200),
                                ruta("primero", "lejos", 4000, 600),
                                ruta("cerca", "primero", 2000, 1200),
                                ruta("lejos", "primero", 4000, 600)),
                        "primero",
                        "cerca",
                        "lejos"));

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
        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(
                        List.of(
                                ruta("primero", "rapido", 100, 1),
                                ruta("primero", "barato", 100, 59)),
                        "primero",
                        "rapido",
                        "barato"));
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
    void visitaYMargenPuedenOcuparExactamenteTodaLaDisponibilidad() {
        var llegada = LocalDateTime.of(2026, 5, 1, 10, 30);

        var config = config(
                llegada,
                llegada.plusMinutes(70),
                NivelGasto.MODERADO,
                3);

        evaluar(config, List.of(
                candidato("unico", 80, CostLevel.UNKNOWN, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(List.of(), "unico"));

        var plan = servicio.generar(config);
        var dia = plan.dias().getFirst();
        var bloques = dia.bloques();

        assertEquals(llegada, dia.inicioDisponible());
        assertEquals(llegada.plusMinutes(70), dia.finDisponible());

        assertEquals(
                List.of(
                        TipoBloqueItinerario.MARGEN,
                        TipoBloqueItinerario.VISITA),
                bloques.stream().map(BloqueItinerarioDto::tipo).toList());

        assertEquals(dia.inicioDisponible(), bloques.getFirst().inicio());
        assertEquals(bloques.getFirst().fin(), bloques.getLast().inicio());
        assertEquals(dia.finDisponible(), bloques.getLast().fin());

        assertFalse(plan.parcial());

        verify(desplazamientos).calcular(anyList());
        }


    @Test
    void trasladoDeCeroSegundosNoCreaBloqueVacio() {
        var config = config(1);
        evaluar(config, List.of(
                candidato("a", 90, CostLevel.FREE, 1.0),
                candidato("b", 89, CostLevel.LOW, 1.0)));
        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(
                        List.of(
                                ruta("a", "b", 0, 0),
                                ruta("b", "a", 0, 0)),
                        "a",
                        "b"));
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

        var config = config(
                llegada,
                llegada.plusHours(6),
                NivelGasto.MODERADO,
                5);

        evaluar(config, List.of(
                candidato("alto", 90, CostLevel.HIGH, 1.0),
                candidato("desconocido", 89, CostLevel.UNKNOWN, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(
                conRutasDesdeInicio(List.of(), "alto", "desconocido"));

        var plan = servicio.generar(config);

        var primeraVisita = plan.dias().getFirst().bloques().stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                .findFirst()
                .orElseThrow();

        assertEquals(
                "desconocido",
                primeraVisita.candidato().lugar().id());
    }

    private ConfiguracionItinerarioDto configConAlojamiento(
            AlojamientoDto alojamiento) {

        var base = config(1);

        var destino = new DestinoDto(
                "madrid",
                "Madrid",
                "Comunidad de Madrid",
                "España",
                40.4168,
                -3.7038);

        var entrada = new ConfiguracionItinerarioDto(
                destino,
                base.disponibilidad(),
                alojamiento == null ? "" : alojamiento.nombre(),
                base.preferencias(),
                base.ritmo(),
                alojamiento);

        when(configuraciones.validar(entrada)).thenReturn(entrada);

        return entrada;
    }

    @Test
    @SuppressWarnings("unchecked")
    void incluyeElAlojamientoEnLosPuntosDelCalculoDeRutas() {
        var hotel = new AlojamientoDto(
                "hotel-prueba",
                "Hotel Centro",
                "Calle de Prueba 10",
                40.4,
                -3.7);

        var entrada = configConAlojamiento(hotel);

        evaluar(entrada, List.of(
                candidato("museo", 90, CostLevel.FREE, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(List.of());

        servicio.generar(entrada);

        ArgumentCaptor<List<PuntoRutaDto>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(desplazamientos).calcular(captor.capture());

        var puntos = captor.getValue();

        assertEquals(2, puntos.size());
        assertTrue(puntos.contains(new PuntoRutaDto("museo", 1.0, 0.0)));

        var origen = puntos.stream()
                .filter(punto -> !punto.id().equals("museo"))
                .findFirst()
                .orElseThrow();

        assertEquals(hotel.latitud(), origen.latitud());
        assertEquals(hotel.longitud(), origen.longitud());
    }

    @Test
    @SuppressWarnings("unchecked")
    void usaLaReferenciaDelDestinoCuandoNoHayAlojamiento() {
        var entrada = configConAlojamiento(null);

        evaluar(entrada, List.of(
                candidato("museo", 90, CostLevel.FREE, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(List.of());

        servicio.generar(entrada);

        ArgumentCaptor<List<PuntoRutaDto>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(desplazamientos).calcular(captor.capture());

        var puntos = captor.getValue();

        assertEquals(2, puntos.size());
        assertTrue(puntos.contains(new PuntoRutaDto("museo", 1.0, 0.0)));

        var origen = puntos.stream()
                .filter(punto -> !punto.id().equals("museo"))
                .findFirst()
                .orElseThrow();

        assertEquals(entrada.destino().latitud(), origen.latitud());
        assertEquals(entrada.destino().longitud(), origen.longitud());
    }

    @Test
    void cuentaElPrimerTrasladoYElMargenAntesDeLaVisita() {
        var hotel = new AlojamientoDto(
                "hotel-prueba",
                "Hotel Centro",
                "Calle de Prueba 10",
                40.4,
                -3.7);

        var entrada = configConAlojamiento(hotel);

        evaluar(entrada, List.of(
                candidato("museo", 90, CostLevel.FREE, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                ruta("referencia:inicio", "museo", 1200, 900)));

        var plan = servicio.generar(entrada);
        var bloques = plan.dias().getFirst().bloques();
        var traslado = bloques.getFirst();

        assertEquals(TipoBloqueItinerario.TRASLADO, traslado.tipo());
        assertEquals("referencia:inicio", traslado.origenId());
        assertEquals("museo", traslado.destinoId());
        assertEquals(entrada.disponibilidad().llegada(), traslado.inicio());
        assertEquals(traslado.inicio().plusMinutes(15), traslado.fin());

        var visita = bloques.stream()
                .filter(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA)
                .findFirst()
                .orElseThrow();

        assertEquals(
                traslado.fin().plusMinutes(entrada.ritmo().margenMinutos()),
                visita.inicio());
    }

    @Test
    void noIncluyeUnaVisitaSiElPrimerTrasladoHaceQueNoQuepa() {
        var llegada = LocalDateTime.of(2026, 5, 1, 11, 0);

        var entrada = config(
                llegada,
                llegada.plusHours(1),
                NivelGasto.MODERADO,
                5);

        evaluar(entrada, List.of(
                candidato("museo", 90, CostLevel.FREE, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                ruta("referencia:inicio", "museo", 1200, 900)));

        var plan = servicio.generar(entrada);

        assertTrue(plan.dias().stream()
                .flatMap(dia -> dia.bloques().stream())
                .noneMatch(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA));

        assertTrue(plan.parcial());
    }

    @Test
    void noInventaUnDesplazamientoCuandoNoHayRutaDesdeElInicio() {
        var entrada = configConAlojamiento(null);

        evaluar(entrada, List.of(
                candidato("museo", 90, CostLevel.FREE, 1.0)));

        when(desplazamientos.calcular(anyList())).thenReturn(List.of());

        var plan = servicio.generar(entrada);

        assertTrue(plan.dias().stream()
                .flatMap(dia -> dia.bloques().stream())
                .noneMatch(bloque -> bloque.tipo() == TipoBloqueItinerario.VISITA));

        assertTrue(plan.advertencias().stream()
                .anyMatch(aviso -> aviso.contains("caminos a pie")));
    }
}
