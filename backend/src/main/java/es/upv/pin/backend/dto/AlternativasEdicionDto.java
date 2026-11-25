package es.upv.pin.backend.dto;

import java.util.List;

public record AlternativasEdicionDto(List<CandidatoEvaluadoDto> candidatos, String mensaje) {}
