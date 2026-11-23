package es.upv.pin.backend.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.LugarDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

class GeminiClientTest {
    private final LugarDto lugar = new LugarDto("internal-secret-id", "Torres dels Serrans", "Valencia",
            null, null, List.of(), List.of(), null);
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = mock(HttpClient.class);

    @SuppressWarnings("unchecked")
    private GeminiClient cliente(String valoraciones) throws Exception {
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(200);
        when(respuesta.body()).thenReturn(json.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", valoraciones))))))));
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
        return new GeminiClient("test", http);
    }

    private String valoracion(String relevancia) {
        return """
                {"valoraciones":[{"ref":"c1","afinidad":75,"relevancia":%s,"duracionMinutos":60,
                 "etiqueta":"Historia","motivo":"Puerta histórica"}]}
                """.formatted(relevancia);
    }

    @Test
    void aceptaRelevanciaEstimadaYConservaLaIdentidadOriginal() throws Exception {
        var resultado = cliente(valoracion("95")).evaluar(List.of(lugar), List.of(), "Valencia");
        assertEquals(95, resultado.get(lugar.id()).relevancia());
        assertEquals(81, resultado.get(lugar.id()).prioridad());
        assertEquals(1, resultado.size());
    }

    @Test
    void permiteRelevanciaDesconocidaSinInventarUnaPuntuacion() throws Exception {
        var resultado = cliente(valoracion("null")).evaluar(List.of(lugar), List.of(), "Valencia");
        assertNull(resultado.get(lugar.id()).relevancia());
        assertEquals(75, resultado.get(lugar.id()).prioridad());
    }

    @Test
    void rechazaReferenciasAjenasPuntuacionesInvalidasYRelevanciaAusente() throws Exception {
        for (String valor : List.of("-1", "101", "3.5", "\"alta\"")) {
            assertThrows(IllegalStateException.class,
                    () -> cliente(valoracion(valor)).evaluar(List.of(lugar), List.of(), "Valencia"));
        }
        for (String cuerpo : List.of(valoracion("90").replace("c1", "c2"),
                valoracion("90").replace("\"afinidad\":75", "\"afinidad\":101"),
                valoracion("90").replace("\"relevancia\":90,", ""))) {
            assertThrows(IllegalStateException.class,
                    () -> cliente(cuerpo).evaluar(List.of(lugar), List.of(), "Valencia"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void unaSolaPeticionValoraRelevanciaSinEnviarIdsNiPedirDatosPracticosInventados() throws Exception {
        cliente(valoracion("95")).evaluar(List.of(lugar), List.of(), "Valencia");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompletableFuture<Void> fin = new CompletableFuture<>();
        captor.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer b) {
                byte[] parte = new byte[b.remaining()];
                b.get(parte);
                bytes.writeBytes(parte);
            }
            public void onError(Throwable e) { fin.completeExceptionally(e); }
            public void onComplete() { fin.complete(null); }
        });
        fin.join();
        String cuerpo = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(cuerpo.contains("conocimiento general"));
        assertTrue(cuerpo.contains("si no reconoces el lugar con seguridad"));
        assertTrue(cuerpo.contains("No inventes lugares ni afirmes precios u horarios"));
        assertTrue(cuerpo.contains("c1"));
        assertFalse(cuerpo.contains(lugar.id()));
        assertEquals(8192, json.readTree(cuerpo).path("generationConfig").path("maxOutputTokens").asInt());
    }
}
