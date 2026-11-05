package es.upv.pin.backend.service;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.dto.EtiquetasLugarGeoapifyDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.SubcategoriaTuristica;

import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static es.upv.pin.backend.model.SubcategoriaTuristica.*;

@Service
public class ClasificacionLugaresService {
    private final Map<CategoriaTuristica, List<String>> categorias = new EnumMap<>(CategoriaTuristica.class);
    private final Map<SubcategoriaTuristica, List<String>> subcategorias = new EnumMap<>(SubcategoriaTuristica.class);

    public ClasificacionLugaresService() {
        categorias.put(GASTRONOMIA, List.of("catering.restaurant", "catering.cafe", "catering.fast_food", "catering.food_court"));
        categorias.put(ARTE_MUSEOS, List.of("entertainment.museum", "entertainment.culture.gallery", "tourism.attraction.artwork"));
        categorias.put(HISTORIA_ARQUITECTURA, List.of("tourism.sights"));
        categorias.put(NATURALEZA_PARQUES, List.of("leisure.park", "beach", "natural"));
        categorias.put(CULTURA_LOCAL, List.of("entertainment.culture", "production.pottery"));
        categorias.put(OCIO_ENTRETENIMIENTO, List.of("entertainment.cinema", "entertainment.theme_park",
                "entertainment.water_park", "entertainment.zoo", "entertainment.aquarium", "entertainment.bowling_alley",
                "entertainment.escape_game", "commercial.shopping_mall", "catering.bar", "catering.pub", "adult.nightclub"));

        subcategorias.put(COMIDA_RAPIDA, List.of("catering.fast_food"));
        subcategorias.put(CAFETERIAS, List.of("catering.cafe"));
        subcategorias.put(MUSEOS, List.of("entertainment.museum"));
        subcategorias.put(GALERIAS_ARTE, List.of("entertainment.culture.gallery"));
        subcategorias.put(ARTE_URBANO, List.of("tourism.attraction.artwork"));
        subcategorias.put(EDIFICIOS_RELIGIOSOS, List.of("tourism.sights.place_of_worship", "tourism.sights.monastery",
                "religion.place_of_worship", "building.place_of_worship"));
        subcategorias.put(CASTILLOS_PALACIOS, List.of("tourism.sights.castle", "tourism.sights.manor"));
        subcategorias.put(YACIMIENTOS_ARQUEOLOGICOS, List.of("tourism.sights.archaeological_site"));
        subcategorias.put(PARQUES_JARDINES, List.of("leisure.park"));
        subcategorias.put(PLAYAS, List.of("beach"));
        subcategorias.put(TALLERES_ARTESANALES, List.of("production.pottery"));
        subcategorias.put(VIDA_NOCTURNA, List.of("catering.bar", "catering.pub", "adult.nightclub"));
        subcategorias.put(COMPRAS, List.of("commercial.shopping_mall"));
        subcategorias.put(PARQUES_ATRACCIONES, List.of("entertainment.theme_park", "entertainment.water_park"));
    }

    public String categoriasConsulta(CategoriaTuristica interes) {
        return String.join(",", categorias.get(interes));
    }

    public Set<SubcategoriaTuristica> subcategorias(LugarGeoapifyDto lugar) {
        Set<SubcategoriaTuristica> resultado = new LinkedHashSet<>();
        for (var entrada : subcategorias.entrySet()) {
            if (coincide(lugar.categories(), entrada.getValue())) resultado.add(entrada.getKey());
        }
        EtiquetasLugarGeoapifyDto raw = etiquetas(lugar);
        if (raw != null) {
            if ("fast_food".equals(raw.amenity())) resultado.add(COMIDA_RAPIDA);
            if ("cafe".equals(raw.amenity())) resultado.add(CAFETERIAS);
            if ("marketplace".equals(raw.amenity())) resultado.add(MERCADOS_TRADICIONALES);
            if ("marketplace".equals(raw.amenity()) && "food".equals(raw.marketplace())) resultado.add(MERCADOS_GASTRONOMICOS);
            if ("hiking".equals(raw.route())) resultado.add(SENDERISMO);
            if (raw.craft() != null && !raw.craft().isBlank() && !"no".equals(raw.craft())) resultado.add(TALLERES_ARTESANALES);
            if (List.of("palace", "castle", "manor").contains(texto(raw.historic()))) resultado.add(CASTILLOS_PALACIOS);
            if ("archaeological_site".equals(raw.historic())) resultado.add(YACIMIENTOS_ARQUEOLOGICOS);
            if ("place_of_worship".equals(raw.amenity())) resultado.add(EDIFICIOS_RELIGIOSOS);
            // Un teatro no implica espectáculo tradicional; solo una etiqueta explícita.
            if ("traditional".equals(raw.theatre_genre())) resultado.add(ESPECTACULOS_TRADICIONALES);
        }
        return resultado;
    }

    public Set<CategoriaTuristica> categorias(LugarGeoapifyDto lugar) {
        Set<CategoriaTuristica> resultado = new LinkedHashSet<>();
        for (var entrada : categorias.entrySet()) {
            if (coincide(lugar.categories(), entrada.getValue())) resultado.add(entrada.getKey());
        }
        for (SubcategoriaTuristica subcategoria : subcategorias(lugar)) resultado.add(subcategoria.getCategoria());
        return resultado;
    }

    public static boolean coincide(List<String> etiquetas, List<String> prefijos) {
        if (etiquetas == null) return false;
        for (String etiqueta : etiquetas) {
            if (etiqueta == null) continue;
            for (String prefijo : prefijos) {
                if (etiqueta.equals(prefijo) || etiqueta.startsWith(prefijo + ".")) return true;
            }
        }
        return false;
    }

    public static EtiquetasLugarGeoapifyDto etiquetas(LugarGeoapifyDto lugar) {
        return lugar.datasource() == null ? null : lugar.datasource().raw();
    }

    private static String texto(String valor) { return valor == null ? "" : valor; }
}
