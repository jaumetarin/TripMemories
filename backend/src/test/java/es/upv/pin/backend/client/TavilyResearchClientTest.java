package es.upv.pin.backend.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.LugarDto;

import org.junit.jupiter.api.Test;

import java.net.http.*;
import java.util.List;

class TavilyResearchClientTest {
    private final LugarDto lugar =
            new LugarDto(
                    "secret-id", "Museo Azul", "Calle 1", null, null, List.of(), List.of(), null);

    @SuppressWarnings("unchecked")
    private TavilyResearchClient cliente(String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> res = mock(HttpResponse.class);
        when(res.statusCode()).thenReturn(200);
        when(res.body()).thenReturn(body);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(res);
        return new TavilyResearchClient("test", http);
    }

    @Test
    void claveAusenteDeshabilitaInvestigacion() {
        var cliente = new TavilyResearchClient("", mock(HttpClient.class));
        assertFalse(cliente.disponible());
        assertTrue(cliente.investigar(List.of(lugar), "Valencia").isEmpty());
    }

    @Test
    void extraeDescripcionLiteralConFuenteSinInventarPrecioNiHorario() throws Exception {
        String descripcion = "El Museo Azul de Valencia alberga una colección de cerámica y pintura local.";
        String respuesta = "{\"results\":[{\"title\":\"Museo Azul Valencia\","
                + "\"url\":\"https://museo.example/acerca\",\"content\":\"" + descripcion + "\"}]}";
        var hallazgo = cliente(respuesta).investigar(List.of(lugar), "Valencia").get(lugar.id());

        assertEquals(descripcion, hallazgo.descripcion());
        assertEquals("https://museo.example/acerca", hallazgo.fuenteDescripcion());
        assertEquals(List.of(hallazgo.fuenteDescripcion()), hallazgo.fuentes());
        assertNull(hallazgo.precioMinimo());
        assertNull(hallazgo.horario());
        assertNull(hallazgo.informacion());
    }

    @Test
    void noConvierteDatosDeEntradaEnUnaDescripcion() throws Exception {
        String respuesta = "{\"results\":[{\"title\":\"Museo Azul Valencia\","
                + "\"url\":\"https://museo.example/visitas\","
                + "\"content\":\"Museo Azul Valencia. Entrada general 10 EUR. Horario 09:00 - 18:00.\"}]}";
        var hallazgo = cliente(respuesta).investigar(List.of(lugar), "Valencia").get(lugar.id());

        assertNull(hallazgo.descripcion());
        assertNull(hallazgo.fuenteDescripcion());
        assertEquals(10.0, hallazgo.precioMinimo());
    }

    @Test
    void rechazaDescripcionDeOtroLugarODestino() throws Exception {
        String respuesta = "{\"results\":[{\"title\":\"Museo Rojo Madrid\","
                + "\"url\":\"https://museo.example\","
                + "\"content\":\"El Museo Rojo de Madrid alberga una colección de pintura local.\"}]}";
        assertTrue(cliente(respuesta).investigar(List.of(lugar), "Valencia").isEmpty());
    }

    @Test
    void separaFrasesDescriptivasDeLasTarifasAunqueIncluyanElNombreDelLugar() throws Exception {
        String respuesta = "{\"results\":[{\"title\":\"Museo Azul Valencia\","
                + "\"url\":\"https://museo.example/visitas\","
                + "\"content\":\"El Museo Azul de Valencia ofrece entrada general 10 EUR.\"}]}";
        var hallazgo = cliente(respuesta).investigar(List.of(lugar), "Valencia").get(lugar.id());
        assertNull(hallazgo.descripcion());
        assertEquals(10.0, hallazgo.precioMinimo());
    }

    @Test
    void limitaElExtractoSinGenerarUnTextoNuevo() throws Exception {
        String texto = "El Museo Azul de Valencia alberga " + "una extensa colección de arte local ".repeat(30);
        String respuesta = "{\"results\":[{\"title\":\"Museo Azul Valencia\","
                + "\"url\":\"https://museo.example/acerca\",\"content\":\"" + texto + "\"}]}";
        var hallazgo = cliente(respuesta).investigar(List.of(lugar), "Valencia").get(lugar.id());
        assertTrue(hallazgo.descripcion().length() <= 600);
        assertTrue(hallazgo.descripcion().endsWith("…"));
        assertTrue(texto.startsWith(hallazgo.descripcion().substring(0, hallazgo.descripcion().length() - 1)));
    }

