package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.exception.ControlGeneracionException;
import es.upv.pin.backend.model.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;

class ControlGeneracionServiceTests {
    private final GeneracionItinerarioService generacion = mock(GeneracionItinerarioService.class);
    private final ConfiguracionItinerarioService configuraciones = new ConfiguracionItinerarioService(
            new FechaHoraService(), new PreferenciasService());
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-10T09:00:00Z"));
    private final Clock reloj = mock(Clock.class);
    private final MockHttpSession sesion = new MockHttpSession();
    private final ItinerarioGeneradoDto plan = new ItinerarioGeneradoDto(
            List.of(), List.of(), true, 0, 0, List.of(), false);
    private ControlGeneracionService control;

    @BeforeEach
    void preparar() {
        when(reloj.instant()).thenAnswer(inv -> ahora.get());
        control = new ControlGeneracionService(generacion, configuraciones, 3, 60, 15, reloj);
        when(generacion.generar(any())).thenReturn(plan);
    }

    private ConfiguracionItinerarioDto viaje(String alojamiento) {
        return new ConfiguracionItinerarioDto(new DestinoDto("madrid", "Madrid", null, null, 40.4, -3.7),
                new FechaHoraDto(LocalDateTime.parse("2026-11-10T10:00"), LocalDateTime.parse("2026-11-10T18:00"), 1),
                alojamiento, new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS, CategoriaTuristica.NATURALEZA_PARQUES),
                        List.of(), List.of(), NivelGasto.MODERADO, 3.0));
    }

    @Test
    void consultarNoLlamaAlGeneradorNiConsumeCuota() {
        assertEquals(3, control.consultar(sesion).restantes());
        assertFalse(control.consultar(sesion).enCurso());
        verifyNoInteractions(generacion);
    }

    @Test
    void cambiarPrioridadNoReutilizaUnPlanConLaSeleccionAnterior() {
        var base = viaje("");
        var lugar = new LugarDto("prado", "Museo del Prado", "Madrid", 40.4, -3.7,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);
        for (var estado : List.of(EstadoLugar.OBLIGATORIO, EstadoLugar.PREFERIDO, EstadoLugar.EVITADO)) {
            var config = new ConfiguracionItinerarioDto(base.destino(), base.disponibilidad(), base.alojamiento(),
                    base.preferencias(), base.ritmo(), List.of(new SeleccionLugarDto(lugar, estado)));
            assertFalse(control.generar(config, sesion).reutilizado());
            assertTrue(control.generar(config, sesion).reutilizado());
        }
        verify(generacion, times(3)).generar(any());
    }

    @Test
    void configuracionInvalidaNoConsumeNiLlamaProveedores() {
        assertThrows(IllegalArgumentException.class, () -> control.generar(null, sesion));
        assertEquals(3, control.consultar(sesion).restantes());
        verifyNoInteractions(generacion);
    }

    @Test
    void alLlegarAlLimiteNoVuelveAEntrarEnElGenerador() {
        control.generar(viaje("A"), sesion);
        control.generar(viaje("B"), sesion);
        control.generar(viaje("C"), sesion);
        var error = assertThrows(ControlGeneracionException.class, () -> control.generar(viaje("D"), sesion));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getEstado());
        assertTrue(error.getEsperaSegundos() > 0);
        assertEquals(0, control.consultar(sesion).restantes());
        verify(generacion, times(3)).generar(any());

        assertTrue(control.generar(viaje("C"), sesion).reutilizado());
        verify(generacion, times(3)).generar(any());
    }

    @Test
    void soloReutilizaLaUltimaConfiguracionYNoPierdeSuVigencia() {
        var primero = control.generar(viaje("A"), sesion);
        ahora.set(ahora.get().plusSeconds(60));
        var repetido = control.generar(viaje("A"), sesion);
        assertSame(plan, repetido.itinerario());
        assertTrue(repetido.reutilizado());
        assertEquals(primero.vigenteHasta(), repetido.vigenteHasta());
        assertEquals(2, control.consultar(sesion).restantes());
        control.generar(viaje("B"), sesion);
        assertFalse(control.generar(viaje("A"), sesion).reutilizado());
        verify(generacion, times(3)).generar(any());
    }

    @Test
    void elOrdenDeInteresesNoCreaOtraGeneracion() {
        var entrada = viaje(" Hotel ");
        control.generar(entrada, sesion);
        var preferencias = new PreferenciasDto(List.of(CategoriaTuristica.NATURALEZA_PARQUES,
                CategoriaTuristica.ARTE_MUSEOS, CategoriaTuristica.ARTE_MUSEOS), List.of(), List.of(), NivelGasto.MODERADO, 3.0);
        var equivalente = new ConfiguracionItinerarioDto(entrada.destino(), entrada.disponibilidad(), "Hotel", preferencias);
        assertTrue(control.generar(equivalente, sesion).reutilizado());
        verify(generacion).generar(any());
    }

    @Test
    void caducaElResultadoYDespuesSeRenuevaElLimite() {
        control.generar(viaje("A"), sesion);
        ahora.set(ahora.get().plus(Duration.ofMinutes(15)));
        assertFalse(control.generar(viaje("A"), sesion).reutilizado());
        assertEquals(1, control.consultar(sesion).restantes());
        ahora.set(ahora.get().plus(Duration.ofMinutes(45)));
        assertEquals(3, control.consultar(sesion).restantes());
        assertFalse(control.generar(viaje("A"), sesion).reutilizado());
        verify(generacion, times(3)).generar(any());
    }

    @Test
    void lasSesionesNoCompartenResultadosNiContadores() {
        control.generar(viaje("A"), sesion);
        var otra = new MockHttpSession();
        assertEquals(3, control.consultar(otra).restantes());
        assertFalse(control.generar(viaje("A"), otra).reutilizado());
        verify(generacion, times(2)).generar(any());
    }

    @Test
    void unFalloLiberaLaSesionConservaElResultadoAnteriorYCuentaComoIntento() {
        control.generar(viaje("A"), sesion);
        when(generacion.generar(any())).thenThrow(new IllegalStateException("Proveedor no disponible"));
        assertThrows(IllegalStateException.class, () -> control.generar(viaje("B"), sesion));
        assertFalse(control.consultar(sesion).enCurso());
        assertEquals(1, control.consultar(sesion).restantes());
        assertTrue(control.generar(viaje("A"), sesion).reutilizado());
        doReturn(plan).when(generacion).generar(any());
        assertFalse(control.generar(viaje("B"), sesion).reutilizado());
        verify(generacion, times(3)).generar(any());
    }

    @Test
    void dosPeticionesSimultaneasSoloInicianUnaGeneracion() throws Exception {
        CountDownLatch iniciada = new CountDownLatch(1);
        CountDownLatch terminar = new CountDownLatch(1);
        when(generacion.generar(any())).thenAnswer(inv -> {
            iniciada.countDown();
            if (!terminar.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("La prueba no liberó el generador");
            return plan;
        });
        try (ExecutorService hilos = Executors.newSingleThreadExecutor()) {
            Future<ControlGeneracionService.Resultado> primera = hilos.submit(() -> control.generar(viaje("A"), sesion));
            try {
                assertTrue(iniciada.await(3, TimeUnit.SECONDS));
                assertTrue(control.consultar(sesion).enCurso());
                for (String alojamiento : List.of("A", "B")) {
                    var error = assertThrows(ControlGeneracionException.class, () -> control.generar(viaje(alojamiento), sesion));
                    assertEquals(HttpStatus.CONFLICT, error.getEstado());
                }
                assertEquals(2, control.consultar(sesion).restantes());
                verify(generacion).generar(any());
            } finally { terminar.countDown(); }
            assertFalse(primera.get(3, TimeUnit.SECONDS).reutilizado());
            assertTrue(control.generar(viaje("A"), sesion).reutilizado());
            assertFalse(control.consultar(sesion).enCurso());
            verify(generacion).generar(any());
        }
    }
}
