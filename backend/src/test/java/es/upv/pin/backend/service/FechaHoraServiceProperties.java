package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.FechaHoraDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

class FechaHoraServiceProperties {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 26);
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-26T09:00:00Z"), ZoneOffset.UTC);
    private final FechaHoraService servicio = new FechaHoraService(RELOJ);

    @Property(tries = 150)
    void intervalosDeUnoADiezDiasInclusivosSonValidosYCalculanDuracionExacta(
            @ForAll @IntRange(min = 0, max = 60) int offsetInicio,
            @ForAll @IntRange(min = 0, max = 9) int diasExtra,
            @ForAll @IntRange(min = 0, max = 23) int horaInicio,
            @ForAll @IntRange(min = 0, max = 23) int horaFin) {
        LocalDateTime llegada = HOY.plusDays(offsetInicio).atTime(horaInicio, 0);
        LocalDateTime salida = HOY.plusDays(offsetInicio + diasExtra).atTime(horaFin, 0);
        Assume.that(salida.isAfter(llegada));

        FechaHoraDto res = servicio.validar(new FechaHoraDto(llegada, salida, 999));
        assertEquals(llegada, res.llegada());
        assertEquals(salida, res.salida());
        assertEquals(diasExtra + 1, res.duracionDias());
    }

    @Property(tries = 100)
    void intervalosConMasDeDiezDiasInclusivosSonRechazados(
            @ForAll @IntRange(min = 0, max = 30) int offsetInicio,
            @ForAll @IntRange(min = 10, max = 40) int diasExtra) {
        LocalDateTime llegada = HOY.plusDays(offsetInicio).atTime(10, 0);
        LocalDateTime salida = HOY.plusDays(offsetInicio + diasExtra).atTime(18, 0);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new FechaHoraDto(llegada, salida, null)));
    }

    @Property(tries = 100)
    void salidaNoPosteriorALlegadaOFechaPasadaEsRechazada(
            @ForAll @IntRange(min = 1, max = 30) int diasPasado,
            @ForAll @IntRange(min = 0, max = 5) int diasAtras) {
        LocalDateTime pasado = HOY.minusDays(diasPasado).atTime(10, 0);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new FechaHoraDto(pasado, pasado.plusHours(5), null)));

        LocalDateTime futura = HOY.plusDays(2).atTime(14, 0);
        LocalDateTime anterior = futura.minusDays(diasAtras);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new FechaHoraDto(futura, anterior, null)));
    }

    @Property(tries = 120)
    void actividadSoloSeAceptaSiCabeIntegramenteDentroDelViaje(
            @ForAll @IntRange(min = 0, max = 400) int inicioMin,
            @ForAll @IntRange(min = 1, max = 300) int duracionMin) {
        LocalDateTime llegada = HOY.plusDays(1).atTime(10, 0);
        LocalDateTime salida = HOY.plusDays(1).atTime(18, 0);
        FechaHoraDto viaje = new FechaHoraDto(llegada, salida, null);

        LocalDateTime actInicio = llegada.plusMinutes(inicioMin);
        LocalDateTime actFin = actInicio.plusMinutes(duracionMin);

        if (!actFin.isAfter(salida)) {
            assertDoesNotThrow(() -> servicio.validarActividad(viaje, actInicio, actFin));
        } else {
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.validarActividad(viaje, actInicio, actFin));
        }
    }
}
