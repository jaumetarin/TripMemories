package es.upv.pin.backend.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;
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

    public RespuestaLugaresGeoapifyDto buscarLugares(
            String destinoId,
            String categorias,
            int limite) {

        if (destinoId == null || destinoId.isBlank()) {
            throw new IllegalArgumentException(
                    "Es necesario indicar el identificador del destino.");
        }

        if (categorias == null || categorias.isBlank()) {
            throw new IllegalArgumentException(
                    "Es necesario indicar las categorías de lugares.");
        }

        if (limite < 1 || limite > 500) {
            throw new IllegalArgumentException(
                    "El límite de lugares debe estar entre 1 y 500.");
        }

        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "La clave de Geoapify no está configurada.");
        }

        String ruta = "/v2/places"
                + "?categories={categorias}"
                + "&filter=place:{destinoId}"
                + "&limit={limite}"
                + "&lang=es"
                + "&apiKey={clave}";

        RespuestaLugaresGeoapifyDto respuesta;

        try {
            respuesta = restClient.get()
                    .uri(ruta, categorias, destinoId, limite, apiKey)
                    .retrieve()
                    .body(RespuestaLugaresGeoapifyDto.class);

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "No se ha podido consultar el servicio de lugares.");
        }

        if (respuesta == null || respuesta.features() == null) {
            throw new IllegalStateException(
                    "El servicio de lugares ha devuelto una respuesta no válida.");
        }

        return respuesta;
    }
}
