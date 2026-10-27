package es.upv.pin.backend.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import es.upv.pin.backend.dto.FechasDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FechasServiceTests {
    private final FechasService servicio = new FechasService();

    @ParameterizedTest
    @CsvSource({
        "2026-11-01, 2026-11-01, 1",
        "2026-11-01, 2026-11-10, 10",
        "2026-10-30, 2026-11-02, 4",
        "2026-12-28, 2027-01-06, 10",
        "2028-02-27, 2028-03-01, 4",
        "2026-03-28, 2026-03-30, 3",
        "2026-10-24, 2026-10-26, 3"
    })
    void calculaDiasInclusivosSinDependerDeHoras(String inicio, String fin, int dias) {
        FechasDto entrada = new FechasDto(LocalDate.parse(inicio), LocalDate.parse(fin), null);
        assertEquals(new FechasDto(entrada.fechaInicio(), entrada.fechaFin(), dias), servicio.validar(entrada));
    }

    @Test
    void rechazaUnaSolicitudNula() {
        assertEquals("Selecciona las fechas del viaje.",
                assertThrows(IllegalArgumentException.class, () -> servicio.validar(null)).getMessage());
    }

    @Test
    void explicaQueFechaEstaPendiente() {
        assertEquals("Selecciona una fecha de inicio.", assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new FechasDto(null, LocalDate.of(2026, 11, 1), null))).getMessage());
        assertEquals("Selecciona una fecha de fin.", assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new FechasDto(LocalDate.of(2026, 11, 1), null, null))).getMessage());
    }

    @Test
    void rechazaUnIntervaloInvertido() {
        assertEquals("La fecha de fin debe ser igual o posterior a la de inicio.",
                assertThrows(IllegalArgumentException.class, () -> servicio.validar(
                        new FechasDto(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), null))).getMessage());
    }

    @Test
    void rechazaOnceDiasAunqueElClienteEnvieOtraDuracion() {
        assertEquals("El viaje puede durar como máximo 10 días, contando el inicio y el fin.",
                assertThrows(IllegalArgumentException.class, () -> servicio.validar(
                        new FechasDto(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 11), 1))).getMessage());
    }

    @Test
    void siempreRecalculaLaDuracionRecibida() {
        FechasDto resultado = servicio.validar(
                new FechasDto(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), 999));
        assertEquals(1, resultado.duracionDias());
    }
}
