package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.dto.CategoriaDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.dto.SubcategoriaDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.SubcategoriaTuristica;

@Service
public class PreferenciasService {

    public List<CategoriaDto> obtenerCatalogo() {
        List<CategoriaDto> catalogo = new ArrayList<>();

        for (CategoriaTuristica categoria : CategoriaTuristica.values()) {
            List<SubcategoriaDto> subcategorias = new ArrayList<>();
            for (SubcategoriaTuristica subcategoria : SubcategoriaTuristica.values()) {
                if (subcategoria.getCategoria() == categoria) {
                    subcategorias.add(new SubcategoriaDto(subcategoria, subcategoria.getNombre()));
                }
            }
            catalogo.add(new CategoriaDto(categoria, categoria.getNombre(), List.copyOf(subcategorias)));
        }

        return List.copyOf(catalogo);
    }

    // El futuro generador también debe llamar a este método antes de usar las preferencias.
    public PreferenciasDto validar(PreferenciasDto preferencias) {
        if (preferencias == null) {
            throw new IllegalArgumentException("Envía las preferencias del viaje.");
        }

        List<CategoriaTuristica> intereses = sinDuplicados(preferencias.intereses(), "intereses");
        List<CategoriaTuristica> categoriasExcluidas = sinDuplicados(
                preferencias.categoriasExcluidas(), "categorías excluidas");
        List<SubcategoriaTuristica> subcategoriasExcluidas = sinDuplicados(
                preferencias.subcategoriasExcluidas(), "subcategorías excluidas");

        for (CategoriaTuristica interes : intereses) {
            if (categoriasExcluidas.contains(interes)) {
                throw new IllegalArgumentException("La categoría «" + interes.getNombre()
                        + "» está seleccionada como interés y como categoría a evitar. "
                        + "Desmarca una de las dos opciones para continuar.");
            }
        }

        // Una exclusión parcial es compatible con el interés de su categoría principal.
        return new PreferenciasDto(intereses, categoriasExcluidas, subcategoriasExcluidas);
    }

    private <T> List<T> sinDuplicados(List<T> valores, String campo) {
        if (valores == null) {
            throw new IllegalArgumentException("Envía la lista de " + campo + ", aunque esté vacía.");
        }
        for (T valor : valores) {
            if (valor == null) {
                throw new IllegalArgumentException("La lista de " + campo + " no puede contener valores nulos.");
            }
        }
        // LinkedHashSet elimina duplicados y mantiene el orden elegido por el usuario.
        return List.copyOf(new LinkedHashSet<>(valores));
    }
}
