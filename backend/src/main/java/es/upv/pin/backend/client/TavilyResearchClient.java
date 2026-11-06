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
import java.text.Normalizer;
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
    // Captura también AM/PM para no perderlo antes de validar el horario.
    private static final Pattern HORA =
            Pattern.compile(
                    "(?iu)(?:horario|horarios|abre|abierto|opening hours|open)[^.!?]{0,100}?"
                        + "(?:[01]?\\d|2[0-3])[:.][0-5]\\d"
                        + "(?:\\s*(?:AM|PM)\\b)?"
                        + "\\s*(?:-|–|a|to)\\s*"
                        + "(?:[01]?\\d|2[0-3])[:.][0-5]\\d"
                        + "(?:\\s*(?:AM|PM)\\b)?\\b");
    private static final Pattern GRATIS =
            Pattern.compile(
                    "(?iu)\\b(?:entrada gratuita|entrada gratis|admission is free|free"
                        + " admission)\\b");
    // Evita interpretar como general una gratuidad negada o condicionada.
    private static final Pattern GRATUIDAD_DUDOSA = Pattern.compile(
            "(?iu)\\b(?:"
                    + "no|not|never|nunca"
                    + "|a\\s+partir|desde|hasta|antes|despu[eé]s"
                    + "|from|after|before|until|starting"
                    + "|para|for|si|if|cuando|when"
                    + "|donaci[oó]n|donativo|donation|donations"
                    + "|solo|s[oó]lo|only|menores|niñ[oa]s?|children"
                    + "|domingos?|sundays?|excepto|except"
                    + "|condiciones|conditions|ciertos|certain|d[ií]as|days"
                    + ")\\b");
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
            Double cantidad = null;
            String moneda = null, horarioTexto = null, fuentePrecio = null, fuenteHorario = null;
            List<String> hechos = new ArrayList<>(), fuentes = new ArrayList<>();
            for (JsonNode fuente : respuesta.path("results")) {
                String url = fuente.path("url").asText();
                String texto = fuente.path("content").asText();
                String titulo = normalizar(fuente.path("title").asText());
                String identidad = normalizar(fuente.path("title").asText() + " " + texto);
                if (!urlValida(url)
                        || !tituloDelLugar(titulo, lugar, destino)
                        || !identidad.contains(normalizar(destino))) continue;
                Matcher precio = PRECIO.matcher(texto), horario = HORA.matcher(texto);
                if (cantidad == null && precio.find()) {
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
                        String contexto = contextoFrase(
                                texto, gratis.start(), gratis.end());

                        // Solo asignamos cero si no detectamos negaciones ni restricciones.
                        if (!GRATUIDAD_DUDOSA.matcher(contexto).find()) {
                            cantidad = 0.0;
                            moneda = "EUR";
                            fuentePrecio = url;
                            hechos.add(gratis.group());
                        }
                    }
                }
                while (horarioTexto == null && horario.find()) {
                    String candidatoHorario = horario.group().replaceAll("\\s+", " ").trim();
                    // No interpretamos aún horarios de 12 horas; evitamos guardar datos ambiguos.
                    if (candidatoHorario.matches("(?iu).*\\d\\s*(?:AM|PM)\\b.*")) continue;
                    horarioTexto = candidatoHorario;
                    fuenteHorario = url;
                    hechos.add(horarioTexto);
                }
                if ((url.equals(fuentePrecio) || url.equals(fuenteHorario)) && !fuentes.contains(url))
                    fuentes.add(url);
                if (cantidad != null && horarioTexto != null) break;
            }
            if (!hechos.isEmpty()) {
                resultado.put(
                        lugar.id(),
                        new HallazgoGemini(
                                String.join(". ", hechos),
                                List.copyOf(fuentes),
                                cantidad,
                                cantidad,
                                moneda,
                                horarioTexto,
                                null,
                                fuentePrecio,
                                fuenteHorario,
                                null));
            }
        }
        return resultado;
    }

    private static String normalizar(String valor) {
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return sinTildes.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll(" +", " ");
    }

    private static boolean tituloDelLugar(String titulo, LugarDto lugar, String destino) {
        String nombre = normalizar(lugar.nombre());
        String alias = lugar.direccion() == null
                ? ""
                : normalizar(lugar.direccion().split(",", 2)[0]);
        if (nombre.length() >= 8 && titulo.contains(nombre)) return true;
        if (alias.length() >= 10
                && !alias.matches("^(calle|carrer|avenida|avinguda|plaza|placa)\\b.*")
                && titulo.contains(alias)) return true;
        return compartenNombre(titulo, nombre, destino)
                || alias.length() >= 10 && compartenNombre(titulo, alias, destino);
    }

    private static boolean compartenNombre(String titulo, String nombre, String destino) {
        Set<String> palabras = new HashSet<>(List.of(nombre.split(" ")));
        palabras.removeAll(Set.of("de", "del", "la", "las", "el", "los", "l", "en", "the"));
        if (palabras.size() < 2) return false;
        Set<String> tituloPalabras = new HashSet<>(List.of(titulo.split(" ")));
        long comunes = palabras.stream().filter(tituloPalabras::contains).count();
        String ciudad = normalizar(destino);
        return comunes >= 2
                && comunes * 3 >= palabras.size() * 2
                && palabras.stream().anyMatch(p -> tituloPalabras.contains(p)
                        && !p.equals(ciudad)
                        && !Set.of("museo", "museu", "parque", "park", "iglesia", "church")
                                .contains(p));
    }

    private static String contextoFrase(String texto, int inicio, int fin) {
        int desde = Math.max(0, inicio - 50), hasta = Math.min(texto.length(), fin + 70);
        for (char separador : new char[] {'.', '!', '?', ';', '\n'}) {
            desde = Math.max(desde, texto.lastIndexOf(separador, inicio - 1) + 1);
            int siguiente = texto.indexOf(separador, fin);
            if (siguiente >= 0) hasta = Math.min(hasta, siguiente);
        }
        return texto.substring(desde, hasta);
    }

    private boolean urlValida(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("tripadvisor.") || host.contains(".tripadvisor.")) return false;
            return !List.of("instagram.com", "facebook.com", "tiktok.com", "youtube.com",
                            "getyourguide.com", "mapquest.com", "yelp.com")
                    .stream().anyMatch(dominio -> host.equals(dominio) || host.endsWith("." + dominio));
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
