package es.upv.pin.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.exception.CandidatoExceptionHandler;
import es.upv.pin.backend.service.*;
import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreparacionCandidatosControllerTests {
    private MockRestServiceServer proveedor;
    private MockMvc mvc;
    private final String cuerpo = """
            {"destino":{"id":"madrid","nombre":"Madrid","region":"Madrid","pais":"España","latitud":40.4,"longitud":-3.7},
             "disponibilidad":{"llegada":"2026-11-10T10:00","salida":"2026-11-12T18:00"},"alojamiento":"Hotel",
             "preferencias":{"intereses":["GASTRONOMIA"],"categoriasExcluidas":[],"subcategoriasExcluidas":["COMIDA_RAPIDA"],
              "nivelGasto":"ECONOMICO","distanciaKm":3}}
            """;

    @BeforeEach
    void preparar() {
        var constructor = RestClient.builder().baseUrl("https://api.geoapify.com");
        proveedor = MockRestServiceServer.bindTo(constructor).build();
        var clasificacion = new ClasificacionLugaresService();
        var servicio = new PreparacionCandidatosService(new GeoapifyClient(constructor.build(), "clave-de-prueba"),
                new ConfiguracionItinerarioService(new FechaHoraService(), new PreferenciasService()),
                clasificacion);
        mvc = MockMvcBuilders.standaloneSetup(new PreparacionCandidatosController(servicio))
                .setControllerAdvice(new CandidatoExceptionHandler()).build();
    }

    private String url() {
        return "https://api.geoapify.com/v2/places?categories="
                + new ClasificacionLugaresService().categoriasConsulta(GASTRONOMIA).replace(",", "%2C")
                + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba";
    }

    @Test
    void devuelveUnicosSinExcluidosYSoloNombreYDireccion() throws Exception {
        proveedor.expect(requestTo(url())).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Restaurante A","formatted":"Calle A, Madrid","lat":40.4,"lon":-3.7,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"a","name":"Restaurante A","lat":40.4,"lon":-3.7,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":"Local B","lat":40.5,"lon":-3.7,"categories":["catering.fast_food.burger"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/candidatos/preparar").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].length()").value(2))
                .andExpect(jsonPath("$[0].horario").doesNotExist())
                .andExpect(jsonPath("$[0].nombre").value("Restaurante A"))
                .andExpect(jsonPath("$[0].direccion").value("Calle A, Madrid"));
        proveedor.verify();
    }

    @Test
    void devuelveHorarioSoloCuandoExisteYDescartaElNombreDesconocido() throws Exception {
        proveedor.expect(requestTo(url())).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Con horario","opening_hours":"Mo-Su 10:00-20:00",
                  "categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":"Sin horario","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"c","name":"Desconocido","categories":["catering.restaurant"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/candidatos/preparar").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Con horario"))
                .andExpect(jsonPath("$[0].horario").value("Mo-Su 10:00-20:00"))
                .andExpect(jsonPath("$[1].nombre").value("Sin horario"))
                .andExpect(jsonPath("$[1].length()").value(2))
                .andExpect(jsonPath("$[1].horario").doesNotExist());
        proveedor.verify();
    }

    @Test
    void unaRespuestaVaciaEsUnExitoYUnFalloEs503() throws Exception {
        proveedor.expect(requestTo(url())).andRespond(withServerError());
        proveedor.expect(requestTo(url())).andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/candidatos/preparar").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(post("/api/candidatos/preparar").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        proveedor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "[]", "{mal-json}"})
    void rechazaCuerposInvalidosSinConsultarAlProveedor(String entrada) throws Exception {
        mvc.perform(post("/api/candidatos/preparar").contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        proveedor.verify();
    }

    @Test
    void rechazaLaContradiccionDePreferenciasAntesDeBuscar() throws Exception {
        mvc.perform(post("/api/candidatos/preparar").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.replace("\"categoriasExcluidas\":[]", "\"categoriasExcluidas\":[\"GASTRONOMIA\"]")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        proveedor.verify();
    }
}
