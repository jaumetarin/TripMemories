package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EstadoEdicionDto(PropuestaEdicionDto propuesta, List<DiaEditableDto> dias, boolean modificada,
        List<VisitaEliminadaDto> eliminadas) {
    public EstadoEdicionDto { eliminadas = eliminadas == null ? List.of() : List.copyOf(eliminadas); }
    public EstadoEdicionDto(PropuestaEdicionDto propuesta, List<DiaEditableDto> dias, boolean modificada) {
        this(propuesta, dias, modificada, List.of());
    }
    public record VisitaEliminadaDto(LocalDate dia, VisitaEditableDto visita) {}
    public record DiaEditableDto(LocalDate fecha, List<VisitaEditableDto> visitas) {}
    public record VisitaEditableDto(UUID actividadId, CandidatoEvaluadoDto candidato, boolean obligatorio) {}
}
