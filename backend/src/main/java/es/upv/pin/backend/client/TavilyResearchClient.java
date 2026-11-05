package es.upv.pin.backend.client;

import es.upv.pin.backend.dto.HallazgoGemini;
import es.upv.pin.backend.dto.LugarDto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/** Extracción conservadora: únicamente frases textuales de resultados identificados. */
@Component
public class TavilyResearchClient implements ResearchProvider {
    private static final URI ENDPOINT = URI.create("https://api.tavily.com/search");
    private static final Pattern PRECIO =
            Pattern.compile(
                    "(?iu)(?:entrada|admission|ticket|precio|price|tarifa)[^.!?\\n"
                        + "]{0,55}?(?:€\\s*(\\d+(?:[.,]\\d{1,2})?)|(\\d+(?:[.,]\\d{1,2})?)\\s*(€|EUR|USD|GBP))");
    private static final Pattern HORA =
            Pattern.compile(
                    "(?iu)(?:horario|horarios|abre|abierto|opening hours|open)[^.!?\\n"
                        + "]{0,130}\\b\\d{1,2}[:.]\\d{2}\\s*(?:-|–|a|to)\\s*\\d{1,2}[:.]\\d{2}\\b");
    private static final Pattern GRATIS =
            Pattern.compile(
                    "(?iu)\\b(?:entrada gratuita|entrada gratis|admission is free|free"
                        + " admission)\\b");
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
    private final String key;

    @org.springframework.beans.factory.annotation.Autowired
    public TavilyResearchClient(@Value("${tavily.api-key:}") String key) {
        this(key, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    TavilyResearchClient(String key, HttpClient http) {
        this.key = key;
        this.http = http;
    }

    @Override
    public boolean disponible() {
        return key != null && !key.isBlank();
    }

    @Override
    public Map<String, HallazgoGemini> investigar(List<LugarDto> lugares, String destino) {
        if (!disponible()) return Map.of();
        if (lugares == null || destino == null || destino.isBlank())
            throw new IllegalArgumentException("Faltan lugares o destino.");
        Map<String, HallazgoGemini> resultado = new LinkedHashMap<>();
        for (LugarDto lugar : lugares) {
            if (lugar == null
                    || lugar.id() == null
                    || lugar.nombre() == null
                    || lugar.nombre().isBlank()) continue;
            String query =
                    lugar.nombre()
                            + " "
                            + destino
                            + " "
                            + (lugar.direccion() == null ? "" : lugar.direccion())
                            + " entrada precio horario sitio oficial";
            JsonNode respuesta = consultar(query);
            for (JsonNode fuente : respuesta.path("results")) {
                String url = fuente.path("url").asText();
                String texto = fuente.path("content").asText();
                String identidad =
                        (fuente.path("title").asText() + " " + texto).toLowerCase(Locale.ROOT);
                if (!urlValida(url)
                        || !identidad.contains(lugar.nombre().toLowerCase(Locale.ROOT))
                        || !identidad.contains(destino.toLowerCase(Locale.ROOT))) continue;
                Matcher precio = PRECIO.matcher(texto), horario = HORA.matcher(texto);
                Double cantidad = null;
                String moneda = null,
                        horarioTexto = null,
                        fuentePrecio = null,
                        fuenteHorario = null;
                List<String> hechos = new ArrayList<>();
                if (precio.find()) {
                    String importe = precio.group(1) != null ? precio.group(1) : precio.group(2);
                    String extracto = precio.group();
                    // Evita rangos, precios con condiciones y tarifas específicas.
                    String contexto =
                            texto.substring(
                                    Math.max(0, precio.start() - 60),
                                    Math.min(texto.length(), precio.end() + 60));
                    String contextoSinHoras =
                            contexto.replaceAll(
                                    "\\d{1,2}[:.]\\d{2}\\s*[-–]\\s*\\d{1,2}[:.]\\d{2}", "");
                    if (!Pattern.compile(
                                    "(?iu)\\b(desde|from|niñ[oa]s?|children|reducid[oa]|reduced|hasta|to|gratis|free)\\b|\\d\\s*[-–]\\s*\\d")
                            .matcher(contextoSinHoras)
                            .find()) {
                        cantidad = Double.valueOf(importe.replace(',', '.'));
                        moneda =
                                precio.group(3) == null || "€".equals(precio.group(3))
                                        ? "EUR"
                                        : precio.group(3).toUpperCase(Locale.ROOT);
                        fuentePrecio = url;
                        hechos.add(extracto);
                    }
                }
                if (cantidad == null) {
                    Matcher gratis = GRATIS.matcher(texto);
                    if (gratis.find()) {
                        String contexto =
                                texto.substring(
                                        Math.max(0, gratis.start() - 100),
                                        Math.min(texto.length(), gratis.end() + 100));
                        if (!Pattern.compile(
                                        "(?iu)\\b(donaci[oó]n|donativo|donation|donations|solo|s[oó]lo|only|menores|niñ[oa]s?|children|domingos?|sundays?|excepto|except|condiciones|conditions|ciertos|certain|d[ií]as|days)\\b")
                                .matcher(contexto)
                                .find()) {
                            cantidad = 0.0;
                            moneda = "EUR";
                            fuentePrecio = url;
                            hechos.add(gratis.group());
                        }
                    }
                }
                if (horario.find()) {
                    horarioTexto = horario.group().trim();
                    fuenteHorario = url;
                    hechos.add(horarioTexto);
                }
                if (!hechos.isEmpty()) {
                    resultado.put(
                            lugar.id(),
                            new HallazgoGemini(
                                    String.join(". ", hechos),
                                    List.of(url),
                                    cantidad,
                                    cantidad,
                                    moneda,
                                    horarioTexto,
                                    null,
                                    fuentePrecio,
                                    fuenteHorario,
                                    null));
                    break;
                }
            }
        }
        return resultado;
    }

    private boolean urlValida(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private JsonNode consultar(String query) {
        try {
            String cuerpo =
                    json.writeValueAsString(
                            Map.of("query", query, "search_depth", "basic", "max_results", 5));
            HttpRequest req =
                    HttpRequest.newBuilder(ENDPOINT)
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + key)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                            .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300)
                throw new IllegalStateException();
            JsonNode respuesta = json.readTree(res.body());
            if (!respuesta.path("results").isArray()) throw new IllegalStateException();
            return respuesta;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No se ha podido consultar Tavily.");
        } catch (Exception e) {
            throw new IllegalStateException("No se ha podido consultar Tavily.");
        }
    }
}
