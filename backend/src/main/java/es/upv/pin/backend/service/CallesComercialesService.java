package es.upv.pin.backend.service;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Descubre calles a partir de comercios reales; no mantiene listas por ciudad ni usa IA. */
public class CallesComercialesService {
    private static final int MAX_CALLES = 3;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CallesComercialesService.class);
    private final GeoapifyClient cliente;

    public CallesComercialesService(GeoapifyClient cliente) { this.cliente = cliente; }

    public List<LugarGeoapifyDto> buscar(DestinoDto destino, List<LugarGeoapifyDto> comercios) {
        Map<String, Map<String, LugarGeoapifyDto>> porCalle = new LinkedHashMap<>();
        for (var comercio : comercios) {
            if (comercio.street() == null || comercio.street().isBlank() || comercio.place_id() == null
                    || !coordenadasValidas(comercio) || !ClasificacionLugaresService.coincide(comercio.categories(),
                    List.of("commercial.clothing", "commercial.department_store", "commercial.books"))) continue;
            porCalle.computeIfAbsent(IdentidadLugares.normalizar(comercio.street()), k -> new LinkedHashMap<>())
                    .merge(comercio.place_id(), comercio, (a, b) -> a.housenumber() == null ? b : a);
        }
        var candidatas = porCalle.values().stream().map(m -> List.copyOf(m.values()))
                .filter(this::esZonaComercial)
                .sorted(Comparator.comparingInt(this::variedadComercial).reversed()
                        .thenComparing(Comparator.<List<LugarGeoapifyDto>>comparingInt(List::size).reversed()))
                .limit(MAX_CALLES).toList();
        List<LugarGeoapifyDto> calles = new ArrayList<>();
        for (var grupo : candidatas) {
            String nombre = grupo.getFirst().street();
            try {
                var calle = cliente.buscarCalle(nombre, destino);
                if (calle == null || !"street".equals(calle.result_type()) || !coordenadasValidas(calle)
                        || calle.place_id() == null || calle.place_id().isBlank()) continue;
                String devuelto = calle.street() == null ? calle.name() : calle.street();
                if (!IdentidadLugares.normalizar(nombre).equals(IdentidadLugares.normalizar(devuelto))) continue;
                // Las horas de las tiendas no se convierten en el horario de la calle.
                calles.add(calle);
            } catch (IllegalStateException e) {
                LOG.warn("No se pudo localizar una calle comercial; se mantienen las demás propuestas de ocio");
            }
        }
        LOG.info("Calles comerciales: {} consultas de geocodificación, {} calles localizadas", candidatas.size(), calles.size());
        return List.copyOf(calles);
    }

    private int variedadComercial(List<LugarGeoapifyDto> grupo) {
        // Un eje con moda, libros y grandes almacenes tiene más variedad para un paseo.
        return (int) List.of("commercial.clothing", "commercial.department_store", "commercial.books").stream()
                .filter(tipo -> grupo.stream().anyMatch(l -> ClasificacionLugaresService.coincide(l.categories(), List.of(tipo)))).count();
    }

    private boolean esZonaComercial(List<LugarGeoapifyDto> grupo) {
        if (grupo.size() < 3) return false;
        long portales = grupo.stream().map(LugarGeoapifyDto::housenumber)
                .filter(n -> n != null && !n.isBlank()).distinct().count();
        if (portales < 2) return false;
        for (var a : grupo) for (var b : grupo) {
            if (IdentidadLugares.distanciaMetros(a.lat(), a.lon(), b.lat(), b.lon()) >= 120) return true;
        }
        return false;
    }

    private static boolean coordenadasValidas(LugarGeoapifyDto lugar) {
        return lugar.lat() != null && lugar.lon() != null && Double.isFinite(lugar.lat()) && Double.isFinite(lugar.lon())
                && Math.abs(lugar.lat()) <= 90 && Math.abs(lugar.lon()) <= 180;
    }
}
