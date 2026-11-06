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

@Component
public class OpenRouteServiceClient {

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public OpenRouteServiceClient(
            @Value("${ors.api-key:}") String apiKey) {
        this(crearCliente(), apiKey);
    }

    OpenRouteServiceClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }
    public RespuestaMatrizOrsDto calcularMatriz(List<PuntoRutaDto> puntos) {
        if (puntos == null || puntos.size() < 2 || puntos.size() > 50) {
            throw new IllegalArgumentException(
                    "Debes indicar entre 2 y 50 puntos para calcular desplazamientos.");
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
