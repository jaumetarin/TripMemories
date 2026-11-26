package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record TrazadosDiaDto(LocalDate dia, List<TramoDto> tramos) {
    public record TramoDto(String clave, String origenId, String destinoId,
            String estado, GeometriaTramoDto geometria) {}
}
