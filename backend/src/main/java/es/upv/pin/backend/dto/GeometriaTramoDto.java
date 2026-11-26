package es.upv.pin.backend.dto;

import java.util.List;

/** Coordenadas GeoJSON: longitud, latitud. Solo se conservan respuestas válidas. */
public record GeometriaTramoDto(List<List<Double>> coordenadas, String fuente) {
    public GeometriaTramoDto {
        coordenadas = coordenadas.stream().map(List::copyOf).toList();
    }
}
