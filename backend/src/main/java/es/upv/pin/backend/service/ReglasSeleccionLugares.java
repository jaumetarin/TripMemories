package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.EstadoLugar;
import es.upv.pin.backend.model.LimitesItinerario;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reglas locales compartidas por el formulario, la confirmación y el Planner. */
public final class ReglasSeleccionLugares {
    public static final int MAX_SELECCIONES = LimitesItinerario.MAXIMO_LUGARES;
    private ReglasSeleccionLugares() {}

    public static boolean compatible(LugarDto lugar, PreferenciasDto preferencias) {
        return lugar != null && lugar.categorias() != null && !lugar.categorias().isEmpty()
                && lugar.subcategorias() != null
                && lugar.categorias().stream().noneMatch(preferencias.categoriasExcluidas()::contains)
                && lugar.subcategorias().stream().noneMatch(preferencias.subcategoriasExcluidas()::contains)
                && (preferencias.intereses().isEmpty() || lugar.categorias().stream().anyMatch(preferencias.intereses()::contains));
    }

    public static List<SeleccionLugarDto> validar(List<SeleccionLugarDto> entrada, PreferenciasDto preferencias) {
        if (entrada == null) return List.of();
        if (entrada.size() > 200) throw new IllegalArgumentException("Se han enviado demasiadas selecciones.");
        List<SeleccionLugarDto> unicas = new ArrayList<>();
        for (var seleccion : entrada) {
            if (seleccion == null || seleccion.estado() == null || seleccion.lugar() == null)
                throw new IllegalArgumentException("Cada lugar seleccionado necesita un único estado.");
            var lugar = seleccion.lugar();
            if (lugar.id() == null || lugar.id().isBlank() || lugar.id().length() > 2048
                    || lugar.nombre() == null || lugar.nombre().isBlank() || lugar.nombre().length() > 250
                    || !IdentidadLugares.coordenadasValidas(lugar))
                throw new IllegalArgumentException("Selecciona un lugar localizable de los resultados del buscador.");
            if (!compatible(lugar, preferencias))
                throw new IllegalArgumentException("«" + lugar.nombre() + "» ya no encaja con las preferencias. Revisa los lugares seleccionados.");
            var anterior = unicas.stream().filter(s -> IdentidadLugares.mismoLugar(s.lugar(), lugar)).findFirst();
            if (anterior.isPresent()) {
                if (anterior.get().estado() != seleccion.estado())
                    throw new IllegalArgumentException("«" + lugar.nombre() + "» tiene estados contradictorios. Elige uno solo.");
            } else unicas.add(seleccion);
        }
        if (unicas.size() > MAX_SELECCIONES)
            throw new IllegalArgumentException("Puedes seleccionar hasta " + MAX_SELECCIONES + " lugares. Retira alguno antes de añadir otro.");
        return unicas.stream().sorted(Comparator.comparing(s -> s.lugar().id())).toList();
    }

    public static EstadoLugar estado(LugarDto lugar, ConfiguracionItinerarioDto config) {
        if (config.selecciones() == null) return null;
        return config.selecciones().stream().filter(s -> IdentidadLugares.mismoLugar(s.lugar(), lugar))
                .map(SeleccionLugarDto::estado).findFirst().orElse(null);
    }

    public static int orden(LugarDto lugar, ConfiguracionItinerarioDto config) {
        EstadoLugar estado = estado(lugar, config);
        return estado == EstadoLugar.OBLIGATORIO ? 0 : estado == EstadoLugar.PREFERIDO ? 1 : 2;
    }

    public static List<LugarDto> aplicar(ConfiguracionItinerarioDto config, List<LugarDto> candidatos) {
        List<LugarDto> resultado = new ArrayList<>();
        if (config.selecciones() != null) config.selecciones().stream()
                .filter(s -> s.estado() != EstadoLugar.EVITADO)
                .sorted(Comparator.comparingInt(s -> orden(s.lugar(), config)))
                .forEach(s -> resultado.add(s.lugar()));
        for (var lugar : candidatos) {
            if (estado(lugar, config) != EstadoLugar.EVITADO && resultado.stream()
                    .noneMatch(l -> IdentidadLugares.mismoLugar(l, lugar))) resultado.add(lugar);
        }
        return List.copyOf(resultado);
    }
}
