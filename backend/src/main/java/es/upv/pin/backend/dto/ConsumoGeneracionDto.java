package es.upv.pin.backend.dto;

import java.time.Instant;

public record ConsumoGeneracionDto(int limite, int restantes, Instant reinicio, boolean enCurso,
        int limiteMensual, int restantesMensuales, Instant reinicioMensual) {}
