package es.upv.pin.backend.dto;

import java.util.List;

import es.upv.pin.backend.model.CategoriaTuristica;

public record CategoriaDto(
        CategoriaTuristica codigo,
        String nombre,
        List<SubcategoriaDto> subcategorias) {
}
