package es.upv.pin.backend.dto;

import java.util.List;
import java.util.Map;

public record ItinerarioGeneradoDto(
        List<DiaItinerarioDto> dias,
        List<String> advertencias,
        boolean parcial,
        int candidatosEvaluados,
        int candidatosEnMatriz,
        List<CandidatoEvaluadoDto> candidatos,
        boolean evaluacionFallback,
        List<IncidenciaEdicionDto> incidenciasEdicion,
        Map<String, GeometriaTramoDto> trazados) {
    public ItinerarioGeneradoDto {
        incidenciasEdicion = incidenciasEdicion == null ? List.of() : List.copyOf(incidenciasEdicion);
        trazados = trazados == null ? Map.of() : Map.copyOf(trazados);
    }
    public ItinerarioGeneradoDto(List<DiaItinerarioDto> dias, List<String> advertencias, boolean parcial,
            int candidatosEvaluados, int candidatosEnMatriz, List<CandidatoEvaluadoDto> candidatos,
            boolean evaluacionFallback, List<IncidenciaEdicionDto> incidenciasEdicion) {
        this(dias, advertencias, parcial, candidatosEvaluados, candidatosEnMatriz, candidatos,
                evaluacionFallback, incidenciasEdicion, Map.of());
    }
    public ItinerarioGeneradoDto conTrazados(Map<String, GeometriaTramoDto> nuevos) {
        var combinados = new java.util.LinkedHashMap<>(trazados);
        combinados.putAll(nuevos);
        return new ItinerarioGeneradoDto(dias, advertencias, parcial, candidatosEvaluados,
                candidatosEnMatriz, candidatos, evaluacionFallback, incidenciasEdicion, combinados);
    }
    public ItinerarioGeneradoDto(List<DiaItinerarioDto> dias, List<String> advertencias, boolean parcial,
            int candidatosEvaluados, int candidatosEnMatriz, List<CandidatoEvaluadoDto> candidatos, boolean evaluacionFallback) {
        this(dias, advertencias, parcial, candidatosEvaluados, candidatosEnMatriz, candidatos, evaluacionFallback, List.of());
    }
}
