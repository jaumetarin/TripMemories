package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.dto.RespuestaMatrizOrsDto;
import es.upv.pin.backend.model.EstadoDesplazamiento;

@Service
public class DesplazamientoService {

    private static final int MAXIMO_PUNTOS = 50;
    private static final String FUENTE = "openrouteservice";

    private final OpenRouteServiceClient cliente;

    public DesplazamientoService(OpenRouteServiceClient cliente) {
        this.cliente = cliente;
    }

    public List<DesplazamientoDto> calcular(List<PuntoRutaDto> puntos) {
        validarPuntos(puntos);

        List<PuntoRutaDto> puntosConCoordenadas = puntos.stream()
                .filter(punto -> punto.latitud() != null
                        && punto.longitud() != null)
                .toList();

        Map<String, Integer> indices = new HashMap<>();

        for (int i = 0; i < puntosConCoordenadas.size(); i++) {
            indices.put(puntosConCoordenadas.get(i).id(), i);
        }

        RespuestaMatrizOrsDto matriz = null;

        if (puntosConCoordenadas.size() >= 2) {
            matriz = cliente.calcularMatriz(puntosConCoordenadas);
        }

        List<DesplazamientoDto> desplazamientos = new ArrayList<>();

        for (PuntoRutaDto origen : puntos) {
            for (PuntoRutaDto destino : puntos) {
                if (origen.id().equals(destino.id())) {
                    continue;
                }

                Integer fila = indices.get(origen.id());
                Integer columna = indices.get(destino.id());

                if (fila == null || columna == null) {
                    desplazamientos.add(new DesplazamientoDto(
                            origen.id(),
                            destino.id(),
                            null,
                            null,
                            EstadoDesplazamiento.COORDENADAS_AUSENTES,
                            null));

                    continue;
                }

                Double distancia = matriz.distances()
                        .get(fila).get(columna);

                Double duracion = matriz.durations()
                        .get(fila).get(columna);

                if (distancia == null || duracion == null) {
                    desplazamientos.add(new DesplazamientoDto(
                            origen.id(),
                            destino.id(),
                            null,
                            null,
                            EstadoDesplazamiento.RUTA_NO_DISPONIBLE,
                            FUENTE));
                } else {
                    desplazamientos.add(new DesplazamientoDto(
                            origen.id(),
                            destino.id(),
                            distancia,
                            duracion,
                            EstadoDesplazamiento.DISPONIBLE,
                            FUENTE));
                }
            }
        }

        return List.copyOf(desplazamientos);
    }

    private static void validarPuntos(List<PuntoRutaDto> puntos) {
        if (puntos == null || puntos.size() > MAXIMO_PUNTOS) {
            throw new IllegalArgumentException(
                    "Debes indicar una lista de hasta 50 puntos.");
        }

        Set<String> identificadores = new HashSet<>();

        for (PuntoRutaDto punto : puntos) {
            if (punto == null
                    || punto.id() == null
                    || punto.id().isBlank()) {
                throw new IllegalArgumentException(
                        "Todos los puntos deben tener un identificador.");
            }

            if (!identificadores.add(punto.id())) {
                throw new IllegalArgumentException(
                        "Los identificadores de los puntos no pueden repetirse.");
            }

            if (!coordenadaValida(punto.latitud(), 90)
                    || !coordenadaValida(punto.longitud(), 180)) {
                throw new IllegalArgumentException(
                        "Las coordenadas indicadas no son válidas.");
            }
        }
    }

    private static boolean coordenadaValida(
            Double coordenada, double limite) {

        return coordenada == null
                || (Double.isFinite(coordenada)
                        && coordenada >= -limite
                        && coordenada <= limite);
    }
}