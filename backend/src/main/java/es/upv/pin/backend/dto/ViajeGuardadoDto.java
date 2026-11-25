package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ViajeGuardadoDto(
        ResumenViajeDto resumen,
        ConfiguracionItinerarioDto configuracion,
        ItinerarioGeneradoDto itinerario,
        List<ReferenciaActividadDto> actividades
) {

    public record ReferenciaActividadDto(
            UUID id,
            LocalDate dia,
            int orden
    ) {}
}
