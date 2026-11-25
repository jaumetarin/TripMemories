package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SustituirActividadDto(
        @NotNull(message = "Indica la revisión del viaje.")
        @Positive(message = "La revisión debe ser positiva.")
        Long revision,

        @NotBlank(message = "Selecciona una actividad alternativa.")
        @Size(
                max = 2048,
                message = "El identificador del candidato es demasiado largo."
        )
        String candidatoId
) {}
