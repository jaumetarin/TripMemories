package es.upv.pin.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record PropuestaEdicionDto(
        UUID propuestaId,
        long revisionBase,
        ItinerarioGeneradoDto itinerario,
        List<IncidenciaEdicionDto> incidencias
) {
    public PropuestaEdicionDto {
        incidencias = List.copyOf(incidencias);
    }

    @JsonProperty("puedeGuardar")
    public boolean puedeGuardar() {
        return incidencias.stream()
                .noneMatch(incidencia ->
                        incidencia.nivel()
                                == IncidenciaEdicionDto.Nivel.CONFLICTO);
    }
}
