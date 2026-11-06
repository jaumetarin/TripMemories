package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.LugarGeoapifyDto;
import es.upv.pin.backend.dto.OpcionComidaDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.dto.RecomendacionComidaDto;
import es.upv.pin.backend.model.CategoriaTuristica;

@Service
public class RecomendacionComidaService {
    private static final int LIMITE = 10;
    private static final Set<String> NOMBRES_DESCONOCIDOS = Set.of("desconocido", "desconocida",
            "nombre desconocido", "sin nombre", "unknown", "unnamed", "no name", "n/a", "null", "undefined");
    private final GeoapifyClient cliente;
    private final ClasificacionLugaresService clasificacion;

    public RecomendacionComidaService(GeoapifyClient cliente, ClasificacionLugaresService clasificacion) {
        this.cliente = cliente;
        this.clasificacion = clasificacion;
    }

    public RecomendacionComidaDto buscar(String destinoId, PreferenciasDto preferencias, Set<String> idsExcluidos) {
        if (preferencias == null || preferencias.intereses() == null
                || !preferencias.intereses().contains(CategoriaTuristica.GASTRONOMIA)
                || (preferencias.categoriasExcluidas() != null
                    && preferencias.categoriasExcluidas().contains(CategoriaTuristica.GASTRONOMIA))) {
            return new RecomendacionComidaDto(List.of(), List.of());
        }
        try {
            var respuesta = cliente.buscarLugares(destinoId, "catering.restaurant", 50);
            List<OpcionComidaDto> opciones = new ArrayList<>();
            Set<String> vistos = new HashSet<>();
            if (idsExcluidos != null) idsExcluidos.stream().filter(id -> id != null).map(String::strip).forEach(vistos::add);
            if (respuesta != null && respuesta.features() != null) {
                // Una copia excluida invalida el lugar aunque otra copia omita sus etiquetas.
                for (var feature : respuesta.features()) {
                    if (feature != null && feature.properties() != null) {
                        var lugar = feature.properties();
                        String id = texto(lugar.place_id());
                        if (id != null && excluido(lugar, preferencias)) vistos.add(id);
                    }
                }
                for (var feature : respuesta.features()) {
                    if (feature == null || feature.properties() == null) continue;
                    LugarGeoapifyDto lugar = feature.properties();
                    String id = texto(lugar.place_id());
                    String nombre = texto(lugar.name());
                    if (id == null || nombre == null || NOMBRES_DESCONOCIDOS.contains(nombre.toLowerCase(Locale.ROOT))
                            || !coordenadasValidas(lugar) || vistos.contains(id) || excluido(lugar, preferencias)) continue;
                    vistos.add(id);
                    String horario = texto(lugar.opening_hours());
                    if (horario == null && lugar.datasource() != null && lugar.datasource().raw() != null)
                        horario = texto(lugar.datasource().raw().opening_hours());
                    opciones.add(new OpcionComidaDto(id, nombre, lugar.lat(), lugar.lon(), texto(lugar.formatted()),
                            horario, horario == null ? null : "Geoapify"));
                    if (opciones.size() == LIMITE) break;
                }
            }
            if (opciones.isEmpty()) return sinOpciones();
            return new RecomendacionComidaDto(List.copyOf(opciones), List.of());
        } catch (RuntimeException ex) {
            return sinOpciones();
        }
    }

    private boolean excluido(LugarGeoapifyDto lugar, PreferenciasDto preferencias) {
        return (preferencias.categoriasExcluidas() != null && clasificacion.categorias(lugar).stream()
                    .anyMatch(preferencias.categoriasExcluidas()::contains))
                || (preferencias.subcategoriasExcluidas() != null && clasificacion.subcategorias(lugar).stream()
                    .anyMatch(preferencias.subcategoriasExcluidas()::contains));
    }

    private static boolean coordenadasValidas(LugarGeoapifyDto lugar) {
        return lugar.lat() != null && lugar.lon() != null && Double.isFinite(lugar.lat())
                && Double.isFinite(lugar.lon()) && Math.abs(lugar.lat()) <= 90 && Math.abs(lugar.lon()) <= 180;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static RecomendacionComidaDto sinOpciones() {
        return new RecomendacionComidaDto(List.of(),
                List.of("No se han podido obtener restaurantes; se propone una comida genérica."));
    }
}
