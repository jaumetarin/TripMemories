package es.upv.pin.backend.dto;

import java.util.List;
import java.util.Map;
import es.upv.pin.backend.model.CategoriaTuristica;

public record CatalogoLugaresDto(List<LugarDto> lugares, List<SeleccionLugarDto> selecciones,
        List<String> avisos, boolean hayMas, int limiteSelecciones, boolean catalogoCompleto,
        List<RepartoCategoriaLugaresDto> reparto, Map<String, CategoriaTuristica> categoriasMuestra) {
    public CatalogoLugaresDto(List<LugarDto> lugares, List<SeleccionLugarDto> selecciones,
            List<String> avisos, boolean hayMas, int limiteSelecciones) {
        this(lugares, selecciones, avisos, hayMas, limiteSelecciones, false, List.of(), Map.of());
    }
    public CatalogoLugaresDto(List<LugarDto> lugares, List<SeleccionLugarDto> selecciones,
            List<String> avisos, boolean hayMas, int limiteSelecciones, boolean catalogoCompleto) {
        this(lugares, selecciones, avisos, hayMas, limiteSelecciones, catalogoCompleto, List.of(), Map.of());
    }
}
