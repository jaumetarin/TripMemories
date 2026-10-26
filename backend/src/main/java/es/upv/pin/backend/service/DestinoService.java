package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.DestinoGeoapifyDto;

import org.springframework.stereotype.Service;

@Service
public class DestinoService {

    private static final double RADIO_DUPLICADOS_KM = 2;

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

        for (DestinoGeoapifyDto lugar : lugares) {
            if (!esValido(lugar)) {
                continue;
            }

            String nombre = obtenerNombre(lugar);

            if (nombre.isBlank()) {
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

            if (esDuplicado(destino, destinos)) {
                continue;
            }

            destinos.add(destino);

            if (destinos.size() == 5) {
                break;
            }
        }

        return destinos;
    }

    private boolean esDuplicado(DestinoDto candidato, List<DestinoDto> incluidos) {
        for (DestinoDto incluido : incluidos) {
            if (candidato.id().equals(incluido.id())) {
                return true;
            }

            // Un mismo lugar puede tener varios identificadores en el proveedor.
            // La cercanía evita eliminar ciudades homónimas que estén lejos.
            boolean mismoNombre = normalizar(candidato.nombre()).equals(normalizar(incluido.nombre()));
            boolean mismaRegion = normalizar(candidato.region()).equals(normalizar(incluido.region()));
            boolean mismoPais = normalizar(candidato.pais()).equals(normalizar(incluido.pais()));

            if (mismoNombre && mismaRegion && mismoPais
                    && distanciaKm(candidato, incluido) <= RADIO_DUPLICADOS_KM) {
                return true;
            }
        }
        return false;
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?U)\\s+", " ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    // Fórmula de Haversine: distancia en línea recta entre dos coordenadas.
    private double distanciaKm(DestinoDto primero, DestinoDto segundo) {
        double diferenciaLatitud = Math.toRadians(segundo.latitud() - primero.latitud());
        double diferenciaLongitud = Math.toRadians(segundo.longitud() - primero.longitud());
        double a = Math.pow(Math.sin(diferenciaLatitud / 2), 2)
                + Math.cos(Math.toRadians(primero.latitud()))
                * Math.cos(Math.toRadians(segundo.latitud()))
                * Math.pow(Math.sin(diferenciaLongitud / 2), 2);

        return 6371 * 2 * Math.asin(Math.sqrt(Math.clamp(a, 0, 1)));
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
