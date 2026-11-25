package es.upv.pin.backend.dto;

import java.util.List;

public record ItinerarioGeneradoDto(
        List<DiaItinerarioDto> dias,
        List<String> advertencias,
        boolean parcial,
        int candidatosEvaluados,
        int candidatosEnMatriz,
        List<CandidatoEvaluadoDto> candidatos,
        boolean evaluacionFallback,
        List<IncidenciaEdicionDto> incidenciasEdicion) {
    public ItinerarioGeneradoDto {
        incidenciasEdicion = incidenciasEdicion == null ? List.of() : List.copyOf(incidenciasEdicion);
    }
    public ItinerarioGeneradoDto(List<DiaItinerarioDto> dias, List<String> advertencias, boolean parcial,
            int candidatosEvaluados, int candidatosEnMatriz, List<CandidatoEvaluadoDto> candidatos, boolean evaluacionFallback) {
        this(dias, advertencias, parcial, candidatosEvaluados, candidatosEnMatriz, candidatos, evaluacionFallback, List.of());
    }
}
