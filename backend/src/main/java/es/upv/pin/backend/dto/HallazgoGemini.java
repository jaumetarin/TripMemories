package es.upv.pin.backend.dto;

import java.util.List;

/** Información atribuida a fuentes externas. Los campos desconocidos son null. */
public record HallazgoGemini(
        String informacion,
        List<String> fuentes,
        Double precioMinimo,
        Double precioMaximo,
        String moneda,
        String horario,
        Integer duracionMinutos,
        String fuentePrecio,
        String fuenteHorario,
        String fuenteDuracion) {
    public HallazgoGemini {
        fuentes = fuentes == null ? List.of() : List.copyOf(fuentes);
    }
}
