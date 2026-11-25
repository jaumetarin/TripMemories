package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmarEdicionDto(
        @NotNull @Positive Long revision,
        boolean aceptarAvisos
) {}
