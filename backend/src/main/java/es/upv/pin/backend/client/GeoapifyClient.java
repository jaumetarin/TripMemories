package es.upv.pin.backend.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;
import es.upv.pin.backend.dto.DestinoGeoapifyDto;
import es.upv.pin.backend.dto.RespuestaDestinosGeoapifyDto;
import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;

@Component
public class GeoapifyClient {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GeoapifyClient.class);

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
    public RespuestaLugaresGeoapifyDto buscarAlojamientos(
            String texto, String destinoId) {

        if (texto == null
                || texto.strip().length() < 3
                || texto.strip().length() > 250) {
            throw new IllegalArgumentException(
                    "Escribe entre 3 y 250 caracteres para buscar alojamiento.");
        }

        if (destinoId == null
                || destinoId.isBlank()
                || destinoId.length() > 2048) {
            throw new IllegalArgumentException(
                    "Selecciona un destino válido para buscar alojamiento.");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "La clave de Geoapify no está configurada.");
        }

        String ruta = "/v1/geocode/autocomplete"
                + "?text={texto}"
                + "&filter=place:{destinoId}"
                + "&lang=es&limit=5&format=geojson"
                + "&apiKey={clave}";

        RespuestaLugaresGeoapifyDto respuesta;

        try {
            respuesta = restClient.get()
                    .uri(ruta, texto.strip(), destinoId.strip(), apiKey)
                    .retrieve()
                    .body(RespuestaLugaresGeoapifyDto.class);

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "No se ha podido consultar el servicio de alojamientos.");
        }

        if (respuesta == null || respuesta.features() == null) {
            throw new IllegalStateException(
                    "El servicio de alojamientos ha devuelto una respuesta no válida.");
        }

        return respuesta;
    }
    public RespuestaLugaresGeoapifyDto buscarLugares(
            String destinoId,
            String categorias,
            int limite) {
        return buscarLugares(destinoId, categorias, limite, 0);
    }

    public RespuestaLugaresGeoapifyDto buscarLugares(
            String destinoId, String categorias, int limite, int offset) {

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
        if (offset < 0) throw new IllegalArgumentException("El desplazamiento de página no puede ser negativo.");

        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "La clave de Geoapify no está configurada.");
        }

        String ruta = "/v2/places"
                + "?categories={categorias}"
                + "&filter=place:{destinoId}"
                + "&limit={limite}"
                + "&lang=es"
                + "&apiKey={clave}"
                + (offset == 0 ? "" : "&offset=" + offset);

        RespuestaLugaresGeoapifyDto respuesta;

        try {
            respuesta = restClient.get()
                    .uri(ruta, categorias, destinoId, limite, apiKey)
                    .retrieve()
                    .body(RespuestaLugaresGeoapifyDto.class);

        } catch (RestClientException exception) {
            String detalle = exception instanceof org.springframework.web.client.RestClientResponseException respuestaError
                    ? "HTTP " + respuestaError.getStatusCode().value()
                    : exception.getCause() == null ? exception.getClass().getSimpleName()
                    : exception.getCause().getClass().getSimpleName();
            LOG.warn("No se pudo consultar Places: {} (offset {})", detalle, offset);
            throw new IllegalStateException(
                    "No se ha podido consultar el servicio de lugares.");
        }

        if (respuesta == null || respuesta.features() == null) {
            throw new IllegalStateException(
                    "El servicio de lugares ha devuelto una respuesta no válida.");
        }

        return respuesta;
    }

    public LugarGeoapifyDto buscarCalle(String nombre, DestinoDto destino) {
        if (nombre == null || nombre.isBlank() || destino == null || destino.id() == null)
            throw new IllegalArgumentException("Indica la calle y el destino.");
        if (apiKey.isBlank()) throw new IllegalStateException("La clave de Geoapify no está configurada.");
        try {
            var respuesta = restClient.get().uri("/v1/geocode/search?text={texto}&type=street"
                            + "&filter=place:{destino}&format=geojson&lang=es&limit=1&apiKey={clave}",
                            nombre + ", " + destino.nombre(), destino.id(), apiKey)
                    .retrieve().body(RespuestaLugaresGeoapifyDto.class);
            if (respuesta == null || respuesta.features() == null)
                throw new IllegalStateException("El servicio de calles ha devuelto una respuesta no válida.");
            if (respuesta.features().isEmpty()) return null;
            var feature = respuesta.features().getFirst();
            return feature == null ? null : feature.properties();
        } catch (RestClientException e) {
            // No propagamos una URL que pueda contener la clave.
            throw new IllegalStateException("No se ha podido localizar la calle comercial.");
        }
    }
}
