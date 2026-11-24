package es.upv.pin.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.SubcategoriaTuristica;
import es.upv.pin.backend.model.TipoLugar;

// Datos del lugar preparado que pueden utilizar las siguientes fases del Planner.
public record LugarDto(String id, String nombre, String direccion,
        Double latitud, Double longitud,
        List<CategoriaTuristica> categorias, List<SubcategoriaTuristica> subcategorias,
        @JsonInclude(JsonInclude.Include.NON_NULL) String horario,
        TipoLugar tipo, Boolean referenciaDocumentada, Integer nombresInternacionales) {
    public LugarDto {
        tipo = tipo == null ? TipoLugar.OTRO : tipo;
        referenciaDocumentada = Boolean.TRUE.equals(referenciaDocumentada);
        nombresInternacionales = nombresInternacionales == null ? 0 : Math.clamp(nombresInternacionales, 0, 10);
    }
    public LugarDto(String id, String nombre, String direccion, Double latitud, Double longitud,
            List<CategoriaTuristica> categorias, List<SubcategoriaTuristica> subcategorias, String horario) {
        this(id, nombre, direccion, latitud, longitud, categorias, subcategorias, horario, TipoLugar.OTRO, false, 0);
    }
    public LugarDto(String id, String nombre, String direccion, Double latitud, Double longitud,
            List<CategoriaTuristica> categorias, List<SubcategoriaTuristica> subcategorias, String horario,
            TipoLugar tipo, Boolean referenciaDocumentada) {
        this(id, nombre, direccion, latitud, longitud, categorias, subcategorias, horario, tipo, referenciaDocumentada, 0);
    }
}
