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
}
