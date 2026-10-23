package es.upv.pin.backend.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import es.upv.pin.backend.dto.DestinoGeoapifyDto;
import es.upv.pin.backend.dto.RespuestaDestinosGeoapifyDto;

@Component
public class GeoapifyClient {

    private final RestClient restClient;
    private final String apiKey;

    // Recibe y guarda las dependencias.
    public GeoapifyClient(
            RestClient restClient,
            @Value("${geoapify.api-key}") String apiKey) {

        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public List<DestinoGeoapifyDto> autocompletar(String texto) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "La clave de Geoapify no está configurada.");
        }

        String ruta = "/v1/geocode/autocomplete"
                + "?text={texto}&type=city"
                + "&lang=es&limit=5&format=json"
                + "&apiKey={clave}";

        RespuestaDestinosGeoapifyDto respuesta;

        try {
            respuesta = restClient.get()
                    .uri(ruta, texto, apiKey)
                    .retrieve()
                    .body(RespuestaDestinosGeoapifyDto.class);

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "No se ha podido consultar el servicio de destinos.");
        }

        if (respuesta == null || respuesta.getResults() == null) {
            throw new IllegalStateException(
                    "El servicio de destinos ha devuelto una respuesta no válida.");
        }

        return respuesta.getResults();
    }
}
