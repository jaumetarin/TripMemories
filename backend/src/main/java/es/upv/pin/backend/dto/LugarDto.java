package es.upv.pin.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.SubcategoriaTuristica;

// Datos del lugar preparado que pueden utilizar las siguientes fases del Planner.
public record LugarDto(String id, String nombre, String direccion,
        Double latitud, Double longitud,
        List<CategoriaTuristica> categorias, List<SubcategoriaTuristica> subcategorias,
        @JsonInclude(JsonInclude.Include.NON_NULL) String horario) {
}
