package es.upv.pin.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.exception.DestinoExceptionHandler;
import es.upv.pin.backend.service.DestinoService;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DestinoControllerTests {
    private MockRestServiceServer geoapify;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("https://api.geoapify.com");
        geoapify = MockRestServiceServer.bindTo(constructor).build();
        mvc = crearApi(new GeoapifyClient(constructor.build(), "clave-de-prueba"));
    }

    @Test
    void devuelveAlFrontendLosDestinosTransformados() throws Exception {
        geoapify.expect(requestTo(urlMadrid())).andRespond(withSuccess("""
                {"results":[
                  {"place_id":"madrid","city":"Madrid","state":"Comunidad de Madrid",
                   "country":"España","lat":40.4168,"lon":-3.7038},
                  {"place_id":"sin-coordenadas","city":"Descartado"}
                ]}
                """, MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/destinos").param("texto", "  Madrid  "))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("madrid"))
                .andExpect(jsonPath("$[0].nombre").value("Madrid"))
                .andExpect(jsonPath("$[0].region").value("Comunidad de Madrid"))
                .andExpect(jsonPath("$[0].pais").value("España"))
                .andExpect(jsonPath("$[0].latitud").value(40.4168))
                .andExpect(jsonPath("$[0].longitud").value(-3.7038));
        geoapify.verify();
    }

    @Test
    void noConsultaAlProveedorHastaTenerTresCaracteres() throws Exception {
        mvc.perform(get("/api/destinos").param("texto", "Ma"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/destinos"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        geoapify.verify();
    }

    @Test
    void rechazaConsultasDemasiadoLargas() throws Exception {
        mvc.perform(get("/api/destinos").param("texto", "a".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La búsqueda no puede superar los 120 caracteres."));
        geoapify.verify();
    }

    @Test
    void comunicaLaFaltaDeClaveSinHacerUnaPeticionExterna() throws Exception {
        MockMvc sinClave = crearApi(new GeoapifyClient(RestClient.create(), ""));
        sinClave.perform(get("/api/destinos").param("texto", "Madrid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("La clave de Geoapify no está configurada."));
    }

    @Test
    void noConfundeUnFalloDeGeoapifyConUnaBusquedaSinResultados() throws Exception {
        geoapify.expect(requestTo(urlMadrid())).andRespond(withServerError());
        mvc.perform(get("/api/destinos").param("texto", "Madrid"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("No se ha podido consultar el servicio de destinos."));
        geoapify.verify();
    }

    private MockMvc crearApi(GeoapifyClient cliente) {
        return MockMvcBuilders.standaloneSetup(new DestinoController(new DestinoService(cliente)))
                .setControllerAdvice(new DestinoExceptionHandler()).build();
    }

    private String urlMadrid() {
        return "https://api.geoapify.com/v1/geocode/autocomplete?text=Madrid&type=city"
                + "&lang=es&limit=5&format=json&apiKey=clave-de-prueba";
    }
}
