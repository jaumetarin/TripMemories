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
    public static final String CENTROS_COMERCIALES = "commercial.shopping_mall";
    private static final String ATRACCIONES_GENERALES = "tourism.sights,tourism.attraction";
    private final Map<CategoriaTuristica, List<String>> categorias = new EnumMap<>(CategoriaTuristica.class);
    private final Map<SubcategoriaTuristica, List<String>> subcategorias = new EnumMap<>(SubcategoriaTuristica.class);
    private final Map<CategoriaTuristica, List<String>> consultas = new EnumMap<>(CategoriaTuristica.class);

    public ClasificacionLugaresService() {
        categorias.put(GASTRONOMIA, List.of("catering.restaurant", "catering.cafe", "catering.fast_food", "catering.food_court"));
        categorias.put(ARTE_MUSEOS, List.of("entertainment.museum", "entertainment.culture.gallery", "tourism.attraction.artwork"));
        categorias.put(HISTORIA_ARQUITECTURA, List.of("tourism.sights"));
        categorias.put(NATURALEZA_PARQUES, List.of("leisure.park", "beach", "natural"));
        categorias.put(CULTURA_LOCAL, List.of("entertainment.culture", "production.pottery", "commercial.marketplace",
                "tourism.sights"));
        categorias.put(OCIO_ENTRETENIMIENTO, List.of("entertainment.theme_park",
                "entertainment.water_park", "entertainment.zoo", "entertainment.aquarium", "entertainment.bowling_alley",
                "entertainment.escape_game", "entertainment.amusement_arcade", "entertainment.activity_park",
                "entertainment.miniature_golf", "commercial.shopping_mall", "commercial.department_store"));

        // Grupos por interés: una búsqueda amplia no recupera siempre los mismos
        // lugares que otra centrada en un tipo concreto. No hay listas por ciudad.
        consultas.put(GASTRONOMIA, List.of("catering.restaurant",
                "catering.cafe,catering.fast_food,catering.food_court", "commercial.marketplace"));
        consultas.put(ARTE_MUSEOS, List.of("entertainment.museum",
                "entertainment.culture.gallery", "tourism.attraction.artwork"));
        consultas.put(HISTORIA_ARQUITECTURA, List.of(
                "tourism.sights.city_gate,tourism.sights.tower,tourism.sights.castle,tourism.sights.manor,tourism.sights.fort",
                "tourism.sights.building,tourism.sights.city_hall,tourism.sights.place_of_worship,tourism.sights.monastery,tourism.sights.archaeological_site",
                ATRACCIONES_GENERALES, "sport.stadium"));
        consultas.put(NATURALEZA_PARQUES, List.of("leisure.park", "beach", "natural"));
        consultas.put(CULTURA_LOCAL, List.of("entertainment.culture,production.pottery,commercial.marketplace",
                consultas.get(HISTORIA_ARQUITECTURA).getFirst(), ATRACCIONES_GENERALES, "sport.stadium"));
        consultas.put(OCIO_ENTRETENIMIENTO, List.of(
                CENTROS_COMERCIALES,
                "entertainment.bowling_alley,entertainment.escape_game,entertainment.amusement_arcade,entertainment.activity_park,entertainment.miniature_golf",
                "entertainment.theme_park,entertainment.water_park,entertainment.zoo,entertainment.aquarium",
                "commercial.clothing", "commercial.department_store", "commercial.books"));

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
        subcategorias.put(MERCADOS_TRADICIONALES, List.of("commercial.marketplace"));
        subcategorias.put(TALLERES_ARTESANALES, List.of("production.pottery"));
        subcategorias.put(ACTIVIDADES_RECREATIVAS, List.of("entertainment.bowling_alley", "entertainment.escape_game",
                "entertainment.amusement_arcade", "entertainment.activity_park", "entertainment.miniature_golf"));
        subcategorias.put(ZOOS_ACUARIOS, List.of("entertainment.zoo", "entertainment.aquarium"));
        subcategorias.put(COMPRAS, List.of("commercial.shopping_mall", "commercial.department_store"));
        subcategorias.put(PARQUES_ATRACCIONES, List.of("entertainment.theme_park", "entertainment.water_park"));
    }

    public List<String> consultasPorInteres(CategoriaTuristica interes) {
        return consultas.get(interes);
    }

    public int maxPaginas(String consulta) {
        // Solo ampliamos el respaldo de atracciones, hasta 500 resultados en páginas de 100.
        return ATRACCIONES_GENERALES.equals(consulta) ? 5 : 1;
    }

    public int prioridadBusqueda(LugarGeoapifyDto lugar) {
        // Es una preferencia por tipo para la muestra, no una medida de popularidad.
        if (atraccionGeneral(lugar) || coincide(lugar.categories(), List.of("tourism.sights.city_gate",
                "tourism.sights.tower", "tourism.sights.castle", "tourism.sights.manor", "tourism.sights.city_hall",
                "tourism.sights.square"))) return 3;
        if (coincide(lugar.categories(), List.of("tourism.attraction.artwork", "tourism.sights.memorial"))) return 1;
        return 2;
    }

    private boolean atraccionGeneral(LugarGeoapifyDto lugar) {
        return coincide(lugar.categories(), List.of("tourism.attraction"))
                && !coincide(lugar.categories(), List.of("tourism.attraction.artwork",
                    "tourism.attraction.fountain", "tourism.attraction.viewpoint", "tourism.attraction.clock",
                    "entertainment", "catering", "commercial", "leisure", "natural", "beach"));
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
        if (descartadoParaOcio(lugar)) resultado.removeIf(s -> s.getCategoria() == OCIO_ENTRETENIMIENTO);
        return resultado;
    }

    public Set<CategoriaTuristica> categorias(LugarGeoapifyDto lugar) {
        Set<CategoriaTuristica> resultado = new LinkedHashSet<>();
        for (var entrada : categorias.entrySet()) {
            if (coincide(lugar.categories(), entrada.getValue())) resultado.add(entrada.getKey());
        }
        for (SubcategoriaTuristica subcategoria : subcategorias(lugar)) resultado.add(subcategoria.getCategoria());
        // El proveedor puede etiquetar un conjunto arquitectónico solo como atracción.
        // Se considera candidato cultural/arquitectónico, sin afirmar su historia.
        if (atraccionGeneral(lugar)) {
            resultado.add(HISTORIA_ARQUITECTURA);
            resultado.add(CULTURA_LOCAL);
        }
        if (descartadoParaOcio(lugar)) resultado.remove(OCIO_ENTRETENIMIENTO);
        return resultado;
    }

    public boolean descartadoParaOcio(LugarGeoapifyDto lugar) {
        var raw = etiquetas(lugar);
        return coincide(lugar.categories(), List.of("entertainment.cinema", "catering.cafe", "catering.bar",
                "catering.pub", "catering.biergarten", "adult.nightclub"))
                || (raw != null && List.of("cinema", "cafe", "bar", "pub", "nightclub").contains(texto(raw.amenity())));
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
