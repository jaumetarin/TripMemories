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
}
