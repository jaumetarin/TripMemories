package es.upv.pin.backend.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.dto.FechaHoraDto;

@Service
public class FechaHoraService {

    private static final int MAXIMO_DIAS = 10;

    public FechaHoraDto validar(FechaHoraDto disponibilidad) {

        if (disponibilidad == null) {
            throw new IllegalArgumentException(
                    "Completa las fechas y horas de llegada y salida.");
        }

        LocalDateTime llegada = disponibilidad.llegada();
        LocalDateTime salida = disponibilidad.salida();

        if (llegada == null) {
            throw new IllegalArgumentException(
                    "Completa la fecha y la hora de llegada.");
        }

        if (salida == null) {
            throw new IllegalArgumentException(
                    "Completa la fecha y la hora de salida.");
        }

        if (!salida.isAfter(llegada)) {
            throw new IllegalArgumentException(
                    "La salida debe ser posterior a la llegada. Revisa las fechas y las horas para disponer de tiempo de viaje.");
        }

        // Contamos días de calendario, no bloques de 24 horas.
        long duracionDias = ChronoUnit.DAYS.between(llegada.toLocalDate(), salida.toLocalDate()) + 1;

        if (duracionDias > MAXIMO_DIAS) {
            throw new IllegalArgumentException(
                    "El viaje puede durar como máximo 10 días, contando el inicio y el fin.");
        }

        return new FechaHoraDto(
                llegada,
                salida,
                (int) duracionDias);
    }

    /**
     * Regla para la futura planificación: toda la actividad debe caber entre
     * la llegada del primer día y la salida del último día, incluidos los límites.
     */
    public void validarActividad(FechaHoraDto disponibilidad, LocalDateTime inicio, LocalDateTime fin) {
        FechaHoraDto intervalo = validar(disponibilidad);

        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new IllegalArgumentException("Indica el inicio y el fin de la actividad, con el fin posterior al inicio.");
        }

        if (inicio.isBefore(intervalo.llegada()) || fin.isAfter(intervalo.salida())) {
            throw new IllegalArgumentException("La actividad debe estar dentro de las fechas y horas de llegada y salida.");
        }
    }
}
