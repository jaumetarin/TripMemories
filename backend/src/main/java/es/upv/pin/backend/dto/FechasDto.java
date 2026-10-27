package es.upv.pin.backend.dto;

import java.time.LocalDate;

public record FechasDto(
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer duracionDias) {
}