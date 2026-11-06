package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.client.AiProvider;
import es.upv.pin.backend.client.ResearchProvider;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

class EvaluacionCandidatosServiceTests {
    private final ConfiguracionItinerarioService config =
            mock(ConfiguracionItinerarioService.class);
    private final PreparacionCandidatosService prep = mock(PreparacionCandidatosService.class);
    private final AiProvider ai = mock(AiProvider.class);
    private final ResearchProvider research = mock(ResearchProvider.class);
    private final EvaluacionCandidatosService service =
            new EvaluacionCandidatosService(config, prep, ai, research);

    @org.junit.jupiter.api.BeforeEach
    void habilitarInvestigacion() {
        when(research.disponible()).thenReturn(true);
    }

    private final ConfiguracionItinerarioDto viaje =
            new ConfiguracionItinerarioDto(
                    new DestinoDto("d", "Destino", "", "", 1.0, 1.0),
                    new FechaHoraDto(
                            LocalDateTime.parse("2026-01-01T10:00"),
                            LocalDateTime.parse("2026-01-01T18:00"),
                            1),
                    "",
                    new PreferenciasDto(
                            List.of(CategoriaTuristica.ARTE_MUSEOS),
                            List.of(),
                            List.of(),
                            NivelGasto.FLEXIBLE,
                            5.0));

    private LugarDto lugar(int i) {
        return new LugarDto(
                "id" + i,
                "Lugar" + i,
                null,
                1.0,
                1.0,
                List.of(CategoriaTuristica.ARTE_MUSEOS),
                List.of(),
                null);
    }

    private void preparar(List<LugarDto> lugares) {
        when(config.validar(viaje)).thenReturn(viaje);
        when(prep.preparar(viaje)).thenReturn(lugares);
    }

    @Test
    void evaluaTodosPorLotesYDescartaLugaresAjenos() {
        var lugares = IntStream.range(0, 45).mapToObj(this::lugar).toList();
        preparar(lugares);
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenAnswer(
                        inv -> {
                            List<LugarDto> ls = inv.getArgument(0);
                            return ls.stream()
                                    .collect(
                                            Collectors.toMap(
                                                    LugarDto::id,
                                                    l ->
                                                            new ValoracionGemini(
                                                                    80, 60, "bueno", "motivo")));
                        });
        when(research.investigar(anyList(), anyString()))
                .thenReturn(
                        Map.of(
                                "ajeno",
                                new HallazgoGemini(
                                        "extra",
                                        List.of("https://example.com"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null, null, null)));
        var result = service.evaluar(viaje);
        assertEquals(45, result.candidatos().size());
        assertFalse(result.fallback());
        verify(ai, times(2)).evaluar(anyList(), anyList(), anyString());
        assertTrue(result.candidatos().stream().allMatch(c -> c.informacion() == null));
    }

    @Test
    void conservaDescripcionYFuenteSinConvertirElCosteDesconocidoEnGratis() {
        preparar(List.of(lugar(1)));
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of("id1", new ValoracionGemini(80, 60, "Museo", "Arte")));
        when(research.investigar(anyList(), anyString())).thenReturn(Map.of("id1",
                new HallazgoGemini(null, List.of("https://museo.example/acerca"),
                        null, null, null, null, null, null, null, null,
                        "Una colección de arte local.", "https://museo.example/acerca")));

        var candidato = service.evaluar(viaje).candidatos().getFirst();

        assertEquals("Una colección de arte local.", candidato.descripcion());
        assertEquals("https://museo.example/acerca", candidato.fuenteDescripcion());
        assertEquals(CostLevel.UNKNOWN, candidato.coste());
        assertNull(candidato.horario());
    }

    @Test
    void descartaDescripcionSinUnaFuenteValida() {
        preparar(List.of(lugar(1)));
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of("id1", new ValoracionGemini(80, 60, "Museo", "Arte")));
        when(research.investigar(anyList(), anyString())).thenReturn(Map.of("id1",
                new HallazgoGemini(null, List.of("https://museo.example"),
                        null, null, null, null, null, null, null, null,
                        "Texto sin respaldo.", null)));

        var candidato = service.evaluar(viaje).candidatos().getFirst();

