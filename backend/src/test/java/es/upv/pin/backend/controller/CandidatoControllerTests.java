package es.upv.pin.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.exception.CandidatoExceptionHandler;
import es.upv.pin.backend.service.CandidatoService;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CandidatoControllerTests {

    private MockRestServiceServer geoapify;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        geoapify = MockRestServiceServer.bindTo(constructor).build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        CandidatoService servicio = new CandidatoService(cliente);
        CandidatoController controlador = new CandidatoController(servicio);

        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new CandidatoExceptionHandler())
                .build();
    }

    private String urlLugares(String categorias) {
        return "https://api.geoapify.com/v2/places"
                + "?categories=" + categorias.replace(",", "%2C")
                + "&filter=place:destino-prueba"
                + "&limit=50"
                + "&lang=es"
                + "&apiKey=clave-de-prueba";
    }
    @Test
    void devuelveLosCandidatosTuristicosTransformados() throws Exception {
        geoapify.expect(requestTo(urlLugares(
                        "tourism.sights,entertainment.museum,leisure.park")))
                .andRespond(withSuccess("""
                        {
                        "features": [
                                {
                                "properties": {
                                "place_id": "museo-prueba",
                                "name": "Museo de prueba",
                                "lat": 40.4,
                                "lon": -3.7,
                                "categories": ["entertainment.museum"],
                                "formatted": "Dirección de prueba"
                                }
                                }
                        ]
                        }
                        """, MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/candidatos/turisticos")
                        .param("destinoId", "destino-prueba"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("museo-prueba"))
                .andExpect(jsonPath("$[0].nombre").value("Museo de prueba"))
                .andExpect(jsonPath("$[0].latitud").value(40.4))
                .andExpect(jsonPath("$[0].longitud").value(-3.7))
                .andExpect(jsonPath("$[0].categorias[0]")
                        .value("entertainment.museum"))
                .andExpect(jsonPath("$[0].direccion")
                        .value("Dirección de prueba"));

        geoapify.verify();
        }
    @Test
    void devuelveUnaListaVaciaSiNoHayRestaurantes() throws Exception {
        geoapify.expect(requestTo(urlLugares("catering.restaurant")))
                .andRespond(withSuccess("""
                        {
                        "features": []
                        }
                        """, MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/candidatos/gastronomicos")
                        .param("destinoId", "destino-prueba"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        geoapify.verify();
        }
    @Test
    void devuelve503SiFallaGeoapify() throws Exception {
        geoapify.expect(requestTo(urlLugares(
                        "tourism.sights,entertainment.museum,leisure.park")))
                .andRespond(withServerError());

        mvc.perform(get("/api/candidatos/turisticos")
                        .param("destinoId", "destino-prueba"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(
                        "No se ha podido consultar el servicio de lugares."));

        geoapify.verify();
        }
}