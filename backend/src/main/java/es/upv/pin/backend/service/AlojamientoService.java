package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.AlojamientoDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;

@Service
public class AlojamientoService {

    private static final Set<String> TIPOS_ADMITIDOS =
            Set.of("amenity", "building", "street");

    private final GeoapifyClient cliente;

    public AlojamientoService(GeoapifyClient cliente) {
        this.cliente = cliente;
    }

    public List<AlojamientoDto> buscar(String texto, String destinoId) {
        if (destinoId == null
                || destinoId.isBlank()
                || destinoId.length() > 2048) {
            throw new IllegalArgumentException(
                    "Selecciona un destino válido para buscar alojamiento.");
        }

        String consulta = texto == null ? "" : texto.strip();

        if (consulta.length() > 250) {
            throw new IllegalArgumentException(
                    "La búsqueda de alojamiento no puede superar los 250 caracteres.");
        }

        if (consulta.length() < 3) {
            return List.of();
        }

        var respuesta = cliente.buscarAlojamientos(
                consulta, destinoId.strip());

        List<AlojamientoDto> alojamientos = new ArrayList<>();
        Set<String> identificadores = new HashSet<>();

        for (var feature : respuesta.features()) {
            if (feature == null || feature.properties() == null) {
                continue;
            }

            LugarGeoapifyDto lugar = feature.properties();

            if (!esValido(lugar)) {
                continue;
            }

            String id = lugar.place_id().strip();

            if (!identificadores.add(id)) {
                continue;
            }

            String direccion = lugar.formatted().strip();

            String nombre = lugar.name() == null || lugar.name().isBlank()
                    ? direccion
                    : lugar.name().strip();

            alojamientos.add(new AlojamientoDto(
                    id,
                    nombre,
                    direccion,
                    lugar.lat(),
                    lugar.lon()));

            if (alojamientos.size() == 5) {
                break;
            }
        }

        return List.copyOf(alojamientos);
    }

    private static boolean esValido(LugarGeoapifyDto lugar) {
        return lugar.place_id() != null
                && !lugar.place_id().isBlank()
                && lugar.formatted() != null
                && !lugar.formatted().isBlank()
                && lugar.result_type() != null
                && TIPOS_ADMITIDOS.contains(lugar.result_type())
                && lugar.lat() != null
                && lugar.lon() != null
                && Double.isFinite(lugar.lat())
                && Double.isFinite(lugar.lon())
                && lugar.lat() >= -90
                && lugar.lat() <= 90
                && lugar.lon() >= -180
                && lugar.lon() <= 180;
    }
}