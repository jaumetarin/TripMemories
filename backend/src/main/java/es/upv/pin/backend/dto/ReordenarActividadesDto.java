package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReordenarActividadesDto(
        @NotNull(message = "Indica la revisión del viaje.")
        @Positive(message = "La revisión debe ser positiva.")
        Long revision,

        @NotNull(message = "Indica el día que quieres modificar.")
        LocalDate dia,

        @NotNull(message = "Indica el orden de las visitas.")
        @Size(min = 1, message = "Indica al menos una visita.")
        List<@NotNull UUID> actividades
) {}