        assertNull(candidato.descripcion());
        assertNull(candidato.fuenteDescripcion());
    }

    @Test
    void evaluaLosSeiscientosCandidatosQuePuedeEntregarUt8() {
        var lugares = IntStream.range(0, 600).mapToObj(this::lugar).toList();
        preparar(lugares);
        when(research.disponible()).thenReturn(false);
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenAnswer(
                        inv -> {
                            List<LugarDto> lote = inv.getArgument(0);
                            return lote.stream()
                                    .collect(
                                            Collectors.toMap(
                                                    LugarDto::id,
                                                    lugar -> new ValoracionGemini(70, 60, "Museo", "Coincide con arte y museos.")));
                        });

        var resultado = service.evaluar(viaje);

        assertEquals(600, resultado.candidatos().size());
        assertFalse(resultado.fallback());
        verify(ai, times(15)).evaluar(anyList(), anyList(), anyString());
        verify(research, never()).investigar(anyList(), anyString());
    }

    @Test
    void noExponeAfirmacionesDeGeminiSinRespaldoEnLosDatos() {
        preparar(List.of(lugar(1)));
        when(research.disponible()).thenReturn(false);
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of("id1", new ValoracionGemini(
                        85, 90, "Entrada gratuita", "Abierto 24 horas y entrada gratuita.")));

        var candidato = service.evaluar(viaje).candidatos().get(0);

        assertEquals(85, candidato.afinidad());
        assertEquals(90, candidato.duracionMinutos());
        assertEquals(CategoriaTuristica.ARTE_MUSEOS.getNombre(), candidato.etiqueta());
        assertEquals("Coincide con tus intereses: "
                + CategoriaTuristica.ARTE_MUSEOS.getNombre() + ".", candidato.motivo());
        assertEquals("GEMINI", candidato.origenEvaluacion());
    }

    @Test
    void respuestaParcialProvocaFallbackParaTodos() {
        preparar(List.of(lugar(1), lugar(2)));
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of("id1", new ValoracionGemini(99, 60, "x", "y")));
        var result = service.evaluar(viaje);
        assertTrue(result.fallback());
        assertTrue(
                result.candidatos().stream().allMatch(c -> c.origenEvaluacion().equals("LOCAL")));
        assertFalse(result.advertencias().isEmpty());
    }

    @Test
    void vacioYErrorInvestigacionSonSeguros() {
        preparar(List.of());
        assertTrue(service.evaluar(viaje).candidatos().isEmpty());
        preparar(List.of(lugar(1)));
        when(ai.evaluar(anyList(), anyList(), anyString())).thenThrow(new IllegalStateException());
        when(research.investigar(anyList(), anyString())).thenThrow(new IllegalStateException());
        assertEquals(2, service.evaluar(viaje).advertencias().size());
    }

    @Test
    void plazoProvocaFallbackCompleto() {
        preparar(List.of(lugar(1), lugar(2)));
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenAnswer(
                        inv -> {
                            Thread.sleep(2000);
                            return Map.of();
                        });
        var limitado =
                new EvaluacionCandidatosService(
                        config,
                        prep,
                        ai,
                        research,
                        java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(100));
        var result = limitado.evaluar(viaje);
        assertTrue(result.fallback());
        assertEquals(2, result.candidatos().size());
        verify(research, never()).investigar(anyList(), anyString());
    }

    @Test
    void sinInvestigacionConfiguradaAvisaSinConsultar() {
        preparar(List.of(lugar(1)));
        when(research.disponible()).thenReturn(false);
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of("id1", new ValoracionGemini(80, 60, "x", "y")));
        var resultado = service.evaluar(viaje);
        assertFalse(resultado.fallback());
        assertTrue(
                resultado.advertencias().stream().anyMatch(a -> a.contains("Confirma los horarios y precios antes de ir")));
        verify(research, never()).investigar(anyList(), anyString());
    }

    @Test
    void moderadoTerminaInvestigacionAlCubrirCapacidad() {
        var moderado =
                new ConfiguracionItinerarioDto(
                        viaje.destino(),
                        viaje.disponibilidad(),
                        "",
                        new PreferenciasDto(
                                List.of(CategoriaTuristica.ARTE_MUSEOS),
                                List.of(),
                                List.of(),
                                NivelGasto.MODERADO,
                                5.0));
        var lugares = List.of(lugar(1), lugar(2));
        when(config.validar(moderado)).thenReturn(moderado);
        when(prep.preparar(moderado)).thenReturn(lugares);
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(
                        Map.of(
                                "id1", new ValoracionGemini(90, 480, "x", "y"),
                                "id2", new ValoracionGemini(80, 480, "x", "y")));
        when(research.investigar(anyList(), anyString()))
                .thenAnswer(
                        inv -> {
                            List<LugarDto> lote = inv.getArgument(0);
                            return Map.of(
                                    lote.get(0).id(),
                                    precio(lote.get(0).id().equals("id1") ? 60 : 10, "EUR"));
                        });
        var resultado = service.evaluar(moderado);
        verify(research, times(1)).investigar(anyList(), anyString());
        assertEquals(CostLevel.UNKNOWN, resultado.candidatos().get(1).coste());
    }

    private HallazgoGemini precio(double n, String moneda) {
        return new HallazgoGemini(
                null,
                List.of("https://example.com"),
                n,
                n,
                moneda,
                null,
                null,
                "https://example.com",
                null,
                null, null, null);
    }

    @Test
    void limitesCoste() {
        assertEquals(CostLevel.FREE, EvaluacionCandidatosService.coste(precio(0, "EUR")));
        assertEquals(CostLevel.LOW, EvaluacionCandidatosService.coste(precio(25, "EUR")));
        assertEquals(CostLevel.MEDIUM, EvaluacionCandidatosService.coste(precio(25.01, "EUR")));
        assertEquals(CostLevel.MEDIUM, EvaluacionCandidatosService.coste(precio(50, "EUR")));
        assertEquals(CostLevel.HIGH, EvaluacionCandidatosService.coste(precio(50.01, "EUR")));
        assertEquals(CostLevel.UNKNOWN, EvaluacionCandidatosService.coste(precio(1, "USD")));
    }

    @Test
    void ventanasUnoYDiezDias() {
        assertEquals(
                240,
                EvaluacionCandidatosService.minutosDisponibles(
                        new FechaHoraDto(
                                LocalDateTime.parse("2026-01-01T12:00"),
                                LocalDateTime.parse("2026-01-01T16:00"),
                                1)));
        assertEquals(
                4560,
                EvaluacionCandidatosService.minutosDisponibles(
                        new FechaHoraDto(
                                LocalDateTime.parse("2026-01-01T12:00"),
                                LocalDateTime.parse("2026-01-10T16:00"),
                                10)));
    }
}
