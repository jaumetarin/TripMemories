package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EstadoEdicionDto(PropuestaEdicionDto propuesta, List<DiaEditableDto> dias, boolean modificada) {
    public record DiaEditableDto(LocalDate fecha, List<VisitaEditableDto> visitas) {}
    public record VisitaEditableDto(UUID actividadId, CandidatoEvaluadoDto candidato, boolean obligatorio) {}
}
