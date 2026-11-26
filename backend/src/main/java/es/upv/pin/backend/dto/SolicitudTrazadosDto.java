package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record SolicitudTrazadosDto(@NotNull LocalDate dia, @Positive Long revision) {}
