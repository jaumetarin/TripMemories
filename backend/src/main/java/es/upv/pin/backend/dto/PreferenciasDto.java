package es.upv.pin.backend.dto;

import java.util.List;

import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.SubcategoriaTuristica;

public record PreferenciasDto(
        List<CategoriaTuristica> intereses,
        List<CategoriaTuristica> categoriasExcluidas,
        List<SubcategoriaTuristica> subcategoriasExcluidas) {
}
