package es.upv.pin.backend.client;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;


import java.util.List;

import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.dto.RespuestaMatrizOrsDto;

class OpenRouteServiceClientTest {

    @Test
    void enviaCoordenadasLonLatYRecuperaLaMatriz() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.heigit.org/openrouteservice");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        OpenRouteServiceClient cliente = new OpenRouteServiceClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(
                        "https://api.heigit.org/openrouteservice/v2/matrix/foot-walking"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "clave-de-prueba"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "locations": [
                            [-3.6921, 40.4138],
                            [-3.6844, 40.4153]
                          ],
                          "metrics": ["distance", "duration"],
                          "units": "m"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {
                          "distances": [
                            [0.0, 1200.0],
                            [1200.0, 0.0]
                          ],
                          "durations": [
                            [0.0, 900.0],
                            [900.0, 0.0]
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PuntoRutaDto> puntos = List.of(
                new PuntoRutaDto("museo", 40.4138, -3.6921),
                new PuntoRutaDto("parque", 40.4153, -3.6844));

        RespuestaMatrizOrsDto respuesta = cliente.calcularMatriz(puntos);

        assertEquals(
                List.of(
                        List.of(0.0, 1200.0),
                        List.of(1200.0, 0.0)),
                respuesta.distances());

        assertEquals(
                List.of(
                        List.of(0.0, 900.0),
                        List.of(900.0, 0.0)),
                respuesta.durations());

        servidor.verify();
    }
    @Test
    void rechazaUnaMatrizConColumnasIncompletas() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.heigit.org/openrouteservice");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        OpenRouteServiceClient cliente = new OpenRouteServiceClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(
                        "https://api.heigit.org/openrouteservice/v2/matrix/foot-walking"))
                .andRespond(withSuccess("""
                        {
                          "distances": [
                            [0.0, 1200.0],
                            [1200.0]
                          ],
                          "durations": [
                            [0.0, 900.0],
                            [900.0, 0.0]
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PuntoRutaDto> puntos = List.of(
                new PuntoRutaDto("museo", 40.4138, -3.6921),
                new PuntoRutaDto("parque", 40.4153, -3.6844));

        assertThrows(
                IllegalStateException.class,
                () -> cliente.calcularMatriz(puntos));

        servidor.verify();
    }
    @Test
    void conservaLosTrayectosSinRutaComoNull() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.heigit.org/openrouteservice");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        OpenRouteServiceClient cliente = new OpenRouteServiceClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(
                        "https://api.heigit.org/openrouteservice/v2/matrix/foot-walking"))
                .andRespond(withSuccess("""
                        {
                          "distances": [
                            [0.0, null],
                            [null, 0.0]
                          ],
                          "durations": [
                            [0.0, null],
                            [null, 0.0]
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PuntoRutaDto> puntos = List.of(
                new PuntoRutaDto("museo", 40.4138, -3.6921),
                new PuntoRutaDto("parque", 40.4153, -3.6844));

        RespuestaMatrizOrsDto respuesta = cliente.calcularMatriz(puntos);

        assertNull(respuesta.distances().get(0).get(1));
        assertNull(respuesta.distances().get(1).get(0));
        assertNull(respuesta.durations().get(0).get(1));
        assertNull(respuesta.durations().get(1).get(0));

        assertEquals(0.0, respuesta.distances().get(0).get(0).doubleValue());
        assertEquals(0.0, respuesta.durations().get(0).get(0).doubleValue());

        servidor.verify();
    }
    @ParameterizedTest
    @CsvSource({
            "-1.0, 900.0",
            "1200.0, -1.0"
    })
    void rechazaDistanciasOTiemposNegativos(
            double distancia, double duracion) {

        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.heigit.org/openrouteservice");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        OpenRouteServiceClient cliente = new OpenRouteServiceClient(
                constructor.build(), "clave-de-prueba");

        String respuestaJson = """
                {
                  "distances": [
                    [0.0, %s],
                    [1200.0, 0.0]
                  ],
                  "durations": [
                    [0.0, %s],
                    [900.0, 0.0]
                  ]
                }
                """.formatted(distancia, duracion);

        servidor.expect(requestTo(
                        "https://api.heigit.org/openrouteservice/v2/matrix/foot-walking"))
                .andRespond(withSuccess(
                        respuestaJson, MediaType.APPLICATION_JSON));

        List<PuntoRutaDto> puntos = List.of(
                new PuntoRutaDto("museo", 40.4138, -3.6921),
                new PuntoRutaDto("parque", 40.4153, -3.6844));

        assertThrows(
                IllegalStateException.class,
                () -> cliente.calcularMatriz(puntos));

        servidor.verify();
    }
    @ParameterizedTest
    @ValueSource(ints = {403, 429, 500})
    void comunicaLosErroresDelProveedor(int codigoHttp) {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.heigit.org/openrouteservice");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        OpenRouteServiceClient cliente = new OpenRouteServiceClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(
                        "https://api.heigit.org/openrouteservice/v2/matrix/foot-walking"))
                .andRespond(withStatus(
                        HttpStatusCode.valueOf(codigoHttp)));

        List<PuntoRutaDto> puntos = List.of(
                new PuntoRutaDto("museo", 40.4138, -3.6921),
                new PuntoRutaDto("parque", 40.4153, -3.6844));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> cliente.calcularMatriz(puntos));

        assertEquals(
                "No se ha podido consultar el servicio de desplazamientos.",
                error.getMessage());

        servidor.verify();
    }
        @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaUnaClaveAusenteSinConsultarAlProveedor(String clave) {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.heigit.org/openrouteservice");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        OpenRouteServiceClient cliente = new OpenRouteServiceClient(
                constructor.build(), clave);

        List<PuntoRutaDto> puntos = List.of(
                new PuntoRutaDto("museo", 40.4138, -3.6921),
                new PuntoRutaDto("parque", 40.4153, -3.6844));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> cliente.calcularMatriz(puntos));

        assertEquals(
                "La clave de openrouteservice no está configurada.",
                error.getMessage());

        servidor.verify();
    }
}