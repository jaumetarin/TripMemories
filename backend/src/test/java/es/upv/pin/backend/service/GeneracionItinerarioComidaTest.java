package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneracionItinerarioComidaTest {
    private final ConfiguracionItinerarioService configuraciones = mock(ConfiguracionItinerarioService.class);
    private final EvaluacionCandidatosService evaluacion = mock(EvaluacionCandidatosService.class);
    private final DesplazamientoService desplazamientos = mock(DesplazamientoService.class);
    private final RecomendacionComidaService recomendaciones = mock(RecomendacionComidaService.class);
    private final GeneracionItinerarioService servicio = new GeneracionItinerarioService(
            configuraciones, evaluacion, desplazamientos,
            new EvaluadorHorarioService(), new ValidadorPlanService(), recomendaciones);

    private ConfiguracionItinerarioDto config(LocalDateTime llegada, LocalDateTime salida, boolean gastronomia) {
        var preferencias = new PreferenciasDto(
                gastronomia ? List.of(CategoriaTuristica.GASTRONOMIA) : List.of(),
                List.of(), List.of(), NivelGasto.SIN_PREFERENCIA, 3.0);
        var config = new ConfiguracionItinerarioDto(
                new DestinoDto("destino", "Teruel", "Aragón", "España", 40.34, -1.10),
                new FechaHoraDto(llegada, salida, 1), "", preferencias,
                new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10));
        when(configuraciones.validar(config)).thenReturn(config);
        when(recomendaciones.buscar(anyString(), any(), anySet(), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(), List.of()));
        return config;
    }

    private CandidatoEvaluadoDto lugar(String id, int afinidad, int duracion) {
        var lugar = new LugarDto(id, id, null, 40.34, -1.10,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);
        return new CandidatoEvaluadoDto(lugar, afinidad, duracion, null, null,
                CostLevel.UNKNOWN, null, List.of(), null, null, "LOCAL",
                CostConfidence.UNKNOWN, ScheduleStatus.UNVERIFIED, null, null, null, null, null);
    }

    private void evaluar(ConfiguracionItinerarioDto config, CandidatoEvaluadoDto... candidatos) {
        when(evaluacion.evaluar(config)).thenReturn(new EvaluacionCandidatosDto(
                List.of(candidatos), false, List.of(), 0, 0));
    }

    private void rutas(String... ids) {
        var resultado = new ArrayList<DesplazamientoDto>();
        for (String origen : ids) for (String destino : ids) if (!origen.equals(destino)) {
            resultado.add(new DesplazamientoDto(origen, destino, 500.0, 600.0,
                    EstadoDesplazamiento.DISPONIBLE, "openrouteservice"));
        }
        when(desplazamientos.calcular(anyList())).thenReturn(List.copyOf(resultado));
    }

    @Test
    void restaurantePrincipalTieneTrayectosYAlternativaSinReserva() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0),
                LocalDateTime.of(2026, 11, 7, 18, 0), true);
        evaluar(config, lugar("a", 90, 120), lugar("b", 80, 60));
        var principal = new OpcionComidaDto("r1", "Restaurante 1", 40.34, -1.10, "Centro");
        var alternativa = new OpcionComidaDto("r2", "Restaurante 2", 40.34, -1.10, "Centro");
        when(recomendaciones.buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(principal, alternativa), List.of()));
        rutas("a", "b", "r1", "r2");

        var plan = servicio.generar(config);
        var bloques = plan.dias().getFirst().bloques();
        var comida = bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertEquals("r1", comida.restaurante().id());
        assertEquals(List.of("r2"), comida.alternativasComida().stream().map(OpcionComidaDto::id).toList());
        assertTrue(comida.condicionada());
        assertEquals(500.0, comida.restaurante().distanciaMetros());
        assertEquals(600.0, comida.alternativasComida().getFirst().duracionSegundos());
        assertTrue(bloques.stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.TRASLADO
                && b.origenId().equals("a") && b.destinoId().equals("r1")));
        assertTrue(bloques.stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.TRASLADO
                && b.origenId().equals("r1") && b.destinoId().equals("b")));
        assertEquals(2, bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.MARGEN).count());
        var descanso = bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.DESCANSO)
                .findFirst().orElseThrow();
        assertEquals(15, java.time.Duration.between(descanso.inicio(), descanso.fin()).toMinutes());
        assertTrue(bloques.stream().noneMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA
                && (b.candidato().lugar().id().equals("r1") || b.candidato().lugar().id().equals("r2"))));
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test
    void sinInteresGastronomicoTambienProponeRestaurante() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0),
                LocalDateTime.of(2026, 11, 7, 18, 0), false);
        evaluar(config, lugar("a", 90, 120), lugar("b", 80, 60));
        var restaurante = new OpcionComidaDto("r1", "Restaurante", 40.34, -1.10, "Centro");
        when(recomendaciones.buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(restaurante), List.of()));
        rutas("a", "b", "r1");

        var plan = servicio.generar(config);
        var comida = plan.dias().getFirst().bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertEquals("r1", comida.restaurante().id());
        verify(recomendaciones).buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList());
    }

    @Test
    void reservaSoloElSolapeDeLaFranjaEnDiaParcial() {
        var config = config(LocalDateTime.of(2026, 11, 7, 14, 30),
                LocalDateTime.of(2026, 11, 7, 16, 0), true);
        evaluar(config, lugar("a", 90, 60));

        var plan = servicio.generar(config);
        var comida = plan.dias().getFirst().bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertEquals(LocalDateTime.of(2026, 11, 7, 14, 30), comida.inicio());
        assertEquals(LocalDateTime.of(2026, 11, 7, 15, 0), comida.fin());
        assertNull(comida.restaurante());
        verifyNoInteractions(recomendaciones);
    }

    @Test
    void restauranteCerradoSegunFuenteNoSeProponeComoPrincipalNiAlternativa() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0),
                LocalDateTime.of(2026, 11, 7, 18, 0), true);
        evaluar(config, lugar("a", 90, 120), lugar("b", 80, 60));
        var cerrado = new OpcionComidaDto("r1", "Cerrado", 40.34, -1.10,
                "Centro", "Sa off", "Geoapify");
        var abiertoDesconocido = new OpcionComidaDto("r2", "Restaurante 2", 40.34, -1.10, "Centro");
        when(recomendaciones.buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(cerrado, abiertoDesconocido), List.of()));
        rutas("a", "b", "r1", "r2");

        var comida = servicio.generar(config).dias().getFirst().bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertEquals("r2", comida.restaurante().id());
        assertTrue(comida.alternativasComida().isEmpty());
    }

    @Test
    void sinRutaAlRestauranteMantieneComidaGenericaYVisitaPosterior() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0),
                LocalDateTime.of(2026, 11, 7, 18, 0), true);
        evaluar(config, lugar("a", 90, 120), lugar("b", 80, 60));
        var restaurante = new OpcionComidaDto("r1", "Restaurante 1", 40.34, -1.10, "Centro");
        when(recomendaciones.buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(restaurante), List.of()));
        var rutas = new ArrayList<DesplazamientoDto>();
        for (String origen : List.of("a", "b", "r1"))
            for (String destino : List.of("a", "b", "r1")) if (!origen.equals(destino)) {
                rutas.add((origen.equals("a") || origen.equals("b")) && destino.equals("r1")
                        ? new DesplazamientoDto(origen, destino, null, null,
                                EstadoDesplazamiento.RUTA_NO_DISPONIBLE, "openrouteservice")
                        : new DesplazamientoDto(origen, destino, 500.0, 600.0,
                                EstadoDesplazamiento.DISPONIBLE, "openrouteservice"));
            }
        when(desplazamientos.calcular(anyList())).thenReturn(rutas);

        var plan = servicio.generar(config);
        var comida = plan.dias().getFirst().bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertNull(comida.restaurante());
        assertTrue(plan.dias().getFirst().bloques().stream().anyMatch(b ->
                b.tipo() == TipoBloqueItinerario.VISITA && b.candidato().lugar().id().equals("b")));
        assertTrue(plan.advertencias().stream().anyMatch(a -> a.contains("restaurante al que puedas llegar a tiempo")));
    }

    @Test
    void cierreInequivocoDescartaVisitaYExplicaMotivo() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0),
                LocalDateTime.of(2026, 11, 7, 18, 0), false);
        var cerradoBase = lugar("cerrado", 90, 60);
        var cerradoLugar = new LugarDto("cerrado", "Museo cerrado", null, 40.34, -1.10,
                cerradoBase.lugar().categorias(), List.of(), "Sa off");
        var cerrado = new CandidatoEvaluadoDto(cerradoLugar, 90, 60, null, null,
                CostLevel.UNKNOWN, null, List.of(), "Sa off", "Geoapify (sin verificar)",
                "LOCAL", CostConfidence.UNKNOWN, ScheduleStatus.UNVERIFIED, null, null, null, null, null);
        evaluar(config, cerrado, lugar("abierto", 80, 60));
        rutas("cerrado", "abierto");

        var plan = servicio.generar(config);
        assertTrue(plan.dias().getFirst().bloques().stream().noneMatch(b ->
                b.tipo() == TipoBloqueItinerario.VISITA && b.candidato().lugar().id().equals("cerrado")));
        assertTrue(plan.advertencias().stream().anyMatch(a ->
                a.contains("Museo cerrado") && a.contains("sus horarios no encajan")));
        assertTrue(plan.advertencias().stream().noneMatch(a -> a.contains("(cerrado)")));
        assertEquals(plan.advertencias().size(), plan.advertencias().stream().distinct().count());
        assertTrue(plan.parcial());
    }

    @Test
    void esperaLaAperturaYRecolocaLaVisitaEnElMismoDia() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0),
                LocalDateTime.of(2026, 11, 7, 18, 0), false);
        var lugar = new LugarDto("museo", "Museo", null, 40.34, -1.10,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), "Sa 11:00-12:00");
        var candidato = new CandidatoEvaluadoDto(lugar, 90, 60, null, null,
                CostLevel.UNKNOWN, null, List.of(), "Sa 11:00-12:00",
                "Geoapify (sin verificar)", "LOCAL", CostConfidence.UNKNOWN,
                ScheduleStatus.UNVERIFIED, null, null, null, null, null);
        evaluar(config, candidato);

        var plan = servicio.generar(config);
        var visita = plan.dias().getFirst().bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).findFirst().orElseThrow();
        assertEquals(LocalDateTime.of(2026, 11, 7, 11, 0), visita.inicio());
        assertEquals(LocalDateTime.of(2026, 11, 7, 12, 0), visita.fin());
        assertTrue(visita.condicionada());
        assertTrue(plan.dias().getFirst().bloques().stream().anyMatch(b ->
                b.tipo() == TipoBloqueItinerario.TIEMPO_LIBRE
                        && b.inicio().equals(LocalDateTime.of(2026, 11, 7, 10, 0))
                        && b.fin().equals(LocalDateTime.of(2026, 11, 7, 11, 0))));
        assertTrue(plan.advertencias().stream().noneMatch(a -> a.contains("sus horarios no encajan")));
    }

    @Test
    void reservaElTrasladoEnVezDeLlenarLasVisitasHastaLaHoraDeComer() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0), LocalDateTime.of(2026, 11, 7, 18, 0), false);
        evaluar(config, lugar("a", 90, 60), lugar("b", 80, 90));
        var restaurante = new OpcionComidaDto("r", "Restaurante cercano", 40.34, -1.10, "Centro");
        when(recomendaciones.buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(restaurante), List.of()));
        rutas("a", "b", "r");

        var plan = servicio.generar(config);
        var bloques = plan.dias().getFirst().bloques();
        var comida = bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertNotNull(comida.restaurante());
        assertTrue(bloques.stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA
                && b.candidato().lugar().id().equals("b") && !b.inicio().isBefore(comida.fin())));
        var traslado = bloques.stream().filter(b -> b.tipo() == TipoBloqueItinerario.TRASLADO
                && b.destinoId().equals("r")).findFirst().orElseThrow();
        assertFalse(traslado.fin().plusMinutes(10).isAfter(comida.inicio()));
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test
    void priorizaUnRestauranteCercanoAlRecorridoDeAntesYDespues() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0), LocalDateTime.of(2026, 11, 7, 18, 0), false);
        evaluar(config, lugar("a", 90, 120), lugar("b", 80, 60));
        var lejos = new OpcionComidaDto("lejos", "Lejano", 40.5, -1.2, "Lejos");
        var cerca = new OpcionComidaDto("cerca", "Cercano", 40.34, -1.1, "Centro");
        when(recomendaciones.buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(lejos, cerca), List.of()));
        var tramos = new ArrayList<DesplazamientoDto>();
        for (String desde : List.of("a", "b", "lejos", "cerca"))
            for (String hasta : List.of("a", "b", "lejos", "cerca")) if (!desde.equals(hasta)) {
                double metros = (desde.equals("lejos") || hasta.equals("lejos")) ? 3000 : 300;
                tramos.add(new DesplazamientoDto(desde, hasta, metros, 600.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice"));
            }
        when(desplazamientos.calcular(anyList())).thenReturn(tramos);

        var comida = servicio.generar(config).dias().getFirst().bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertEquals("cerca", comida.restaurante().id());
        assertEquals(300.0, comida.restaurante().distanciaMetros());
    }

    @Test
    void sinRestaurantesDejaPausaLibreSinAdvertenciaDeDisponibilidad() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0), LocalDateTime.of(2026, 11, 7, 18, 0), false);
        evaluar(config, lugar("a", 90, 60));
        var comida = servicio.generar(config).dias().getFirst().bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA).findFirst().orElseThrow();
        assertNull(comida.restaurante());
        assertFalse(comida.condicionada());
        assertTrue(comida.alternativasComida().isEmpty());
        assertEquals("Pausa para comer. Puedes elegir un restaurante por la zona.", comida.descripcion());
    }

    @Test
    void restauranteEnElMismoPuntoNoNecesitaUnBloqueDeTrasladoDeCeroMinutos() {
        var config = config(LocalDateTime.of(2026, 11, 7, 10, 0), LocalDateTime.of(2026, 11, 7, 18, 0), false);
        evaluar(config, lugar("a", 90, 120));
        var restaurante = new OpcionComidaDto("r", "Cafetería del museo", 40.34, -1.10, "Centro");
        when(recomendaciones.buscar(eq("destino"), eq(config.preferencias()), eq(Set.of()), anyList()))
                .thenReturn(new RecomendacionComidaDto(List.of(restaurante), List.of()));
        when(desplazamientos.calcular(anyList())).thenReturn(List.of(
                new DesplazamientoDto("a", "r", 0.0, 0.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("r", "a", 0.0, 0.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice")));
        var plan = servicio.generar(config);
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
        assertTrue(plan.dias().getFirst().bloques().stream().noneMatch(b -> b.tipo() == TipoBloqueItinerario.TRASLADO));
    }
}
