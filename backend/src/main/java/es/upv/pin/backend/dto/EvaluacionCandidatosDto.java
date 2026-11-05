package es.upv.pin.backend.dto;

import java.util.List;

public record EvaluacionCandidatosDto(
        List<CandidatoEvaluadoDto> candidatos,
        boolean fallback,
        List<String> advertencias,
        int minutosDisponibles,
        int minutosInvestigados) {}
