package es.upv.pin.backend.dto;

public record DestinoDto(
        String id,
        String nombre,
        String region,
        String pais,
        Double latitud,
        Double longitud) {
}
