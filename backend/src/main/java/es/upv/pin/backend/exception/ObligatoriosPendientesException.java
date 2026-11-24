package es.upv.pin.backend.exception;

import java.util.List;

/** Una propuesta incompleta de obligatorios no se acepta como itinerario. */
public class ObligatoriosPendientesException extends IllegalArgumentException {
    private final List<String> problemas;

    public ObligatoriosPendientesException(List<String> problemas) {
        super("No hemos podido encajar todos tus lugares obligatorios. Revisa las fechas, la disponibilidad o las prioridades antes de generar de nuevo.");
        this.problemas = List.copyOf(problemas);
    }

    public List<String> getProblemas() { return problemas; }
}
