package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuardarViajeDto(
        @NotBlank(message = "Introduce un título para el viaje.")
        @Size(
                max = 160,
                message = "El título admite hasta 160 caracteres."
        )
        String titulo
) {}
