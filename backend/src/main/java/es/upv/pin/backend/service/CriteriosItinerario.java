package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.CategoriaTuristica;
import java.util.*;

/** Equilibrio de visitas automáticas. Una visita compartida ocupa un solo turno. */
public final class CriteriosItinerario {
    private CriteriosItinerario() {}

    public static List<CategoriaTuristica> categorias(ConfiguracionItinerarioDto config) {
        var preferencias = config.preferencias();
        return (preferencias.intereses().isEmpty() ? List.of(CategoriaTuristica.values()) : preferencias.intereses())
                .stream().filter(c -> !preferencias.categoriasExcluidas().contains(c)).distinct().sorted().toList();
    }

    public static CategoriaTuristica categoria(LugarDto lugar, List<CategoriaTuristica> activas,
            Map<CategoriaTuristica, Integer> usadas, List<LugarDto> disponibles) {
        return activas.stream().filter(c -> lugar.categorias() != null && lugar.categorias().contains(c))
                .min(Comparator.comparingInt((CategoriaTuristica c) -> usadas.getOrDefault(c, 0))
                        .thenComparingLong(c -> disponibles.stream().filter(l -> l.categorias() != null && l.categorias().contains(c)).count())
                        .thenComparing(Comparator.naturalOrder())).orElse(null);
    }

    public static int repeticion(LugarDto lugar, List<CategoriaTuristica> activas,
            Map<CategoriaTuristica, Integer> usadas) {
        return activas.stream().filter(c -> lugar.categorias() != null && lugar.categorias().contains(c))
                .mapToInt(c -> usadas.getOrDefault(c, 0)).min().orElse(Integer.MAX_VALUE);
    }

    /** Señales orientativas del proveedor: no certifican popularidad ni apertura. */
    public static Comparator<LugarDto> referencias() {
        return Comparator.comparing((LugarDto l) -> !l.referenciaDocumentada())
                .thenComparing(Comparator.comparingInt(LugarDto::nombresInternacionales).reversed());
    }

    /** Antes del límite de IA: elegidos por el usuario, y después turnos por interés y referentes. */
    public static List<LugarDto> muestra(List<LugarDto> candidatos, ConfiguracionItinerarioDto config, int limite) {
        var unicos = ReglasSeleccionLugares.aplicar(config, candidatos);
        List<LugarDto> resultado = new ArrayList<>(unicos.stream()
                .filter(l -> ReglasSeleccionLugares.orden(l, config) < 2).toList());
        List<LugarDto> pendientes = new ArrayList<>(unicos.stream()
                .filter(l -> ReglasSeleccionLugares.orden(l, config) == 2).toList());
        var activas = categorias(config);
        Map<CategoriaTuristica, Integer> usadas = new EnumMap<>(CategoriaTuristica.class);
        while (!pendientes.isEmpty() && resultado.size() < limite) {
            var elegido = pendientes.stream().min(Comparator.comparingInt((LugarDto l) -> repeticion(l, activas, usadas))
                    .thenComparing(referencias()).thenComparing(LugarDto::id)).orElseThrow();
            resultado.add(elegido);
            pendientes.remove(elegido);
            // En un empate, reservar el lugar compartido para el interés con menos alternativas.
            var categoria = categoria(elegido, activas, usadas, pendientes);
            if (categoria != null) usadas.merge(categoria, 1, Integer::sum);
        }
        return List.copyOf(resultado);
    }
}