    @Test
    void aceptaSoloHechosDeFuenteQueIdentificaLugarYDestino() throws Exception {
        String respuesta =
                "{\"results\":[{\"title\":\"Museo Azul"
                    + " Valencia\",\"url\":\"https://example.org/museo\",\"content\":\"Museo Azul"
                    + " Valencia. Entrada general 10 EUR. Horario 09:00 - 18:00.\"}]}";
        var hallazgo = cliente(respuesta).investigar(List.of(lugar), "Valencia").get(lugar.id());
        assertEquals(10.0, hallazgo.precioMinimo());
        assertEquals("https://example.org/museo", hallazgo.fuentePrecio());
        assertEquals("https://example.org/museo", hallazgo.fuenteHorario());
        assertTrue(
                cliente(respuesta.replace("Valencia", "Madrid"))
                        .investigar(List.of(lugar), "Valencia")
                        .isEmpty());
    }

    @Test
    void noInterpretaTarifasReducidasComoPrecioGeneral() throws Exception {
        String respuesta =
                "{\"results\":[{\"title\":\"Museo Azul"
                    + " Valencia\",\"url\":\"https://example.org\",\"content\":\"Entrada reducida 5"
                    + " EUR para niños.\"}]}";
        assertTrue(cliente(respuesta).investigar(List.of(lugar), "Valencia").isEmpty());
    }

    @Test
    void aceptaEntradaGratuitaSinCondicionesPeroRechazaDonativos() throws Exception {
        String respuesta =
                "{\"results\":[{\"title\":\"Museo Azul"
                    + " Valencia\",\"url\":\"https://example.org\",\"content\":\"Entrada"
                    + " gratuita.\"}]}";
        var hallazgo = cliente(respuesta).investigar(List.of(lugar), "Valencia").get(lugar.id());
        assertEquals(0.0, hallazgo.precioMinimo());
        assertEquals("EUR", hallazgo.moneda());
        assertEquals("https://example.org", hallazgo.fuentePrecio());
        assertTrue(
                cliente(respuesta.replace("Entrada gratuita.", "Entrada gratuita con donación."))
                        .investigar(List.of(lugar), "Valencia")
                        .isEmpty());
    }

    @Test
    void reconoceNombreAlternativoDeGeoapifyYUnePrecioYHorarioDeDosFuentes() throws Exception {
        var museo = new LugarDto(
                "id", "Museu de Belles Arts de València",
                "Museo de Bellas Artes de Valencia, Calle de San Pío V, 9, Valencia",
                null, null, List.of(), List.of(), null);
        String respuesta = "{\"results\":["
                + "{\"title\":\"Horarios y tarifas - Museo de Bellas Artes de Valencia\","
                + "\"url\":\"https://museo.example/oficial\",\"content\":\"Entrada gratuita.\"},"
                + "{\"title\":\"Museo de Bellas Artes de Valencia\","
                + "\"url\":\"https://turismo.example/horario\","
                + "\"content\":\"Horario de apertura\\nMartes a domingo\\n10:00 - 20:00.\"}]}";

        var hallazgo = cliente(respuesta).investigar(List.of(museo), "Valencia").get("id");

        assertEquals(0.0, hallazgo.precioMinimo());
        assertTrue(hallazgo.horario().contains("10:00 - 20:00"));
        assertEquals("https://museo.example/oficial", hallazgo.fuentePrecio());
        assertEquals("https://turismo.example/horario", hallazgo.fuenteHorario());
        assertEquals(2, hallazgo.fuentes().size());
    }

