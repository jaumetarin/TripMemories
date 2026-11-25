package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DatosGeneracionGuardadosDto(
        List<DisponibilidadDiaDto> dias,
        List<String> advertencias,
        boolean parcial,
        int candidatosEvaluados,
        int candidatosEnMatriz,
        List<CandidatoEvaluadoDto> candidatos,
        boolean evaluacionFallback
) {

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
                itinerario.evaluacionFallback()
        );
    }

    public record DisponibilidadDiaDto(
            LocalDate fecha,
            LocalDateTime inicioDisponible,
            LocalDateTime finDisponible
    ) {}
}
