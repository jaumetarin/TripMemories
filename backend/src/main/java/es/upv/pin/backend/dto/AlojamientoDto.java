package es.upv.pin.backend.dto;

public record AlojamientoDto(
        String id,
        String nombre,
        String direccion,
        Double latitud,
        Double longitud) {
}