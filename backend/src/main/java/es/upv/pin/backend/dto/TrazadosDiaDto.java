package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record TrazadosDiaDto(LocalDate dia, List<TramoDto> tramos, java.time.Instant reintentarDesde) {
    public TrazadosDiaDto(LocalDate dia, List<TramoDto> tramos) { this(dia, tramos, null); }
    public record TramoDto(String clave, String origenId, String destinoId,
            String estado, GeometriaTramoDto geometria) {}
}
