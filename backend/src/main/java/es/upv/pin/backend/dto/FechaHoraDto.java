package es.upv.pin.backend.dto;

import java.time.LocalDateTime;

// Horas locales del destino. La duración siempre se calcula en el servicio.
public record FechaHoraDto(
        LocalDateTime llegada,
        LocalDateTime salida,
        Integer duracionDias) {
}
