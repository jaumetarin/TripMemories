package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class ControlGeneracionServiceTests {
    private final GeneracionItinerarioService generacion = mock(GeneracionItinerarioService.class);
    private final CuotaGeneracionService cuota = mock(CuotaGeneracionService.class);
    private final ConfiguracionItinerarioService configuraciones = new ConfiguracionItinerarioService(
        new FechaHoraService(Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)), new PreferenciasService());
    private final ControlGeneracionService control = new ControlGeneracionService(generacion, configuraciones, cuota, 15);
    private final MockHttpSession sesion = new MockHttpSession();
    private final UUID usuario = UUID.randomUUID();
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

    @Test void invalidasNoReservan() {
        assertThrows(IllegalArgumentException.class, () -> control.generar(null, usuario, sesion));
        verifyNoInteractions(cuota, generacion);
    }
    @Test void repeticionReutilizaSinReservar() {
        when(generacion.generar(any())).thenReturn(new ItinerarioGeneradoDto(List.of(), List.of(), true, 0, 0, List.of(), false));
        var primero = control.generar(viaje(""), usuario, sesion);
        var segundo = control.generar(viaje(""), usuario, sesion);
        assertFalse(primero.reutilizado()); assertTrue(segundo.reutilizado());
        assertEquals(primero.generacionId(), segundo.generacionId());
        verify(cuota, times(1)).reservar(usuario);
    }
    @Test void falloDelProveedorConservaReserva() {
        when(generacion.generar(any())).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class, () -> control.generar(viaje(""), usuario, sesion));
        verify(cuota).reservar(usuario);
    }
}
