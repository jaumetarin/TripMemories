package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DatosGeneracionGuardadosDto(
        List<DisponibilidadDiaDto> dias,
        List<String> advertencias,
        boolean parcial,
        int candidatosEvaluados,
        int candidatosEnMatriz,
        List<CandidatoEvaluadoDto> candidatos,
        boolean evaluacionFallback,
        List<IncidenciaEdicionDto> incidenciasEdicion,
        Map<String, GeometriaTramoDto> trazados
) {
    public DatosGeneracionGuardadosDto {
        incidenciasEdicion = incidenciasEdicion == null ? List.of() : List.copyOf(incidenciasEdicion);
        trazados = trazados == null ? Map.of() : Map.copyOf(trazados);
    }
    public DatosGeneracionGuardadosDto(List<DisponibilidadDiaDto> dias, List<String> advertencias, boolean parcial,
            int candidatosEvaluados, int candidatosEnMatriz, List<CandidatoEvaluadoDto> candidatos,
            boolean evaluacionFallback, List<IncidenciaEdicionDto> incidenciasEdicion) {
        this(dias, advertencias, parcial, candidatosEvaluados, candidatosEnMatriz, candidatos,
                evaluacionFallback, incidenciasEdicion, Map.of());
    }
    public DatosGeneracionGuardadosDto(List<DisponibilidadDiaDto> dias, List<String> advertencias, boolean parcial,
            int candidatosEvaluados, int candidatosEnMatriz, List<CandidatoEvaluadoDto> candidatos, boolean evaluacionFallback) {
        this(dias, advertencias, parcial, candidatosEvaluados, candidatosEnMatriz, candidatos, evaluacionFallback, List.of());
    }

    public static DatosGeneracionGuardadosDto de(
            ItinerarioGeneradoDto itinerario
    ) {
        var dias = itinerario.dias().stream()
                .map(dia -> new DisponibilidadDiaDto(
                        dia.fecha(),
                        dia.inicioDisponible(),
                        dia.finDisponible()
                ))
                .toList();

        return new DatosGeneracionGuardadosDto(
                dias,
                List.copyOf(itinerario.advertencias()),
                itinerario.parcial(),
                itinerario.candidatosEvaluados(),
                itinerario.candidatosEnMatriz(),
                List.copyOf(itinerario.candidatos()),
                itinerario.evaluacionFallback(),
                itinerario.incidenciasEdicion(), itinerario.trazados()
        );
    }

    public record DisponibilidadDiaDto(
            LocalDate fecha,
            LocalDateTime inicioDisponible,
            LocalDateTime finDisponible
    ) {}
}
