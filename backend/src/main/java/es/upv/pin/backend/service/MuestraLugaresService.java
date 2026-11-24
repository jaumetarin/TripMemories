package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Reparto para la pantalla: cada lugar ocupa un solo hueco y no se rellenan categorías con otras. */
@Service
public class MuestraLugaresService {
    public record Resultado(List<LugarDto> lugares, List<RepartoCategoriaLugaresDto> reparto,
            Map<String, CategoriaTuristica> categorias) {}

    public Resultado seleccionar(List<LugarDto> lugares, ConfiguracionItinerarioDto config, int limite, boolean equilibrar) {
        var intereses = (config.preferencias().intereses().isEmpty() ? List.of(CategoriaTuristica.values())
                : config.preferencias().intereses()).stream()
                .filter(c -> !config.preferencias().categoriasExcluidas().contains(c)).distinct().toList();
        Map<String, Double> distancias = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        lugares.forEach(l -> {
            distancias.put(l.id(), distancia(l, config.destino()));
            nombres.put(l.id(), IdentidadLugares.normalizar(l.nombre()));
        });
        if (!equilibrar || intereses.isEmpty()) {
            var encontrados = lugares.stream().sorted(Comparator.comparing((LugarDto l) -> !l.referenciaDocumentada())
                    .thenComparing(Comparator.comparingInt(LugarDto::nombresInternacionales).reversed())
                    .thenComparingDouble(l -> distancias.get(l.id())).thenComparing(l -> nombres.get(l.id())))
                    .limit(limite).toList();
            return new Resultado(encontrados, List.of(), Map.of());
        }
        int cupo = limite / intereses.size(); // Con 30 y cuatro intereses mostramos 28: siete de cada uno.
        Map<CategoriaTuristica, List<LugarDto>> opciones = new EnumMap<>(CategoriaTuristica.class);
        intereses.forEach(c -> opciones.put(c, lugares.stream().filter(l -> l.categorias().contains(c)).toList()));
        var ordenCategorias = intereses.stream().sorted(Comparator.comparingInt(c -> opciones.get(c).size())).toList();
        Map<String, CategoriaTuristica> asignados = new LinkedHashMap<>();
        Map<String, LugarDto> porId = new HashMap<>();
        lugares.forEach(l -> porId.put(l.id(), l));
        for (int ronda = 0; ronda < cupo; ronda++) for (var categoria : ordenCategorias) {
            asignar(categoria, opciones, asignados, porId, distancias, nombres, new HashSet<>(), cupo);
        }
        // También se alternan los intereses en el orden de las tarjetas, no solo en su recuento.
        List<List<LugarDto>> porCategoria = intereses.stream().map(c -> asignados.entrySet().stream()
                .filter(e -> e.getValue() == c).map(e -> porId.get(e.getKey())).toList()).toList();
        List<LugarDto> resultado = new ArrayList<>();
        for (int i = 0; i < cupo; i++) for (var lista : porCategoria) if (i < lista.size()) resultado.add(lista.get(i));
        var reparto = intereses.stream().map(c -> new RepartoCategoriaLugaresDto(c, c.getNombre(),
                (int) asignados.values().stream().filter(valor -> valor == c).count(), cupo)).toList();
        return new Resultado(List.copyOf(resultado), reparto, Map.copyOf(asignados));
    }

    private boolean asignar(CategoriaTuristica categoria, Map<CategoriaTuristica, List<LugarDto>> opciones,
            Map<String, CategoriaTuristica> asignados, Map<String, LugarDto> porId, Map<String, Double> distancias,
            Map<String, String> nombres, Set<String> visitados, int cupo) {
        Map<TipoLugar, Integer> tipos = new EnumMap<>(TipoLugar.class);
        asignados.forEach((id, c) -> { if (c == categoria) tipos.merge(porId.get(id).tipo(), 1, Integer::sum); });
        int compras = tipos.getOrDefault(TipoLugar.CENTRO_COMERCIAL, 0) + tipos.getOrDefault(TipoLugar.CALLE_COMERCIAL, 0);
        var ordenados = opciones.get(categoria).stream()
                .sorted(Comparator.comparingInt((LugarDto l) -> nivelTipo(l.tipo(), categoria))
                        .thenComparingInt(l -> tipos.getOrDefault(l.tipo(), 0))
                        .thenComparing(l -> !l.referenciaDocumentada())
                        .thenComparing(Comparator.comparingInt(LugarDto::nombresInternacionales).reversed())
                        .thenComparingDouble(l -> distancias.get(l.id()))
                        .thenComparing(l -> nombres.get(l.id())).thenComparing(LugarDto::id)).toList();
        // Primero se eligen candidatos libres; los intercambios solo sirven para resolver una escasez real.
        // Así una categoría compartida no provoca que se vayan perdiendo las mejores elecciones anteriores.
        for (var lugar : ordenados) {
            if (asignados.containsKey(lugar.id()) || visitados.contains(lugar.id())) continue;
            if (categoria == CategoriaTuristica.OCIO_ENTRETENIMIENTO && esCompra(lugar.tipo())
                    && compras >= Math.max(1, cupo / 3)) continue;
            asignados.put(lugar.id(), categoria);
            return true;
        }
        for (var lugar : ordenados) {
            if (asignados.get(lugar.id()) == categoria || visitados.contains(lugar.id())) continue;
            if (categoria == CategoriaTuristica.OCIO_ENTRETENIMIENTO && esCompra(lugar.tipo())
                    && compras >= Math.max(1, cupo / 3)) continue;
            visitados.add(lugar.id());
            var anterior = asignados.remove(lugar.id());
            // Si está compartido, se busca una alternativa para su categoría anterior. No se duplica.
            if (anterior != null && !asignar(anterior, opciones, asignados, porId, distancias, nombres, visitados, cupo)) {
                asignados.put(lugar.id(), anterior);
                continue;
            }
            asignados.put(lugar.id(), categoria);
            return true;
        }
        return false;
    }

    private static boolean esCompra(TipoLugar tipo) {
        return tipo == TipoLugar.CENTRO_COMERCIAL || tipo == TipoLugar.CALLE_COMERCIAL;
    }

    private static int nivelTipo(TipoLugar tipo, CategoriaTuristica categoria) {
        if (tipo == TipoLugar.OTRO) return 3;
        if (categoria == CategoriaTuristica.NATURALEZA_PARQUES)
            return tipo == TipoLugar.PARQUE || tipo == TipoLugar.PLAYA ? 0 : tipo == TipoLugar.PLAZA ? 1 : 2;
        if (categoria == CategoriaTuristica.HISTORIA_ARQUITECTURA)
            return tipo == TipoLugar.PLAZA || tipo == TipoLugar.MONUMENTO ? 0 : 1;
        return 0;
    }

    private static double distancia(LugarDto lugar, DestinoDto destino) {
        return IdentidadLugares.coordenadasValidas(lugar) && destino != null && destino.latitud() != null && destino.longitud() != null
                ? IdentidadLugares.distanciaMetros(lugar.latitud(), lugar.longitud(), destino.latitud(), destino.longitud())
                : Double.POSITIVE_INFINITY;
    }
}
