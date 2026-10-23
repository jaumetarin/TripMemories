package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.List;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.DestinoGeoapifyDto;

import org.springframework.stereotype.Service;

@Service
public class DestinoService {

    private final GeoapifyClient geoapifyClient;

    public DestinoService(GeoapifyClient geoapifyClient) {
        this.geoapifyClient = geoapifyClient;
    }

    public List<DestinoDto> buscar(String texto) {
        List<DestinoDto> destinos = new ArrayList<>();

        if (texto == null) {
            return destinos;
        }

        String consulta = texto.strip();

        if (consulta.length() > 120) {
            throw new IllegalArgumentException(
                    "La búsqueda no puede superar los 120 caracteres.");
        }

        if (consulta.length() < 3) {
            return destinos;
        }

        List<DestinoGeoapifyDto> lugares =
                geoapifyClient.autocompletar(consulta);

        
        List<String> identificadoresIncluidos = new ArrayList<>();

        for (DestinoGeoapifyDto lugar : lugares) {
            if (!esValido(lugar)) {
                continue;
            }

            String nombre = obtenerNombre(lugar);

            if (nombre.isBlank()) {
                continue;
            }

            if (identificadoresIncluidos.contains(lugar.place_id())) {
                continue;
            }

            String region = lugar.state();

            if (region == null) {
                region = "";
            }

            String pais = lugar.country();

            if (pais == null) {
                pais = "";
            }

            DestinoDto destino = new DestinoDto(
                    lugar.place_id(),
                    nombre,
                    region,
                    pais,
                    lugar.lat(),
                    lugar.lon()
            );

            destinos.add(destino);
            identificadoresIncluidos.add(lugar.place_id());

            if (destinos.size() == 5) {
                break;
            }
        }

        return destinos;
    }

    private boolean esValido(DestinoGeoapifyDto lugar) {
        if (lugar == null) {
            return false;
        }

        if (lugar.place_id() == null || lugar.place_id().isBlank()) {
            return false;
        }

        if (lugar.lat() == null || lugar.lon() == null) {
            return false;
        }

        if (!Double.isFinite(lugar.lat()) || !Double.isFinite(lugar.lon())) {
            return false;
        }

        return Math.abs(lugar.lat()) <= 90 && Math.abs(lugar.lon()) <= 180;
    }

    private String obtenerNombre(DestinoGeoapifyDto lugar) {
        String nombre = lugar.city();

        if (nombre != null && !nombre.isBlank()) {
            return nombre.strip();
        }

        nombre = lugar.name();

        if (nombre != null && !nombre.isBlank()) {
            return nombre.strip();
        }

        nombre = lugar.address_line1();

        if (nombre != null && !nombre.isBlank()) {
            return nombre.strip();
        }

        return "";
    }
}
