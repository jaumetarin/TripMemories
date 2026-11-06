package es.upv.pin.backend.dto;

import java.util.List;

public record ItinerarioGeneradoDto(
        List<DiaItinerarioDto> dias,
        List<String> advertencias,
        boolean parcial,
        int candidatosEvaluados,
        int candidatosEnMatriz,
        List<CandidatoEvaluadoDto> candidatos,
        boolean evaluacionFallback) {}
