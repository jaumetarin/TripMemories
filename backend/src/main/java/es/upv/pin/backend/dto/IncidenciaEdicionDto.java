package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

public record IncidenciaEdicionDto(
        Nivel nivel,
        Codigo codigo,
        LocalDate dia,
        UUID actividadId,
        String lugarId,
        String mensaje
) {
    public enum Nivel {
        AVISO,
        CONFLICTO
    }

    public enum Codigo {
        OBLIGATORIO_INCUMPLIDO,
        HORARIO_NO_CONFIRMADO,
        HORARIO_INCOMPATIBLE,
        DISPONIBILIDAD_EXCEDIDA,
        RUTA_NO_DISPONIBLE,
        CONSULTA_RUTAS_FALLIDA,
        PLAN_INCONSISTENTE
    }
}
