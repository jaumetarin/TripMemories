package es.upv.pin.backend.dto;

import es.upv.pin.backend.model.CostConfidence;
import es.upv.pin.backend.model.CostLevel;
import es.upv.pin.backend.model.ScheduleStatus;

import java.util.List;

public record CandidatoEvaluadoDto(
        LugarDto lugar,
        int afinidad,
        int duracionMinutos,
        String etiqueta,
        String motivo,
        CostLevel coste,
        String informacion,
        List<String> fuentes,
        String horario,
        String horarioFuente,
        String origenEvaluacion,
        CostConfidence confianzaCoste,
        ScheduleStatus estadoHorario,
        Double precioMinimo,
        Double precioMaximo,
        String moneda,
        String descripcion,
        String fuenteDescripcion) {}
