package es.upv.pin.backend.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.dto.RespuestaMatrizOrsDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenRouteServiceDesdeTest {
    private static final String URL = "https://api.heigit.org/openrouteservice/v2/matrix/foot-walking";
    private final PuntoRutaDto origen = new PuntoRutaDto("origen", 39.47, -0.37);
    private final PuntoRutaDto destino = new PuntoRutaDto("destino", 39.48, -0.38);

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 50})
    void enviaSoloUnaFilaYConservaElOrdenDeLosDestinos(int cantidad) {
        var escenario = escenario("clave-de-prueba");
        var destinos = IntStream.range(0, cantidad)
                .mapToObj(i -> new PuntoRutaDto("destino-" + i, 39.48 + i * 0.001, -0.38)).toList();
        var puntos = Stream.concat(Stream.of(origen), destinos.stream()).toList();
        var distancias = IntStream.range(0, cantidad).mapToObj(i -> 100.0 + i).toList();
        var duraciones = IntStream.range(0, cantidad).mapToObj(i -> 60.0 + i).toList();
        var json = new ObjectMapper();
        var cuerpo = Map.of("locations", puntos.stream().map(p -> List.of(p.longitud(), p.latitud())).toList(),
                "sources", List.of("0"),
                "destinations", IntStream.rangeClosed(1, cantidad).mapToObj(Integer::toString).toList(),
                "metrics", List.of("distance", "duration"), "units", "m");

        escenario.servidor().expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "clave-de-prueba"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(json.writeValueAsString(cuerpo), JsonCompareMode.STRICT))
                .andRespond(withSuccess(json.writeValueAsString(new RespuestaMatrizOrsDto(
                        List.of(distancias), List.of(duraciones))), MediaType.APPLICATION_JSON));

        var respuesta = escenario.cliente().calcularDesde(origen, destinos);

        assertEquals(List.of(distancias), respuesta.distances());
        assertEquals(List.of(duraciones), respuesta.durations());
        escenario.servidor().verify();
    }

    @Test
    void conservaNullComoRutaNoDisponibleSinConvertirloEnCero() {
        var escenario = escenario("clave-de-prueba");
        escenario.servidor().expect(requestTo(URL)).andRespond(withSuccess(
                "{\"distances\":[[null]],\"durations\":[[null]]}", MediaType.APPLICATION_JSON));

        var respuesta = escenario.cliente().calcularDesde(origen, List.of(destino));

        assertNull(respuesta.distances().getFirst().getFirst());
        assertNull(respuesta.durations().getFirst().getFirst());
        escenario.servidor().verify();
    }

    @Test
    void rechazaListasAusentesVaciasOConMasDeCincuentaDestinosSinHacerPeticiones() {
        var escenario = escenario("clave-de-prueba");
        assertThrows(IllegalArgumentException.class, () -> escenario.cliente().calcularDesde(origen, null));
        assertThrows(IllegalArgumentException.class, () -> escenario.cliente().calcularDesde(origen, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> escenario.cliente().calcularDesde(origen, Collections.nCopies(51, destino)));
        escenario.servidor().verify();
    }

    @ParameterizedTest
    @MethodSource("puntosInvalidos")
    void rechazaCoordenadasInvalidasEnOrigenYDestinosAntesDeConsultar(PuntoRutaDto punto) {
        var escenario = escenario("clave-de-prueba");
        assertThrows(IllegalArgumentException.class,
                () -> escenario.cliente().calcularDesde(punto, List.of(destino)));
        assertThrows(IllegalArgumentException.class,
                () -> escenario.cliente().calcularDesde(origen, Collections.singletonList(punto)));
        escenario.servidor().verify();
    }

    private static Stream<PuntoRutaDto> puntosInvalidos() {
        return Stream.of(null,
                new PuntoRutaDto("p", null, -0.37),
                new PuntoRutaDto("p", 39.47, null),
                new PuntoRutaDto("p", 90.1, -0.37),
                new PuntoRutaDto("p", -90.1, -0.37),
                new PuntoRutaDto("p", 39.47, 180.1),
                new PuntoRutaDto("p", 39.47, -180.1),
                new PuntoRutaDto("p", Double.NaN, -0.37),
                new PuntoRutaDto("p", 39.47, Double.POSITIVE_INFINITY));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}",
            "{\"distances\":null,\"durations\":[[60]]}",
            "{\"distances\":[[100]],\"durations\":null}",
            "{\"distances\":[],\"durations\":[[60]]}",
            "{\"distances\":[null],\"durations\":[[60]]}",
            "{\"distances\":[[]],\"durations\":[[60]]}",
            "{\"distances\":[[100,200]],\"durations\":[[60]]}",
            "{\"distances\":[[100],[200]],\"durations\":[[60]]}",
            "{\"distances\":[[100]],\"durations\":[[60,120]]}",
            "{\"distances\":[[-1]],\"durations\":[[60]]}",
            "{\"distances\":[[100]],\"durations\":[[-1]]}",
            "{\"distances\":[[1e400]],\"durations\":[[60]]}",
            "{\"distances\":[[100]],\"durations\":[[1e400]]}",
            "respuesta que no es JSON"
    })
    void rechazaRespuestasIncompletasConDimensionesIncorrectasONumerosInvalidos(String cuerpo) {
        var escenario = escenario("clave-de-prueba");
        escenario.servidor().expect(requestTo(URL))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class,
                () -> escenario.cliente().calcularDesde(origen, List.of(destino)));
        escenario.servidor().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429, 500})
    void comunicaLosErroresHttpSinReintentarAutomaticamente(int estado) {
        var escenario = escenario("clave-de-prueba");
        escenario.servidor().expect(requestTo(URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(estado)));

        var error = assertThrows(IllegalStateException.class,
                () -> escenario.cliente().calcularDesde(origen, List.of(destino)));

        assertEquals("No se ha podido consultar el servicio de desplazamientos.", error.getMessage());
        escenario.servidor().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaLaClaveAusenteSinHacerPeticiones(String clave) {
        var escenario = escenario(clave);

        var error = assertThrows(IllegalStateException.class,
                () -> escenario.cliente().calcularDesde(origen, List.of(destino)));

        assertEquals("La clave de openrouteservice no está configurada.", error.getMessage());
        escenario.servidor().verify();
    }

    private static Escenario escenario(String clave) {
        var constructor = RestClient.builder().baseUrl("https://api.heigit.org/openrouteservice");
        var servidor = MockRestServiceServer.bindTo(constructor).build();
        return new Escenario(new OpenRouteServiceClient(constructor.build(), clave), servidor);
    }

    private record Escenario(OpenRouteServiceClient cliente, MockRestServiceServer servidor) {}
}