    @Test
    void ignoraRedesSocialesYNoConfundeDiasDeCierreConEntradaCondicionada()
            throws Exception {
        String respuesta = "{\"results\":["
                + "{\"title\":\"Museo Azul Valencia\",\"url\":\"https://www.instagram.com/p/1\","
                + "\"content\":\"Museo Azul Valencia. Entrada gratuita. Horario 10:00 - 20:00.\"},"
                + "{\"title\":\"Museo Azul Valencia\",\"url\":\"https://museo.example/visitas\","
                + "\"content\":\"Días de cierre: lunes. Tickets. Entrada gratuita. Horario de apertura\\nMartes a domingo\\n10:00 - 20:00.\"}]}";

        var hallazgo = cliente(respuesta).investigar(List.of(lugar), "Valencia").get(lugar.id());

        assertEquals(0.0, hallazgo.precioMinimo());
        assertEquals("https://museo.example/visitas", hallazgo.fuentePrecio());
        assertEquals("https://museo.example/visitas", hallazgo.fuenteHorario());
        assertEquals(1, hallazgo.fuentes().size());
    }

    @Test
    void noAtribuyeHorarioDeUnaEntidadAlMuseoMencionadoEnSuPagina() throws Exception {
        var museo = new LugarDto(
                "id", "Museu de l'Arròs de València",
                "Museo del Arroz, Calle del Rosario, 3, Valencia",
                null, null, List.of(), List.of(), null);
        String respuesta = "{\"results\":[{\"title\":\"Arroz de Valencia Denominación de Origen\","
                + "\"url\":\"https://arroz.example\","
                + "\"content\":\"Conoce el Museo del Arroz. Museu de l'Arròs de València."
                + " Horario: De lunes a viernes de 08:00 a 14:30.\"}]}";

        assertTrue(cliente(respuesta).investigar(List.of(museo), "Valencia").isEmpty());
    }

    @Test
    void noUsaPrecioDeResenasDeTripadvisor() throws Exception {
        String respuesta = "{\"results\":[{\"title\":\"Museo Azul Valencia\","
                + "\"url\":\"https://www.tripadvisor.es/Museo-Azul\","
                + "\"content\":\"Museo Azul Valencia. Entrada general 2 EUR.\"}]}";

        assertTrue(cliente(respuesta).investigar(List.of(lugar), "Valencia").isEmpty());
    }
    @Test
    void noAsignaGratuidadGeneralSiHayNegacionesOCondiciones() throws Exception {
        List<String> frases = List.of(
                "No hay entrada gratuita.",
                "Entrada gratuita a partir de las 18:00.");

        for (String frase : frases) {
            String respuesta = """
                    {
                    "results": [
                    {
                    "title": "Museo Azul Valencia",
                    "url": "https://museo.example/visitas",
                    "content": "%s"
                    }
                    ]
                    }
                    """.formatted(frase);

            var hallazgo = cliente(respuesta)
                    .investigar(List.of(lugar), "Valencia")
                    .get(lugar.id());

            // El texto ambiguo no debe convertirse en un precio general.
            assertTrue(
                    hallazgo == null
                            || (hallazgo.precioMinimo() == null
                                    && hallazgo.precioMaximo() == null),
                    "No debería asignarse un precio a esta frase: " + frase);
        }
    }
    @Test
    void noAceptaHorariosAmPmRecortados() throws Exception {
        List<String> frases = List.of(
                "Opening hours 09:00 - 05:00 PM.",
                "Opening hours 09:00 AM - 05:00 PM.",
                "Opening hours 09:00 - 05:00PM.",
                "Opening hours 09:00am - 05:00pm.");

        for (String frase : frases) {
            String respuesta = """
                    {
                    "results": [
                    {
                    "title": "Museo Azul Valencia",
                    "url": "https://museo.example/visitas",
                    "content": "%s"
                    }
                    ]
                    }
                    """.formatted(frase);

            var hallazgo = cliente(respuesta)
                    .investigar(List.of(lugar), "Valencia")
                    .get(lugar.id());

            // No debemos guardar un intervalo que haya perdido AM/PM.
            assertTrue(
                    hallazgo == null || hallazgo.horario() == null,
                    "No debería aceptarse este horario sin interpretar: " + frase);
        }
    }
}
