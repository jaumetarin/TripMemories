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
    private final CacheGeoapify cache = new CacheGeoapify();
    private final LimiteConsultasGeoapify consultas;

    // Construcción explícita de pruebas: mantiene el máximo simultáneo, sin pausas artificiales.
    public GeoapifyClient(
            RestClient restClient,
            String apiKey) {
        this(restClient, apiKey, new LimiteConsultasGeoapify(3, 0));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GeoapifyClient(RestClient restClient, @Value("${geoapify.api-key}") String apiKey,
            LimiteConsultasGeoapify consultas) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.consultas = consultas;
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
            respuesta = consultas.ejecutar(() -> restClient.get()
                    .uri(ruta, texto, apiKey)
                    .retrieve()
                    .body(RespuestaDestinosGeoapifyDto.class));

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
            respuesta = consultas.ejecutar(() -> restClient.get()
                    .uri(ruta, texto.strip(), destinoId.strip(), apiKey)
                    .retrieve()
                    .body(RespuestaLugaresGeoapifyDto.class));

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
        return buscarLugares(destinoId, categorias, limite, offset, "");
    }

    public RespuestaLugaresGeoapifyDto buscarLugares(
            String destinoId, String categorias, int limite, int offset, String nombre) {
        return buscarLugares(destinoId, categorias, limite, offset, nombre, "");
    }

    public RespuestaLugaresGeoapifyDto buscarLugares(DestinoDto destino, String categorias,
            int limite, int offset, String nombre) {
        if (destino == null) throw new IllegalArgumentException("Indica el destino.");
        String sesgo = destino.latitud() != null && destino.longitud() != null
                && Double.isFinite(destino.latitud()) && Math.abs(destino.latitud()) <= 90
                && Double.isFinite(destino.longitud()) && Math.abs(destino.longitud()) <= 180
                ? destino.longitud() + "," + destino.latitud() : "";
        // Ordenar grupos históricos específicos por cercanía puede agotar el tiempo de Places.
        // El filtro place sigue acotando la ciudad; el reparto de pantalla ordena los resultados localmente.
        if (categorias != null && java.util.Arrays.stream(categorias.split(","))
                .anyMatch(c -> c.strip().startsWith("tourism.sights."))) sesgo = "";
        return buscarLugares(destino.id(), categorias, limite, offset, nombre, sesgo);
    }

    private RespuestaLugaresGeoapifyDto buscarLugares(String destinoId, String categorias,
            int limite, int offset, String nombre, String sesgo) {

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

        String categoriasOrdenadas = java.util.Arrays.stream(categorias.split(",")).map(String::strip)
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        String nombreBuscado = nombre == null ? "" : nombre.strip();
        return cache.obtener(new CacheGeoapify.Consulta("places", destinoId, categoriasOrdenadas, limite, offset, nombreBuscado, sesgo),
                () -> consultarLugares(destinoId, categorias, limite, offset, nombreBuscado, sesgo));
    }

    private RespuestaLugaresGeoapifyDto consultarLugares(String destinoId, String categorias, int limite,
            int offset, String nombre, String sesgo) {

        String ruta = "/v2/places"
                + "?categories={categorias}"
                + "&filter=place:{destinoId}"
                + "&limit={limite}"
                + "&lang=es"
                + (sesgo.isEmpty() ? "" : "&bias=proximity:" + sesgo)
                + "&apiKey={clave}"
                + (offset == 0 ? "" : "&offset=" + offset)
                + (nombre == null || nombre.isBlank() ? "" : "&name={nombre}");

        RespuestaLugaresGeoapifyDto respuesta = null;
        // Solo repetimos una vez una lectura que haya agotado su tiempo de espera.
        // No repetimos errores HTTP (clave, cuota, parámetros...) ni respuestas no válidas.
        for (int intento = 0; intento < 2; intento++) {
            try {
                respuesta = consultas.ejecutar(() -> restClient.get()
                        .uri(ruta, categorias, destinoId, limite, apiKey, nombre == null ? "" : nombre)
                        .retrieve().body(RespuestaLugaresGeoapifyDto.class));
                LOG.info("Places: consulta externa completada (categorías {}, offset {})", categorias, offset);
                break;
            } catch (RestClientException exception) {
                boolean tiempoAgotado = tiempoAgotado(exception);
                String detalle = exception instanceof org.springframework.web.client.RestClientResponseException respuestaError
                        ? "HTTP " + respuestaError.getStatusCode().value()
                        : tiempoAgotado ? "tiempo de espera agotado" : "fallo de conexión o respuesta";
                // No registrar la excepción ni la URL: pueden contener la API key.
                LOG.warn("No se pudo consultar Places: {} (categorías {}, offset {}, intento {})",
                        detalle, categorias, offset, intento + 1);
                if (tiempoAgotado && intento == 0 && !Thread.currentThread().isInterrupted()) continue;
                throw new IllegalStateException(tiempoAgotado
                        ? "Geoapify está tardando demasiado en responder. No se han podido cargar los lugares; vuelve a intentarlo en unos instantes."
                        : "No se ha podido consultar el servicio de lugares.");
            }
        }

        if (respuesta == null || respuesta.features() == null) {
            throw new IllegalStateException(
                    "El servicio de lugares ha devuelto una respuesta no válida.");
        }

        return respuesta;
    }

    private static boolean tiempoAgotado(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof java.net.http.HttpTimeoutException
                    || causa instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }

    public LugarGeoapifyDto buscarCalle(String nombre, DestinoDto destino) {
        if (nombre == null || nombre.isBlank() || destino == null || destino.id() == null)
            throw new IllegalArgumentException("Indica la calle y el destino.");
        if (apiKey.isBlank()) throw new IllegalStateException("La clave de Geoapify no está configurada.");
        try {
            String texto = nombre + ", " + destino.nombre();
            var respuesta = cache.obtener(new CacheGeoapify.Consulta("calle", destino.id(), "", 1, 0, texto),
                    () -> consultas.ejecutar(() -> restClient.get().uri("/v1/geocode/search?text={texto}&type=street"
                            + "&filter=place:{destino}&format=geojson&lang=es&limit=1&apiKey={clave}",
                            texto, destino.id(), apiKey)
                    .retrieve().body(RespuestaLugaresGeoapifyDto.class)));
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
