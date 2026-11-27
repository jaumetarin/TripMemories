package es.upv.pin.backend.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.dto.RespuestaMatrizOrsDto;
import es.upv.pin.backend.model.LimitesItinerario;
import es.upv.pin.backend.dto.GeometriaTramoDto;
import tools.jackson.databind.JsonNode;

@Component
public class OpenRouteServiceClient {

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public OpenRouteServiceClient(
            @Value("${ors.api-key:}") String apiKey, ControlLlamadasProveedor control) {
        this(crearCliente().mutate().requestInterceptor(control).build(), apiKey);
    }

    OpenRouteServiceClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }
    public GeometriaTramoDto calcularGeometria(PuntoRutaDto origen, PuntoRutaDto destino) {
        if (!coordenadasValidas(origen) || !coordenadasValidas(destino))
            throw new IllegalArgumentException("El trazado necesita dos ubicaciones válidas.");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("La clave de openrouteservice no está configurada.");
        try {
            var respuesta = restClient.post().uri("/v2/directions/foot-walking/geojson")
                    .header("Authorization", apiKey).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("coordinates", List.of(List.of(origen.longitud(), origen.latitud()),
                            List.of(destino.longitud(), destino.latitud())), "instructions", false))
                    .retrieve().body(JsonNode.class);
            if (respuesta == null || !respuesta.path("features").isArray() || respuesta.path("features").size() != 1)
                throw new IllegalStateException("El proveedor no ha devuelto un trazado válido.");
            var geometria = respuesta.path("features").path(0).path("geometry");
            var coordenadas = geometria.path("coordinates");
            if (!"LineString".equals(geometria.path("type").asText()) || !coordenadas.isArray()
                    || coordenadas.size() < 2 || coordenadas.size() > 20000)
                throw new IllegalStateException("El proveedor no ha devuelto un trazado válido.");
            List<List<Double>> puntos = new ArrayList<>();
            for (var punto : coordenadas) {
                if (!punto.isArray() || punto.size() < 2 || !punto.path(0).isNumber() || !punto.path(1).isNumber())
                    throw new IllegalStateException("El proveedor ha devuelto coordenadas inválidas.");
                double lon = punto.path(0).asDouble(), lat = punto.path(1).asDouble();
                if (!Double.isFinite(lon) || !Double.isFinite(lat) || Math.abs(lon) > 180 || Math.abs(lat) > 90)
                    throw new IllegalStateException("El proveedor ha devuelto coordenadas inválidas.");
                puntos.add(List.of(lon, lat));
            }
            return new GeometriaTramoDto(puntos, "openrouteservice");
        } catch (RestClientException e) {
            throw new IllegalStateException("No se ha podido obtener el trazado a pie.");
        }
    }
    public RespuestaMatrizOrsDto calcularMatriz(List<PuntoRutaDto> puntos) {
        if (puntos == null || puntos.size() < 2
                || puntos.size() > LimitesItinerario.MAXIMO_PUNTOS_RUTA) {
            throw new IllegalArgumentException(
                    "Debes indicar entre 2 y " + LimitesItinerario.MAXIMO_PUNTOS_RUTA
                            + " puntos para calcular desplazamientos.");
        }

        List<List<Double>> coordenadas = new ArrayList<>();

        for (PuntoRutaDto punto : puntos) {
            if (!coordenadasValidas(punto)) {
                throw new IllegalArgumentException(
                        "Todos los puntos deben tener coordenadas válidas.");
            }

            coordenadas.add(List.of(punto.longitud(), punto.latitud()));
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "La clave de openrouteservice no está configurada.");
        }

        Map<String, Object> cuerpo = Map.of(
                "locations", coordenadas,
                "metrics", List.of("distance", "duration"),
                "units", "m");

        RespuestaMatrizOrsDto respuesta;

        try {
            respuesta = restClient.post()
                    .uri("/v2/matrix/foot-walking")
                    .header("Authorization", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(RespuestaMatrizOrsDto.class);

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "No se ha podido consultar el servicio de desplazamientos.");
        }

        if (respuesta == null
                || !matrizValida(respuesta.distances(), puntos.size())
                || !matrizValida(respuesta.durations(), puntos.size())) {
            throw new IllegalStateException(
                    "El servicio de desplazamientos ha devuelto una respuesta no válida.");
        }

        return respuesta;
    }
    public RespuestaMatrizOrsDto calcularDesde(
            PuntoRutaDto origen,
            List<PuntoRutaDto> destinos
    ) {
        if (!coordenadasValidas(origen)
                || destinos == null
                || destinos.isEmpty()
                || destinos.size()
                        > LimitesItinerario.MAXIMO_PUNTOS_RUTA - 1) {
            throw new IllegalArgumentException(
                    "Indica un origen y entre 1 y "
                            + (LimitesItinerario.MAXIMO_PUNTOS_RUTA - 1)
                            + " destinos con coordenadas válidas.");
        }

        List<List<Double>> coordenadas = new ArrayList<>();
        coordenadas.add(List.of(origen.longitud(), origen.latitud()));

        List<String> indicesDestinos = new ArrayList<>();

        for (PuntoRutaDto destino : destinos) {
            if (!coordenadasValidas(destino)) {
                throw new IllegalArgumentException(
                        "Todos los destinos deben tener coordenadas válidas.");
            }

            indicesDestinos.add(Integer.toString(coordenadas.size()));
            coordenadas.add(List.of(destino.longitud(), destino.latitud()));
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "La clave de openrouteservice no está configurada.");
        }

        Map<String, Object> cuerpo = Map.of(
                "locations", coordenadas,
                "sources", List.of("0"),
                "destinations", indicesDestinos,
                "metrics", List.of("distance", "duration"),
                "units", "m");

        RespuestaMatrizOrsDto respuesta;

        try {
            respuesta = restClient.post()
                    .uri("/v2/matrix/foot-walking")
                    .header("Authorization", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(RespuestaMatrizOrsDto.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "No se ha podido consultar el servicio de desplazamientos.");
        }

        if (respuesta == null
                || !filaValida(respuesta.distances(), destinos.size())
                || !filaValida(respuesta.durations(), destinos.size())) {
            throw new IllegalStateException(
                    "El servicio de desplazamientos ha devuelto "
                            + "una respuesta no válida.");
        }

        return respuesta;
    }

    private static boolean filaValida(
            List<List<Double>> matriz,
            int numeroDestinos
    ) {
        if (matriz == null
                || matriz.size() != 1
                || matriz.getFirst() == null
                || matriz.getFirst().size() != numeroDestinos) {
            return false;
        }

        for (Double valor : matriz.getFirst()) {
            if (valor != null
                    && (!Double.isFinite(valor) || valor < 0)) {
                return false;
            }
        }

        return true;
    }

    private static boolean matrizValida(
            List<List<Double>> matriz, int numeroPuntos) {

        if (matriz == null || matriz.size() != numeroPuntos) {
            return false;
        }

        for (List<Double> fila : matriz) {
            if (fila == null || fila.size() != numeroPuntos) {
                return false;
            }

            for (Double valor : fila) {
                if (valor != null
                        && (!Double.isFinite(valor) || valor < 0)) {
                    return false;
                }
            }
        }

        return true;
    }
    private static boolean coordenadasValidas(PuntoRutaDto punto) {
        return punto != null
                && punto.latitud() != null
                && punto.longitud() != null
                && Double.isFinite(punto.latitud())
                && Double.isFinite(punto.longitud())
                && punto.latitud() >= -90
                && punto.latitud() <= 90
                && punto.longitud() >= -180
                && punto.longitud() <= 180;
    }
    private static RestClient crearCliente() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory fabrica =
                new JdkClientHttpRequestFactory(httpClient);

        fabrica.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder()
                .baseUrl("https://api.heigit.org/openrouteservice")
                .requestFactory(fabrica)
                .build();
    }
}
