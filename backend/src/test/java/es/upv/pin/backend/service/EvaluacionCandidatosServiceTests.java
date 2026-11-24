package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

import es.upv.pin.backend.client.AiProvider;
import es.upv.pin.backend.client.ResearchProvider;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvaluacionCandidatosServiceTests {
    private final ConfiguracionItinerarioService config =
            mock(ConfiguracionItinerarioService.class);
    private final PreparacionCandidatosService prep = mock(PreparacionCandidatosService.class);
    private final AiProvider ai = mock(AiProvider.class);
    private final ResearchProvider research = mock(ResearchProvider.class);
    private final EvaluacionCandidatosService service =
            new EvaluacionCandidatosService(config, prep, ai, research, 120);

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
                                                                    80, null, 60, "bueno", "motivo")));
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
    void protegeLaSeleccionAntesDelLimiteDeIaYQuitaEvitados() {
        var obligatorio = lugar(999);
        var evitado = lugar(0);
        var entrada = new ConfiguracionItinerarioDto(viaje.destino(), viaje.disponibilidad(), "", viaje.preferencias(), null,
                List.of(new SeleccionLugarDto(obligatorio, EstadoLugar.OBLIGATORIO), new SeleccionLugarDto(evitado, EstadoLugar.EVITADO)));
        when(config.validar(entrada)).thenReturn(entrada);
        when(prep.preparar(entrada)).thenReturn(IntStream.range(0, 150).mapToObj(this::lugar).toList());
        when(ai.evaluar(anyList(), anyList(), anyString())).thenAnswer(inv -> {
            List<LugarDto> lote = inv.getArgument(0);
            return lote.stream().collect(Collectors.toMap(LugarDto::id,
                    l -> new ValoracionGemini(80, null, 60, "Visita", "Interés cultural")));
        });
        when(research.disponible()).thenReturn(false);
        var resultado = service.evaluar(entrada);
        assertEquals(120, resultado.candidatos().size());
        assertEquals(obligatorio, resultado.candidatos().getFirst().lugar());
        assertTrue(resultado.candidatos().stream().noneMatch(c -> c.lugar().id().equals(evitado.id())));
        verify(ai, times(3)).evaluar(anyList(), anyList(), anyString());
    }

    @Test
    void conservaDescripcionYFuenteSinConvertirElCosteDesconocidoEnGratis() {
        preparar(List.of(lugar(1)));
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of("id1", new ValoracionGemini(80, null, 60, "Museo", "Arte")));
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
                .thenReturn(Map.of("id1", new ValoracionGemini(80, null, 60, "Museo", "Arte")));
        when(research.investigar(anyList(), anyString())).thenReturn(Map.of("id1",
                new HallazgoGemini(null, List.of("https://museo.example"),
                        null, null, null, null, null, null, null, null,
                        "Texto sin respaldo.", null)));

        var candidato = service.evaluar(viaje).candidatos().getFirst();

        assertNull(candidato.descripcion());
        assertNull(candidato.fuenteDescripcion());
    }

    @Test
    void limitaLaEvaluacionATresLotesAunqueSeRecuperenSeiscientos() {
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
                                                    lugar -> new ValoracionGemini(70, null, 60, "Museo", "Coincide con arte y museos.")));
                        });

        var resultado = service.evaluar(viaje);

        assertEquals(120, resultado.candidatos().size());
        assertTrue(resultado.advertencias().stream().anyMatch(a -> a.contains("muestra variada")));
        assertFalse(resultado.fallback());
        verify(ai, times(3)).evaluar(anyList(), anyList(), anyString());
        verify(research, never()).investigar(anyList(), anyString());
    }

    @Test
    void conservaLaRelevanciaDeLosLotesCorrectosSiElSiguienteFalla() {
        preparar(IntStream.range(0, 85).mapToObj(this::lugar).toList());
        when(research.disponible()).thenReturn(false);
        when(ai.evaluar(anyList(), anyList(), anyString())).thenAnswer(inv -> {
            List<LugarDto> lote = inv.getArgument(0);
            if (!lote.getFirst().id().equals("id0")) throw new IllegalStateException("Cuota agotada");
            return lote.stream().collect(Collectors.toMap(LugarDto::id,
                    l -> new ValoracionGemini(80, 95, 60, "Arte", "Museo")));
        });
        var resultado = service.evaluar(viaje);
        assertTrue(resultado.fallback());
        assertEquals(40, resultado.candidatos().stream().filter(c -> c.origenEvaluacion().equals("GEMINI")).count());
        assertTrue(resultado.candidatos().stream().filter(c -> c.origenEvaluacion().equals("GEMINI"))
                .allMatch(c -> Integer.valueOf(95).equals(c.relevancia())));
        assertTrue(resultado.candidatos().stream().filter(c -> c.origenEvaluacion().equals("LOCAL"))
                .allMatch(c -> c.relevancia() == null && c.coste() == CostLevel.UNKNOWN));
        verify(ai, times(2)).evaluar(anyList(), anyList(), anyString());
    }

    @Test
    void laRelevanciaInfluyeEnLaPrioridadSinConvertirseEnInformacionConfirmada() {
        preparar(List.of(lugar(1), lugar(2)));
        when(research.disponible()).thenReturn(false);
        when(ai.evaluar(anyList(), anyList(), anyString())).thenReturn(Map.of(
                "id1", new ValoracionGemini(80, 20, 60, "Arte", "Visita"),
                "id2", new ValoracionGemini(80, 95, 60, "Arte", "Referente")));
        var resultado = service.evaluar(viaje);
        assertEquals("id2", resultado.candidatos().getFirst().lugar().id());
        assertEquals(95, resultado.candidatos().getFirst().relevancia());
        assertNull(resultado.candidatos().getFirst().horario());
        assertEquals(CostLevel.UNKNOWN, resultado.candidatos().getFirst().coste());
    }

    @Test
    void noExponeAfirmacionesDeGeminiSinRespaldoEnLosDatos() {
        preparar(List.of(lugar(1)));
        when(research.disponible()).thenReturn(false);
        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of("id1", new ValoracionGemini(
                        85, null, 90, "Entrada gratuita", "Abierto 24 horas y entrada gratuita.")));

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
                .thenReturn(Map.of("id1", new ValoracionGemini(99, null, 60, "x", "y")));
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
                        120,
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
                .thenReturn(Map.of("id1", new ValoracionGemini(80, null, 60, "x", "y")));
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
                                "id1", new ValoracionGemini(90, null, 480, "x", "y"),
                                "id2", new ValoracionGemini(80, null, 480, "x", "y")));
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
    @Test
    void conservaElCierreSemanalAunqueTavilyDevuelvaUnHorarioIncompleto() {
        String horarioSemanal = "Mo off; Tu-Su 09:00-18:00";

        LugarDto museo = new LugarDto(
                "museo",
                "Museo Azul",
                "Calle 1",
                40.4,
                -3.7,
                List.of(CategoriaTuristica.ARTE_MUSEOS),
                List.of(),
                horarioSemanal);

        LocalDateTime lunes = LocalDateTime.of(2026, 11, 23, 10, 0);

        ConfiguracionItinerarioDto viajeLunes =
                new ConfiguracionItinerarioDto(
                        viaje.destino(),
                        new FechaHoraDto(lunes, lunes.plusHours(8), 1),
                        viaje.alojamiento(),
                        viaje.preferencias());

        when(config.validar(viajeLunes)).thenReturn(viajeLunes);
        when(prep.preparar(viajeLunes)).thenReturn(List.of(museo));

        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of(
                        "museo",
                        new ValoracionGemini(
                                80, null, 60, "Museo", "Interés artístico")));

        String fuente = "https://museo.example/horarios";

        HallazgoGemini horarioExterno = new HallazgoGemini(
                null,
                List.of(fuente),
                null,
                null,
                null,
                "Horario 09:00 - 18:00",
                null,
                null,
                fuente,
                null,
                null,
                null);

        when(research.investigar(anyList(), anyString()))
                .thenReturn(Map.of("museo", horarioExterno));

        CandidatoEvaluadoDto candidato =
                service.evaluar(viajeLunes).candidatos().getFirst();

        assertEquals(horarioSemanal, candidato.horario());
        assertEquals("Geoapify (sin verificar)", candidato.horarioFuente());
        assertEquals(ScheduleStatus.UNVERIFIED, candidato.estadoHorario());

        EstadoHorarioVisita estado = new EvaluadorHorarioService()
                .evaluar(candidato, lunes, lunes.plusHours(1));

        assertEquals(
                EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE,
                estado);
    }
    @Test
    void usaElHorarioExternoCuandoGeoapifyNoAportaHorario() {
        preparar(List.of(lugar(1)));

        when(ai.evaluar(anyList(), anyList(), anyString()))
                .thenReturn(Map.of(
                        "id1",
                        new ValoracionGemini(
                                80, null, 60, "Museo", "Interés artístico")));

        String fuente = "https://museo.example/horarios";

        HallazgoGemini hallazgo = new HallazgoGemini(
                null,
                List.of(fuente),
                null,
                null,
                null,
                "Horario 09:00 - 18:00",
                null,
                null,
                fuente,
                null,
                null,
                null);

        when(research.investigar(anyList(), anyString()))
                .thenReturn(Map.of("id1", hallazgo));

        CandidatoEvaluadoDto candidato =
                service.evaluar(viaje).candidatos().getFirst();

        assertEquals("Horario 09:00 - 18:00", candidato.horario());
        assertEquals("Fuentes externas", candidato.horarioFuente());
        assertEquals(ScheduleStatus.PARTIAL, candidato.estadoHorario());
        assertTrue(candidato.fuentes().contains(fuente));
    }
    @ParameterizedTest
    @CsvSource({
        "id1,Lugar compartido,Otra dirección",
        "alias,HOTEL MUSEO,Otra dirección",
        "alias,Lugar compartido,CALLE DE PRUEBA 123"
    })
    @SuppressWarnings("unchecked")
    void noEnviaElAlojamientoAGeminiSiCoincideConUnCandidato(
        String identificador,
        String nombre,
        String direccion) {

        var alojamiento = new AlojamientoDto(
                "id1", "Hotel Museo", "Calle de Prueba 123", 1.0, 1.0);

        var entrada = new ConfiguracionItinerarioDto(
                viaje.destino(),
                viaje.disponibilidad(),
                alojamiento.nombre(),
                viaje.preferencias(),
                viaje.ritmo(),
                alojamiento);

        var compartido = new LugarDto(
                identificador, nombre, direccion, 1.0, 1.0,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);

        var otro = new LugarDto(
                "id2", "Otro museo", "Avenida de Prueba 20", 1.1, 1.1,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);

        when(config.validar(entrada)).thenReturn(entrada);
        when(prep.preparar(entrada)).thenReturn(List.of(compartido, otro));
        when(research.disponible()).thenReturn(false);

        when(ai.evaluar(anyList(), anyList(), anyString())).thenReturn(
                Map.of(
                        "id2", new ValoracionGemini(
                                80, null, 60, "Museo", "Interés cultural")));

        var resultado = service.evaluar(entrada);

        ArgumentCaptor<List<LugarDto>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(ai).evaluar(
                captor.capture(),
                eq(entrada.preferencias().intereses()),
                eq(entrada.destino().nombre()));

        assertEquals(List.of(otro), captor.getValue());
        assertEquals(2, resultado.candidatos().size());
        assertFalse(resultado.fallback());
   }
}
