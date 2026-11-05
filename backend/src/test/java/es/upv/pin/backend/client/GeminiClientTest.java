package es.upv.pin.backend.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.LugarDto;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

class GeminiClientTest {
    private final LugarDto lugar =
            new LugarDto(
                    "internal-secret-id",
                    "Museo",
                    "Calle 1",
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null);

    @SuppressWarnings("unchecked")
    private GeminiClient cliente(String texto) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(200);
        when(respuesta.body()).thenReturn(texto);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
        return new GeminiClient("test", http);
    }

    @Test
    void aceptaReferenciaCorrectaYRechazaAjenas() throws Exception {
        String respuesta =
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"valoraciones\\\":[{\\\"ref\\\":\\\"c1\\\",\\\"afinidad\\\":75,\\\"duracionMinutos\\\":60,\\\"etiqueta\\\":\\\"Arte\\\",\\\"motivo\\\":\\\"Interesante\\\"}]}\"}]}}]}";
        assertEquals(
                75,
                cliente(respuesta)
                        .evaluar(List.of(lugar), List.of(), "Valencia")
                        .get(lugar.id())
                        .afinidad());
        assertThrows(
                IllegalStateException.class,
                () ->
                        cliente(respuesta.replace("c1", "c2"))
                                .evaluar(List.of(lugar), List.of(), "Valencia"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        cliente(respuesta.replace("afinidad\\\":75", "afinidad\\\":101"))
                                .evaluar(List.of(lugar), List.of(), "Valencia"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void instruccionesLimitanMotivosYNoEnvianIds() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(200);
        when(respuesta.body())
                .thenReturn(
                        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"valoraciones\\\":[{\\\"ref\\\":\\\"c1\\\",\\\"afinidad\\\":75,\\\"duracionMinutos\\\":60,\\\"etiqueta\\\":\\\"Arte\\\",\\\"motivo\\\":\\\"Interés\\\"}]}\"}]}}]}");
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(respuesta);
        new GeminiClient("test", http).evaluar(List.of(lugar), List.of(), "Valencia");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompletableFuture<Void> fin = new CompletableFuture<>();
        captor.getValue()
                .bodyPublisher()
                .orElseThrow()
                .subscribe(
                        new Flow.Subscriber<ByteBuffer>() {
                            public void onSubscribe(Flow.Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            public void onNext(ByteBuffer b) {
                                byte[] parte = new byte[b.remaining()];
                                b.get(parte);
                                bytes.writeBytes(parte);
                            }

                            public void onError(Throwable e) {
                                fin.completeExceptionally(e);
                            }

                            public void onComplete() {
                                fin.complete(null);
                            }
                        });
        fin.join();
        String cuerpo = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(cuerpo.contains("exclusivamente el nombre"));
        assertTrue(cuerpo.contains("No uses memoria"));
        assertTrue(cuerpo.contains("popularidad, historia, precios ni horarios"));
        assertTrue(cuerpo.contains("nombres propios"));
        assertTrue(cuerpo.contains("evidencia equivalente"));
        assertTrue(cuerpo.contains("c1"));
        assertFalse(cuerpo.contains(lugar.id()));
    }
}
