package es.upv.pin.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EliminarViajeDto(@NotNull @Positive Long revision, @NotNull Boolean eliminarRecuerdos) {}
