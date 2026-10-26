package es.upv.pin.backend.service;

import java.util.List;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.DestinoDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DestinoServiceTests {

    private MockRestServiceServer servidor;
    private DestinoService servicio;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");
        servidor = MockRestServiceServer.bindTo(constructor).build();
        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");
        servicio = new DestinoService(cliente);
    }

    @Test
    void noConsultaElProveedorCuandoElTextoEsInsuficiente() {
        assertTrue(servicio.buscar(null).isEmpty());
        assertTrue(servicio.buscar("").isEmpty());
        assertTrue(servicio.buscar("   ").isEmpty());
        assertTrue(servicio.buscar(" Ma ").isEmpty());
        servidor.verify();
    }

    @Test
    void rechazaConsultasDemasiadoLargasSinConsultarElProveedor() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> servicio.buscar("a".repeat(121)));

        assertEquals("La búsqueda no puede superar los 120 caracteres.",
                error.getMessage());
        servidor.verify();
    }

    @Test
    void limpiaElTextoYTransformaLosDatosDelProveedor() {
        responderCon("""
                {"results": [{
                  "place_id": "madrid",
                  "city": " Madrid ",
                  "name": "Nombre alternativo",
                  "state": "Comunidad de Madrid",
                  "country": "España",
                  "lat": 40.4168,
                  "lon": -3.7038
                }]}
                """);

        List<DestinoDto> destinos = servicio.buscar("  Madrid  ");

        assertEquals(List.of(new DestinoDto(
                "madrid", "Madrid", "Comunidad de Madrid", "España",
                40.4168, -3.7038)), destinos);
        servidor.verify();
    }

    @Test
    void descartaResultadosInvalidosYAdmiteCoordenadasCero() {
        responderCon("""
                {"results": [
                  null,
                  {"city": "Sin identificador", "lat": 1, "lon": 1},
                  {"place_id": " ", "city": "Vacío", "lat": 1, "lon": 1},
                  {"place_id": "sin-latitud", "city": "Sin latitud", "lon": 1},
                  {"place_id": "sin-longitud", "city": "Sin longitud", "lat": 1},
                  {"place_id": "latitud", "city": "Fuera", "lat": 91, "lon": 1},
                  {"place_id": "longitud", "city": "Fuera", "lat": 1, "lon": -181},
                  {"place_id": "sin-nombre", "lat": 1, "lon": 1},
                  {"place_id": "cero", "city": "Punto cero", "lat": 0, "lon": 0}
                ]}
                """);

        assertEquals(List.of(new DestinoDto(
                "cero", "Punto cero", "", "", 0, 0)),
                servicio.buscar("Madrid"));
        servidor.verify();
    }

    @Test
    void utilizaNombresAlternativosYEliminaDuplicadosPorIdentificador() {
        responderCon("""
                {"results": [
                  {"place_id": "uno", "city": " ", "name": " Nombre ",
                   "address_line1": "Otra dirección", "lat": 1, "lon": 2},
                  {"place_id": "dos", "address_line1": " Dirección ", "lat": 3, "lon": 4},
                  {"place_id": "uno", "city": "Duplicado", "lat": 1, "lon": 2},
                  {"place_id": "tres", "city": "Nombre", "lat": 5, "lon": 6}
                ]}
                """);

        assertEquals(List.of(
                new DestinoDto("uno", "Nombre", "", "", 1, 2),
                new DestinoDto("dos", "Dirección", "", "", 3, 4),
                new DestinoDto("tres", "Nombre", "", "", 5, 6)),
                servicio.buscar("Madrid"));
        servidor.verify();
    }

    @Test
    void eliminaElDuplicadoDeLorcaAunqueTengaOtroIdentificadorYCoordenadas() {
        servidor.expect(requestTo("https://api.geoapify.com/v1/geocode/autocomplete"
                        + "?text=Lorca&type=city&lang=es&limit=5&format=json&apiKey=clave-de-prueba"))
                .andRespond(withSuccess("""
                        {"results": [
                          {"place_id": "lorca-ciudad", "city": "Lorca", "state": "Región de Murcia",
                           "country": "España", "lat": 37.6711989, "lon": -1.6988592},
                          {"place_id": "lorcha", "city": "Lorcha", "state": "Comunidad Valenciana",
                           "country": "España", "lat": 38.8438222, "lon": -0.31146},
                          {"place_id": "lorca-otro-id", "city": "Lorca", "state": "Región de Murcia",
                           "country": "España", "lat": 37.672488, "lon": -1.6974536}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<DestinoDto> destinos = servicio.buscar("Lorca");

        assertEquals(List.of("lorca-ciudad", "lorcha"), destinos.stream().map(DestinoDto::id).toList());
        assertEquals(37.6711989, destinos.getFirst().latitud());
        assertEquals(-1.6988592, destinos.getFirst().longitud());
        servidor.verify();
    }

    @Test
    void comparaLosDuplicadosSinDistinguirMayusculasTildesNiEspacios() {
        responderCon("""
                {"results": [
                  {"place_id": "primero", "city": "San José", "state": "Región Central",
                   "country": "España", "lat": 40, "lon": -3},
                  {"place_id": "segundo", "city": " SAN  JOSE ", "state": " region  CENTRAL ",
                   "country": " ESPANA ", "lat": 40.001, "lon": -3.001}
                ]}
                """);

        List<DestinoDto> destinos = servicio.buscar("Madrid");

        assertEquals(1, destinos.size());
        assertEquals("primero", destinos.getFirst().id());
        assertEquals("San José", destinos.getFirst().nombre());
        servidor.verify();
    }

    @Test
    void conservaHomonomosLejanosYDestinosConNombreRegionOPaisDistintos() {
        responderCon("""
                {"results": [
                  {"place_id": "original", "city": "Villa", "state": "Región A",
                   "country": "País A", "lat": 40, "lon": -3},
                  {"place_id": "lejano", "city": "Villa", "state": "Región A",
                   "country": "País A", "lat": 41, "lon": -3},
                  {"place_id": "otra-region", "city": "Villa", "state": "Región B",
                   "country": "País A", "lat": 40.001, "lon": -3},
                  {"place_id": "otro-pais", "city": "Villa", "state": "Región A",
                   "country": "País B", "lat": 40.001, "lon": -3},
                  {"place_id": "otro-nombre", "city": "Villa Nueva", "state": "Región A",
                   "country": "País A", "lat": 40.001, "lon": -3}
                ]}
                """);

        assertEquals(List.of("original", "lejano", "otra-region", "otro-pais", "otro-nombre"),
                servicio.buscar("Madrid").stream().map(DestinoDto::id).toList());
        servidor.verify();
    }

    @Test
    void devuelveComoMaximoCincoDestinosEnElOrdenRecibido() {
        responderCon("""
                {"results": [
                  {"place_id": "1", "city": "Uno", "lat": 1, "lon": 1},
                  {"place_id": "2", "city": "Dos", "lat": 2, "lon": 2},
                  {"place_id": "3", "city": "Tres", "lat": 3, "lon": 3},
                  {"place_id": "4", "city": "Cuatro", "lat": 4, "lon": 4},
                  {"place_id": "5", "city": "Cinco", "lat": 5, "lon": 5},
                  {"place_id": "6", "city": "Seis", "lat": 6, "lon": 6}
                ]}
                """);

        List<DestinoDto> destinos = servicio.buscar("Madrid");

        assertEquals(5, destinos.size());
        assertEquals("1", destinos.get(0).id());
        assertEquals("5", destinos.get(4).id());
        servidor.verify();
    }

    @Test
    void propagaElFalloDelProveedorEnLugarDeDevolverUnaListaVacia() {
        servidor.expect(requestTo(urlMadrid()))
                .andRespond(withServerError());

        assertThrows(IllegalStateException.class,
                () -> servicio.buscar("Madrid"));
        servidor.verify();
    }

    private void responderCon(String json) {
        servidor.expect(requestTo(urlMadrid()))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private String urlMadrid() {
        return "https://api.geoapify.com/v1/geocode/autocomplete"
                + "?text=Madrid&type=city&lang=es&limit=5&format=json"
                + "&apiKey=clave-de-prueba";
    }
}
