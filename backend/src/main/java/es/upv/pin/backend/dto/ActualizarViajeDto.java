package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ActualizarViajeDto(
        @NotBlank(message = "Introduce un título para el viaje.")
        @Size(max = 160, message = "El título admite hasta 160 caracteres.")
        String titulo,
        @NotNull(message = "Indica la revisión del viaje.")
        @Positive(message = "La revisión debe ser positiva.")
        Long revision
) {}
