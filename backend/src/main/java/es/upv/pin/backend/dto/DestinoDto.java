package es.upv.pin.backend.dto;

public record DestinoDto(
        String id,
        String nombre,
        String region,
        String pais,
        double latitud,
        double longitud) {
}
