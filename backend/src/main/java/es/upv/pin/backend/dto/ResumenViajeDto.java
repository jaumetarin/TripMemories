package es.upv.pin.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResumenViajeDto(
        UUID id,
        String titulo,
        String destino,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Instant actualizadoEn,
        long revision,
        boolean eliminandoRecuerdos
) {
    public ResumenViajeDto(UUID id, String titulo, String destino, LocalDate fechaInicio,
            LocalDate fechaFin, Instant actualizadoEn, long revision) {
        this(id,titulo,destino,fechaInicio,fechaFin,actualizadoEn,revision,false);
    }
}
