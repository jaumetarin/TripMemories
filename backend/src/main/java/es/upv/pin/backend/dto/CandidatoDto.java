package es.upv.pin.backend.dto;

import java.util.List;

public record CandidatoDto(
        String id,
        String nombre,
        Double latitud,
        Double longitud,
        List<String> categorias,
        String direccion) {
}
