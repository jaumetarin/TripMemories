package es.upv.pin.backend.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import es.upv.pin.backend.exception.ConfiguracionItinerarioExceptionHandler;
import es.upv.pin.backend.service.ConfiguracionItinerarioService;
import es.upv.pin.backend.service.FechaHoraService;
import es.upv.pin.backend.service.PreferenciasService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfiguracionItinerarioControllerTests {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConfiguracionItinerarioController(
            new ConfiguracionItinerarioService(new FechaHoraService(), new PreferenciasService())))
            .setControllerAdvice(new ConfiguracionItinerarioExceptionHandler()).build();

    private String cuerpo() {
        return """
                {"destino":{"id":"madrid-id","nombre":"Madrid","region":"Madrid","pais":"España","latitud":40.4,"longitud":-3.7},
                 "disponibilidad":{"llegada":"2026-11-10T10:00","salida":"2026-11-12T18:00","duracionDias":999},
                 "alojamiento":" Hotel Madrid ",
                 "preferencias":{"intereses":["GASTRONOMIA","GASTRONOMIA"],"categoriasExcluidas":["ARTE_MUSEOS"],
                    "subcategoriasExcluidas":["COMIDA_RAPIDA"],"nivelGasto":"ECONOMICO","distanciaKm":3}}
                """;
    }

    @Test
    void confirmaTodaLaConfiguracionYDevuelveUnaVersionCanonicaSinCache() throws Exception {
        mvc.perform(post("/api/itinerarios/confirmar-configuracion").contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.destino.id").value("madrid-id"))
                .andExpect(jsonPath("$.disponibilidad.duracionDias").value(3))
                .andExpect(jsonPath("$.alojamiento").value("Hotel Madrid"))
                .andExpect(jsonPath("$.preferencias.intereses.length()").value(1))
                .andExpect(jsonPath("$.preferencias.subcategoriasExcluidas[0]").value("COMIDA_RAPIDA"))
                .andExpect(jsonPath("$.preferencias.nivelGasto").value("ECONOMICO"))
                .andExpect(jsonPath("$.preferencias.distanciaKm").value(3.0));
    }

    @Test
    void cadaPeticionUsaSusUltimosValoresSinEstadoCompartido() throws Exception {
        for (String nivel : new String[]{"ECONOMICO", "MODERADO", "ECONOMICO"}) {
            mvc.perform(post("/api/itinerarios/confirmar-configuracion").contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo().replace("ECONOMICO", nivel)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.preferencias.nivelGasto").value(nivel))
                    .andExpect(jsonPath("$.preferencias.distanciaKm").value(3.0))
                    .andExpect(jsonPath("$.alojamiento").value("Hotel Madrid"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "[]", "{mal-json}"})
    void rechazaConfiguracionAusenteOJsonIncorrecto(String cuerpo) throws Exception {
        mvc.perform(post("/api/itinerarios/confirmar-configuracion").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"latitud\":40.4,", "\"longitud\":-3.7", "\"id\":\"madrid-id\",",
            "\"preferencias\":", "\"llegada\":\"2026-11-10T10:00\","})
    void losCamposIncompletosNoSeConfirman(String fragmento) throws Exception {
        // Para el último campo de destino se retira también su coma anterior.
        String entrada = cuerpo().replace(fragmento.equals("\"longitud\":-3.7") ? "," + fragmento : fragmento, "");
        mvc.perform(post("/api/itinerarios/confirmar-configuracion").contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void comunicaLosErroresDeNegocioYPermiteCorregirlos() throws Exception {
        mvc.perform(post("/api/itinerarios/confirmar-configuracion").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo().replace("\"distanciaKm\":3", "\"distanciaKm\":11")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(post("/api/itinerarios/confirmar-configuracion").contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isOk());
    }
}
