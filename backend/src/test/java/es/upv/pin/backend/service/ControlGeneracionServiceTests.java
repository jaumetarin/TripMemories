package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

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
            new FechaHoraService(java.time.Clock.fixed(java.time.Instant.parse("2026-01-01T12:00:00Z"), java.time.ZoneOffset.UTC)), new PreferenciasService());
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
        String nombre = alojamiento == null ? "" : alojamiento.strip();

        AlojamientoDto localizado = nombre.isEmpty()
                ? null
                : new AlojamientoDto(
                        "hotel-" + nombre,
                        nombre,
                        "Calle de Prueba 10",
                        40.41,
                        -3.71);

        return new ConfiguracionItinerarioDto(
                new DestinoDto(
                        "madrid", "Madrid", null, null, 40.4, -3.7),
                new FechaHoraDto(
                        LocalDateTime.parse("2026-11-10T10:00"),
                        LocalDateTime.parse("2026-11-10T18:00"),
                        1),
                alojamiento,
                new PreferenciasDto(
                        List.of(
                                CategoriaTuristica.ARTE_MUSEOS,
                                CategoriaTuristica.NATURALEZA_PARQUES),
                        List.of(),
                        List.of(),
                        NivelGasto.MODERADO,
                        3.0),
                null,
                localizado);
}
    @Test
    void consultarNoLlamaAlGeneradorNiConsumeCuota() {
        assertEquals(3, control.consultar(sesion).restantes());
        assertFalse(control.consultar(sesion).enCurso());
        verifyNoInteractions(generacion);
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
        var equivalente = new ConfiguracionItinerarioDto(
        entrada.destino(),
        entrada.disponibilidad(),
        "Hotel",
        preferencias,
        entrada.ritmo(),
        entrada.alojamientoLocalizado());
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
    @Test
    void entregaAlGeneradorElAlojamientoSeleccionado() {
        var entrada = viaje("Hotel");

        control.generar(entrada, sesion);

        var captor = ArgumentCaptor.forClass(ConfiguracionItinerarioDto.class);

        verify(generacion).generar(captor.capture());

        assertEquals(
                entrada.alojamientoLocalizado(),
                captor.getValue().alojamientoLocalizado());
    }

    @Test
    void cambiarDeAlojamientoAunqueTengaElMismoNombreNoReutilizaElPlan() {
        var entrada = viaje("Hotel");

        control.generar(entrada, sesion);

        var otroAlojamiento = new AlojamientoDto(
                "otro-hotel",
                "Hotel",
                "Otra calle 20",
                40.42,
                -3.72);

        var modificada = new ConfiguracionItinerarioDto(
                entrada.destino(),
                entrada.disponibilidad(),
                entrada.alojamiento(),
                entrada.preferencias(),
                entrada.ritmo(),
                otroAlojamiento);

        var resultado = control.generar(modificada, sesion);

        assertFalse(resultado.reutilizado());
        verify(generacion, times(2)).generar(any());
    }
}
