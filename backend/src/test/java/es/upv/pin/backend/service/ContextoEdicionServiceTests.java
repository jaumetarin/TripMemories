package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.exception.RevisionViajeException;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ContextoEdicionServiceTests {
    private final ConsultaViajeService consultas =
            mock(ConsultaViajeService.class);
    private final ContextoEdicionService servicio =
            new ContextoEdicionService(consultas);
    private final UUID usuario = UUID.randomUUID();
    private final UUID viajeId = UUID.randomUUID();
    private final UUID visitaId = UUID.randomUUID();
    private final UUID margenId = UUID.randomUUID();
    private ViajeGuardadoDto viaje;

    @BeforeEach
    void preparar() {
        var fecha = LocalDate.of(2026, 12, 10);
        var inicio = fecha.atTime(10, 0);
        var margen = new BloqueItinerarioDto(
                TipoBloqueItinerario.MARGEN, inicio,
                inicio.plusMinutes(10), null,
                null, null, null, null, null, false, "Margen");
        var visita = new BloqueItinerarioDto(
                TipoBloqueItinerario.VISITA, inicio.plusMinutes(10),
                inicio.plusMinutes(70),
                mock(CandidatoEvaluadoDto.class),
                null, null, null, null, null, false, "Visita");

        var dia = new DiaItinerarioDto(
                fecha, inicio, inicio.plusHours(8),
                List.of(margen, visita));
        var plan = new ItinerarioGeneradoDto(
                List.of(dia), List.of(), false,
                1, 1, List.of(), false);
        var resumen = new ResumenViajeDto(
                viajeId, "Prueba", "Valencia", fecha, fecha,
                Instant.parse("2026-11-25T10:00:00Z"), 3);

        viaje = new ViajeGuardadoDto(
                resumen, null, plan,
                List.of(
                        new ViajeGuardadoDto.ReferenciaActividadDto(
                                margenId, fecha, 1),
                        new ViajeGuardadoDto.ReferenciaActividadDto(
                                visitaId, fecha, 2)));

        when(consultas.recuperar(usuario, viajeId)).thenReturn(viaje);
    }

    @Test
    void localizaLaVisitaPorSuOrdenEntreTodosLosBloques() {
        var resultado = servicio.prepararVisita(
                usuario, viajeId, 3, visitaId);

        assertSame(viaje, resultado.viaje());
        assertEquals(visitaId, resultado.referencia().id());
        assertSame(viaje.itinerario().dias().getFirst(), resultado.dia());
        assertSame(resultado.dia().bloques().get(1), resultado.bloque());
        verify(consultas).recuperar(usuario, viajeId);
    }

    @Test
    void respetaElRechazoDePropiedadDeLaConsulta() {
        UUID ajeno = UUID.randomUUID();
        var rechazo = new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No se ha encontrado el viaje.");
        when(consultas.recuperar(ajeno, viajeId)).thenThrow(rechazo);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> servicio.prepararVisita(
                        ajeno, viajeId, 3, visitaId));

        assertSame(rechazo, error);
        verify(consultas).recuperar(ajeno, viajeId);
        verify(consultas, never()).recuperar(usuario, viajeId);
    }

    @Test
    void rechazaUnaRevisionAntigua() {
        assertThrows(
                RevisionViajeException.class,
                () -> servicio.prepararVisita(
                        usuario, viajeId, 2, visitaId));
    }

    @Test
    void rechazaUnaActividadQueNoPerteneceAlViaje() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> servicio.prepararVisita(
                        usuario, viajeId, 3, UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void noPermiteEditarUnMargenComoSiFueraUnaVisita() {
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.prepararVisita(
                        usuario, viajeId, 3, margenId));
    }

    @Test
    void rechazaRevisionNoPositivaAntesDeConsultar() {
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.recuperar(usuario, viajeId, 0));

        verifyNoInteractions(consultas);
    }
}
