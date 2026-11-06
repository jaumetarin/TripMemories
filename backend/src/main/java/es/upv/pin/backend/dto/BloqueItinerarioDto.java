package es.upv.pin.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import es.upv.pin.backend.model.TipoBloqueItinerario;

public record BloqueItinerarioDto(
        TipoBloqueItinerario tipo,
        LocalDateTime inicio,
        LocalDateTime fin,
        CandidatoEvaluadoDto candidato,
        String origenId,
        String destinoId,
        Double distanciaMetros,
        Double duracionSegundos,
        String fuente,
        boolean condicionada,
        String descripcion,
        OpcionComidaDto restaurante,
        List<OpcionComidaDto> alternativasComida) {
    public BloqueItinerarioDto(TipoBloqueItinerario tipo, LocalDateTime inicio, LocalDateTime fin,
            CandidatoEvaluadoDto candidato, String origenId, String destinoId, Double distanciaMetros,
            Double duracionSegundos, String fuente, boolean condicionada, String descripcion) {
        this(tipo, inicio, fin, candidato, origenId, destinoId, distanciaMetros, duracionSegundos,
                fuente, condicionada, descripcion, null, List.of());
    }
}
