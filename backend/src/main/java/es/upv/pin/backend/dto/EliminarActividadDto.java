package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EliminarActividadDto(
        @NotNull(message = "Indica la revisión del viaje.")
        @Positive(message = "La revisión debe ser positiva.")
        Long revision
) {}
