package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.dto.CategoriaDto;
import es.upv.pin.backend.dto.NivelGastoDto;
import es.upv.pin.backend.dto.OpcionesGastoDistanciaDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.dto.SubcategoriaDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.NivelGasto;
import es.upv.pin.backend.model.SubcategoriaTuristica;

@Service
public class PreferenciasService {
    private static final int DISTANCIA_MINIMA_KM = 1;
    private static final int DISTANCIA_MAXIMA_KM = 10;

    public OpcionesGastoDistanciaDto obtenerOpcionesGastoDistancia() {
        List<NivelGastoDto> niveles = new ArrayList<>();
        for (NivelGasto nivel : NivelGasto.values()) {
            niveles.add(new NivelGastoDto(nivel, nivel.getNombre(), nivel.getDescripcion()));
        }
        return new OpcionesGastoDistanciaDto(List.copyOf(niveles),
                DISTANCIA_MINIMA_KM, DISTANCIA_MAXIMA_KM, 1, NivelGasto.SIN_PREFERENCIA, 3,
                "Orienta el coste de las recomendaciones; no es un presupuesto ni un importe en euros.",
                "Distancia deseada entre una actividad y la siguiente. No es la distancia total del día ni un límite obligatorio.");
    }

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

        if (preferencias.nivelGasto() == null) {
            throw new IllegalArgumentException("Elige un nivel de gasto, también puedes seleccionar «Sin preferencia».");
        }
        Double distanciaKm = preferencias.distanciaKm();
        if (distanciaKm == null) {
            throw new IllegalArgumentException("Elige la distancia deseada entre actividades consecutivas.");
        }
        if (!Double.isFinite(distanciaKm) || distanciaKm < DISTANCIA_MINIMA_KM
                || distanciaKm > DISTANCIA_MAXIMA_KM || distanciaKm != Math.floor(distanciaKm)) {
            throw new IllegalArgumentException("Elige una distancia entre actividades de 1 a 10 km, en kilómetros enteros.");
        }

        // El Planner debe recibir estos valores: gasto cualitativo y distancia deseada
        // por trayecto, sin convertirlos en presupuesto ni en límites obligatorios.
        return new PreferenciasDto(intereses, categoriasExcluidas, subcategoriasExcluidas,
                preferencias.nivelGasto(), distanciaKm);
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
