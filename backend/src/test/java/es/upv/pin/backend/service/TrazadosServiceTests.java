package es.upv.pin.backend.service;

import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrazadosServiceTests {
    final OpenRouteServiceClient cliente = mock(OpenRouteServiceClient.class);
    final TrazadosService servicio = new TrazadosService(cliente);
    final MockHttpSession sesion = new MockHttpSession();
    final LocalDate fecha = LocalDate.of(2027, 1, 1);
    final ConfiguracionItinerarioDto config = new ConfiguracionItinerarioDto(
            new DestinoDto("ciudad", "Ciudad", "", "España", 39.47, -.37),
            new FechaHoraDto(fecha.atTime(10, 0), fecha.atTime(18, 0), 1), "",
            new PreferenciasDto(List.of(), List.of(), List.of(), NivelGasto.SIN_PREFERENCIA, 3.0),
            new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10));

    CandidatoEvaluadoDto candidato(String id, Double lat) {
        return new CandidatoEvaluadoDto(new LugarDto(id, id, "Ciudad", lat, -.371,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null), 80, 60, "Arte", "Visita",
                CostLevel.UNKNOWN, null, List.of(), null, null, "LOCAL", CostConfidence.UNKNOWN,
                ScheduleStatus.UNVERIFIED, null, null, null, null, null, null);
    }
    BloqueItinerarioDto traslado(String a, String b, int hora) {
        return new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO, fecha.atTime(hora, 0), fecha.atTime(hora, 10),
                null, a, b, 500.0, 600.0, "openrouteservice", false, "Desplazamiento");
    }
    ItinerarioGeneradoDto plan(Double latA) {
        var a = candidato("a", latA);
        var b = candidato("b", 39.472);
        var r = new OpcionComidaDto("r", "Restaurante", 39.473, -.373, "Centro");
        var bloques = List.of(traslado("referencia:inicio", "a", 10),
                new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, fecha.atTime(10, 10), fecha.atTime(11, 10), a,
                        null, null, null, null, null, true, "Visita"),
                traslado("a", "r", 12),
                new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA, fecha.atTime(13, 0), fecha.atTime(15, 0), null,
                        null, null, null, null, null, true, "Comida", r, List.of()),
                traslado("r", "b", 15),
                new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, fecha.atTime(15, 10), fecha.atTime(16, 10), b,
                        null, null, null, null, null, true, "Visita"));
        return new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(fecha, fecha.atTime(10, 0), fecha.atTime(18, 0), bloques)),
                List.of(), false, 2, 2, List.of(a, b), false);
    }
    void proveedorDisponible() {
        when(cliente.calcularGeometria(any(), any())).thenAnswer(invocacion -> geometria(invocacion.getArgument(0), invocacion.getArgument(1)));
    }
    GeometriaTramoDto geometria(PuntoRutaDto a, PuntoRutaDto b) {
        return new GeometriaTramoDto(List.of(List.of(a.longitud(), a.latitud()),
                List.of(b.longitud(), b.latitud())), "openrouteservice");
    }

    @Test void obtieneSoloLosTresTramosDelPlanIncluidosInicioYRestaurante() {
        proveedorDisponible();
        var resultado = servicio.obtener(config, plan(39.471), fecha, sesion);
        assertEquals(List.of("a", "r", "b"), resultado.tramos().stream().map(TrazadosDiaDto.TramoDto::destinoId).toList());
        assertTrue(resultado.tramos().stream().allMatch(t -> t.estado().equals("DISPONIBLE")));
        verify(cliente, times(3)).calcularGeometria(any(), any());
        verify(cliente).calcularGeometria(new PuntoRutaDto("referencia:inicio", 39.47, -.37), new PuntoRutaDto("a", 39.471, -.371));
    }

    @Test void reutilizaLaSesionYLosTrazadosGuardadosInclusoEnOtraSesion() {
        proveedorDisponible();
        var plan = plan(39.471);
        var uno = servicio.obtener(config, plan, fecha, sesion);
        assertEquals(uno, servicio.obtener(config, plan, fecha, sesion));
        var guardado = plan.conTrazados(TrazadosService.disponibles(uno));
        assertEquals(uno, servicio.obtener(config, guardado, fecha, new MockHttpSession()));
        assertEquals(guardado.trazados(), DatosGeneracionGuardadosDto.de(guardado).trazados());
        verify(cliente, times(3)).calcularGeometria(any(), any());
    }

    @Test void unFalloNoOcultaLosOtrosCaminosNiSeCacheaComoExito() {
        proveedorDisponible();
        doThrow(new IllegalStateException("fallo simulado"))
                .doAnswer(i -> geometria(i.getArgument(0), i.getArgument(1)))
                .when(cliente).calcularGeometria(argThat(p -> p != null && p.id().equals("a")), any());
        var parcial = servicio.obtener(config, plan(39.471), fecha, sesion);
        assertEquals(List.of("DISPONIBLE", "NO_DISPONIBLE", "DISPONIBLE"), parcial.tramos().stream().map(TrazadosDiaDto.TramoDto::estado).toList());
        assertNull(parcial.tramos().get(1).geometria());
        assertEquals(2, TrazadosService.disponibles(parcial).size());
        assertTrue(servicio.obtener(config, plan(39.471), fecha, sesion).tramos().stream().allMatch(t -> t.geometria() != null));
        verify(cliente, times(4)).calcularGeometria(any(), any());
    }

    @Test void coordenadasAusentesNoInventanUnaLineaNiBloqueanOtrosTramos() {
        proveedorDisponible();
        var resultado = servicio.obtener(config, plan(null), fecha, sesion);
        assertEquals(List.of("COORDENADAS_AUSENTES", "COORDENADAS_AUSENTES", "DISPONIBLE"), resultado.tramos().stream().map(TrazadosDiaDto.TramoDto::estado).toList());
        verify(cliente, times(1)).calcularGeometria(any(), any());
    }

    @Test void unDiaAjenoNoConsultaElProveedor() {
        assertThrows(IllegalArgumentException.class, () -> servicio.obtener(config, plan(39.471), fecha.plusDays(1), sesion));
        verifyNoInteractions(cliente);
    }

    @Test void cambiarCoordenadasRetiraLosDosTramosAntiguosPeroConservaElIntacto() {
        proveedorDisponible();
        var original = servicio.obtener(config, plan(39.471), fecha, sesion);
        var cambios = plan(39.475);
        var vigentes = TramosPlan.vigentes(config, cambios, TrazadosService.disponibles(original));
        assertEquals(Set.of(original.tramos().get(2).clave()), vigentes.keySet());
        var actualizado = servicio.obtener(config, cambios.conTrazados(vigentes), fecha, sesion);
        assertNotEquals(original.tramos().getFirst().clave(), actualizado.tramos().getFirst().clave());
        verify(cliente, times(5)).calcularGeometria(any(), any());
    }

    @Test void dosConsultasSimultaneasCompartenLaPeticionDeCadaTramo() throws Exception {
        var comienza = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        when(cliente.calcularGeometria(any(), any())).thenAnswer(i -> {
            comienza.countDown();
            assertTrue(continuar.await(3, TimeUnit.SECONDS));
            return geometria(i.getArgument(0), i.getArgument(1));
        });
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            var uno = hilos.submit(() -> servicio.obtener(config, plan(39.471), fecha, sesion));
            assertTrue(comienza.await(2, TimeUnit.SECONDS));
            var dos = hilos.submit(() -> servicio.obtener(config, plan(39.471), fecha, sesion));
            continuar.countDown();
            assertEquals(uno.get(5, TimeUnit.SECONDS), dos.get(5, TimeUnit.SECONDS));
        }
        verify(cliente, times(3)).calcularGeometria(any(), any());
    }
}
