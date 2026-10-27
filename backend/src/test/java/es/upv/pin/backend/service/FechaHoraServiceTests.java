package es.upv.pin.backend.service;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import es.upv.pin.backend.dto.FechaHoraDto;

import static org.junit.jupiter.api.Assertions.*;

class FechaHoraServiceTests {
    private final FechaHoraService servicio = new FechaHoraService();

    @ParameterizedTest
    @CsvSource({
        "2026-11-01T10:00, 2026-11-01T18:00, 1",
        "2026-11-01T10:00, 2026-11-01T10:01, 1",
        "2026-11-01T23:00, 2026-11-02T07:00, 2",
        "2026-11-01T10:00, 2026-11-10T18:00, 10",
        "2026-11-01T00:00, 2026-11-10T23:59, 10",
        "2026-10-30T20:00, 2026-11-02T08:00, 4",
        "2026-12-28T10:00, 2027-01-06T18:00, 10",
        "2028-02-27T10:00, 2028-03-01T18:00, 4",
        "2026-03-28T10:00, 2026-03-30T18:00, 3",
        "2026-10-24T10:00, 2026-10-26T18:00, 3"
    })
    void aceptaDisponibilidadPositivaYCuentaDiasDeCalendario(String llegada, String salida, int dias) {
        FechaHoraDto entrada = intervalo(llegada, salida);
        assertEquals(new FechaHoraDto(entrada.llegada(), entrada.salida(), dias), servicio.validar(entrada));
    }

    @Test
    void solicitaLosDatosIncompletos() {
        assertEquals("Completa las fechas y horas de llegada y salida.",
                assertThrows(IllegalArgumentException.class, () -> servicio.validar(null)).getMessage());
        assertEquals("Completa la fecha y la hora de llegada.", assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new FechaHoraDto(null, LocalDateTime.parse("2026-11-01T18:00"), null))).getMessage());
        assertEquals("Completa la fecha y la hora de salida.", assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new FechaHoraDto(LocalDateTime.parse("2026-11-01T10:00"), null, null))).getMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "2026-11-01T18:00, 2026-11-01T10:00",
        "2026-11-01T10:00, 2026-11-01T10:00",
        "2026-11-02T08:00, 2026-11-01T20:00"
    })
    void rechazaVentanasVaciasEInvertidas(String llegada, String salida) {
        assertEquals("La salida debe ser posterior a la llegada. Revisa las fechas y las horas para disponer de tiempo de viaje.",
                assertThrows(IllegalArgumentException.class, () -> servicio.validar(intervalo(llegada, salida))).getMessage());
    }

    @Test
    void rechazaOnceDiasAunqueHayanPasadoMenosDeDiezBloquesDe24Horas() {
        FechaHoraDto entrada = new FechaHoraDto(LocalDateTime.parse("2026-11-01T23:59"),
                LocalDateTime.parse("2026-11-11T00:00"), 1);
        assertEquals("El viaje puede durar como máximo 10 días, contando el inicio y el fin.",
                assertThrows(IllegalArgumentException.class, () -> servicio.validar(entrada)).getMessage());
    }

    @Test
    void recalculaLaDuracionDelClienteYConservaLasHoras() {
        FechaHoraDto entrada = new FechaHoraDto(LocalDateTime.parse("2026-11-01T10:15"),
                LocalDateTime.parse("2026-11-02T08:45"), 999);
        assertEquals(new FechaHoraDto(entrada.llegada(), entrada.salida(), 2), servicio.validar(entrada));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-11-01T20:00, 2026-11-03T08:00",
        "2026-11-01T20:00, 2026-11-01T21:00",
        "2026-11-02T00:00, 2026-11-02T23:59",
        "2026-11-03T07:00, 2026-11-03T08:00"
    })
    void aceptaActividadesDentroDeLaVentanaIncluidosSusLimites(String inicio, String fin) {
        assertDoesNotThrow(() -> servicio.validarActividad(intervalo("2026-11-01T20:00", "2026-11-03T08:00"),
                LocalDateTime.parse(inicio), LocalDateTime.parse(fin)));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-11-01T19:59, 2026-11-01T21:00",
        "2026-11-03T07:00, 2026-11-03T08:01",
        "2026-10-31T20:00, 2026-11-01T01:00",
        "2026-11-03T20:00, 2026-11-03T21:00",
        "2026-11-02T10:00, 2026-11-02T10:00",
        "2026-11-02T11:00, 2026-11-02T10:00"
    })
    void rechazaActividadesFueraDeLaVentanaOVacias(String inicio, String fin) {
        assertThrows(IllegalArgumentException.class, () -> servicio.validarActividad(
                intervalo("2026-11-01T20:00", "2026-11-03T08:00"), LocalDateTime.parse(inicio), LocalDateTime.parse(fin)));
    }

    @Test
    void rechazaActividadesIncompletasYViajesSinDisponibilidad() {
        FechaHoraDto viaje = intervalo("2026-11-01T10:00", "2026-11-01T18:00");
        assertThrows(IllegalArgumentException.class, () -> servicio.validarActividad(viaje, null, viaje.salida()));
        assertThrows(IllegalArgumentException.class, () -> servicio.validarActividad(viaje, viaje.llegada(), null));
        assertThrows(IllegalArgumentException.class, () -> servicio.validarActividad(
                intervalo("2026-11-01T10:00", "2026-11-01T10:00"), viaje.llegada(), viaje.salida()));
    }

    private FechaHoraDto intervalo(String llegada, String salida) {
        return new FechaHoraDto(LocalDateTime.parse(llegada), LocalDateTime.parse(salida), null);
    }
}
