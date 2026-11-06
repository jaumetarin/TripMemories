package es.upv.pin.backend.dto;

import es.upv.pin.backend.model.EstadoDesplazamiento;

public record DesplazamientoDto(
        String origenId,
        String destinoId,
        Double distanciaMetros,
        Double duracionSegundos,
        EstadoDesplazamiento estado,
        String fuente) {
}