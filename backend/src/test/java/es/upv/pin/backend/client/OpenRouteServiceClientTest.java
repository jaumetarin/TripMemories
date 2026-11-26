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
import java.util.stream.IntStream;
import tools.jackson.databind.ObjectMapper;

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

    @Test void recuperaGeojsonPeatonalSinInstruccionesYNuncaInvierteLongitudLatitud() {
        var builder = RestClient.builder().baseUrl("https://api.heigit.org/openrouteservice");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new OpenRouteServiceClient(builder.build(), "clave-prueba");
        servidor.expect(requestTo("https://api.heigit.org/openrouteservice/v2/directions/foot-walking/geojson"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "clave-prueba"))
                .andExpect(content().json("{\"coordinates\":[[-3.7,40.4],[-3.6,40.5]],\"instructions\":false}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[-3.7,40.4],[-3.65,40.45],[-3.6,40.5]]}}]}", MediaType.APPLICATION_JSON));
        var resultado = cliente.calcularGeometria(new PuntoRutaDto("a", 40.4, -3.7), new PuntoRutaDto("b", 40.5, -3.6));
        assertEquals(List.of(-3.65, 40.45), resultado.coordenadas().get(1));
        assertEquals("openrouteservice", resultado.fuente());
        servidor.verify();
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"features\":[]}",
        "{\"features\":[{\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]}}]}",
        "{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[190,2],[1,2]]}}]}"
    })
    void unaGeometriaInvalidaNoSeConvierteEnCaminoConfirmado(String cuerpo) {
        var builder = RestClient.builder().baseUrl("https://api.heigit.org/openrouteservice");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new OpenRouteServiceClient(builder.build(), "clave-prueba");
        servidor.expect(requestTo("https://api.heigit.org/openrouteservice/v2/directions/foot-walking/geojson"))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> cliente.calcularGeometria(new PuntoRutaDto("a", 40.4, -3.7), new PuntoRutaDto("b", 40.5, -3.6)));
        servidor.verify();
    }

    @Test
    void admiteCincuentaLugaresMasElOrigen() {
        var constructor = RestClient.builder().baseUrl("https://api.heigit.org/openrouteservice");
        var servidor = MockRestServiceServer.bindTo(constructor).build();
        var cliente = new OpenRouteServiceClient(constructor.build(), "clave-de-prueba");
        var puntos = IntStream.range(0, 51)
                .mapToObj(i -> new PuntoRutaDto("punto-" + i, 40.4 + i * .001, -3.7)).toList();
        var matriz = IntStream.range(0, 51).mapToObj(i -> IntStream.range(0, 51)
                .mapToObj(j -> i == j ? 0.0 : 10.0).toList()).toList();
        var json = new ObjectMapper();
        servidor.expect(requestTo("https://api.heigit.org/openrouteservice/v2/matrix/foot-walking"))
                .andExpect(content().json(json.writeValueAsString(java.util.Map.of(
                        "locations", puntos.stream().map(p -> List.of(p.longitud(), p.latitud())).toList(),
                        "metrics", List.of("distance", "duration"), "units", "m")), JsonCompareMode.STRICT))
                .andRespond(withSuccess(json.writeValueAsString(new RespuestaMatrizOrsDto(matriz, matriz)),
                        MediaType.APPLICATION_JSON));

        var respuesta = cliente.calcularMatriz(puntos);

        assertEquals(51, respuesta.distances().size());
        assertEquals(51, respuesta.durations().getFirst().size());
        servidor.verify();
    }

    @Test
    void rechazaCincuentaYDosPuntosSinConsultarAlProveedor() {
        var constructor = RestClient.builder().baseUrl("https://api.heigit.org/openrouteservice");
        var servidor = MockRestServiceServer.bindTo(constructor).build();
        var cliente = new OpenRouteServiceClient(constructor.build(), "clave-de-prueba");
        var puntos = IntStream.range(0, 52)
                .mapToObj(i -> new PuntoRutaDto("punto-" + i, 40.4, -3.7)).toList();

        assertThrows(IllegalArgumentException.class, () -> cliente.calcularMatriz(puntos));
        servidor.verify();
    }

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
