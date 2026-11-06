package es.upv.pin.backend.dto;

import java.util.List;

public record ResultadoValidacionPlan(List<String> incidencias, boolean parcial) {
    /** Las incidencias detectadas son incumplimientos, no avisos de incertidumbre. */
    public boolean valido() { return incidencias != null && incidencias.isEmpty(); }
}
