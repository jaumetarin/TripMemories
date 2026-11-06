package es.upv.pin.backend.dto;

import java.time.LocalDateTime;
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
        String descripcion) {}
