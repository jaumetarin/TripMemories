package es.upv.pin.backend.dto;

import java.time.Instant;

public record ConsumoGeneracionDto(int limite, int restantes, Instant reinicio, boolean enCurso) {}
