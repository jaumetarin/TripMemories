package es.upv.pin.backend.client;

import java.util.List;

import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.DestinoGeoapifyDto;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeoapifyClientTests {

    @Test
    void interpretaLosDestinosDeGeoapifyComoRecords() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo("https://api.geoapify.com/v1/geocode/autocomplete"
                        + "?text=Madrid&type=city&lang=es&limit=5&format=json"
                        + "&apiKey=clave-de-prueba"))
                .andRespond(withSuccess("""
                        {
                          "query": {"text": "Madrid"},
                          "results": [
                            {
                              "place_id": "madrid-ejemplo",
                              "city": "Madrid",
                              "name": "Madrid",
                              "address_line1": "Madrid",
                              "state": "Comunidad de Madrid",
                              "country": "España",
                              "lat": 40.4168,
                              "lon": -3.7038,
                              "formatted": "Madrid, España"
                            },
                            {
                              "place_id": "otro-destino",
                              "city": "Otro destino"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<DestinoGeoapifyDto> destinos = cliente.autocompletar("Madrid");

        assertEquals(2, destinos.size());
        DestinoGeoapifyDto destino = destinos.get(0);
        assertEquals("madrid-ejemplo", destino.place_id());
        assertEquals("Madrid", destino.city());
        assertEquals("Madrid", destino.name());
        assertEquals("Madrid", destino.address_line1());
        assertEquals("Comunidad de Madrid", destino.state());
        assertEquals("España", destino.country());
        assertEquals(40.4168, destino.lat().doubleValue());
        assertEquals(-3.7038, destino.lon().doubleValue());
        assertNull(destinos.get(1).lat());
        assertNull(destinos.get(1).lon());
        servidor.verify();
    }

    @Test
    void conservaLosNombresDelJsonDeTripMemories() {
        DestinoDto destino = new DestinoDto(
                "madrid-ejemplo", "Madrid", "Comunidad de Madrid",
                "España", 40.4168, -3.7038);

        JsonMapper conversor = JsonMapper.builder().build();
        JsonNode json = conversor.readTree(conversor.writeValueAsString(destino));

        assertEquals(6, json.size());
        assertEquals("madrid-ejemplo", json.path("id").asString());
        assertEquals("Madrid", json.path("nombre").asString());
        assertEquals("Comunidad de Madrid", json.path("region").asString());
        assertEquals("España", json.path("pais").asString());
        assertEquals(40.4168, json.path("latitud").asDouble());
        assertEquals(-3.7038, json.path("longitud").asDouble());
    }
}
