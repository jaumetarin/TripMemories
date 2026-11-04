package es.upv.pin.backend.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import es.upv.pin.backend.exception.PreferenciasExceptionHandler;
import es.upv.pin.backend.model.NivelGasto;
import es.upv.pin.backend.service.PreferenciasService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GastoDistanciaControllerTests {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PreferenciasController(new PreferenciasService()))
            .setControllerAdvice(new PreferenciasExceptionHandler()).build();

    private String solicitud(String nivel, String distancia) {
        return """
                {"intereses":["GASTRONOMIA","GASTRONOMIA"],"categoriasExcluidas":["ARTE_MUSEOS"],
                 "subcategoriasExcluidas":["COMIDA_RAPIDA"],"nivelGasto":%s,"distanciaKm":%s}
                """.formatted(nivel, distancia);
    }

    @Test
    void ofreceOpcionesAdmitidasConSuSignificado() throws Exception {
        mvc.perform(get("/api/itinerarios/opciones-gasto-distancia"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nivelesGasto.length()").value(4))
                .andExpect(jsonPath("$.nivelesGasto[0].codigo").value("ECONOMICO"))
                .andExpect(jsonPath("$.nivelesGasto[0].nombre").value("Económico"))
                .andExpect(jsonPath("$.nivelesGasto[3].codigo").value("SIN_PREFERENCIA"))
                .andExpect(jsonPath("$.distanciaMinimaKm").value(1))
                .andExpect(jsonPath("$.distanciaMaximaKm").value(10))
                .andExpect(jsonPath("$.pasoDistanciaKm").value(1))
                .andExpect(jsonPath("$.distanciaInicialKm").value(3))
                .andExpect(jsonPath("$.nivelGastoInicial").value("SIN_PREFERENCIA"))
                .andExpect(jsonPath("$.significadoGasto").isNotEmpty())
                .andExpect(jsonPath("$.significadoDistancia").isNotEmpty());
    }

    @ParameterizedTest
    @EnumSource(NivelGasto.class)
    void devuelveElNivelYLaDistanciaParaElResumenSinPerderLasListas(NivelGasto nivel) throws Exception {
        for (String distancia : new String[]{"1", "10"}) {
            mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON)
                    .content(solicitud("\"" + nivel.name() + "\"", distancia)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nivelGasto").value(nivel.name()))
                    .andExpect(jsonPath("$.distanciaKm").value(Double.valueOf(distancia)))
                    .andExpect(jsonPath("$.intereses.length()").value(1))
                    .andExpect(jsonPath("$.categoriasExcluidas[0]").value("ARTE_MUSEOS"))
                    .andExpect(jsonPath("$.subcategoriasExcluidas[0]").value("COMIDA_RAPIDA"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "11", "1.5", "9.9", "\"no-es-un-numero\"", "\"NaN\""})
    void bloqueaDistanciasInvalidasConMensaje(String distancia) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON)
                .content(solicitud("\"MODERADO\"", distancia)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"DESCONOCIDO\"", "\"Económico\"", "\"\"", "1", "\"1\"", "{}", "[]"})
    void bloqueaNivelesInvalidosInclusoOrdinalesNumericos(String nivel) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON)
                .content(solicitud(nivel, "3")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"intereses\":[],\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[]}",
        "{\"intereses\":[],\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[],\"nivelGasto\":\"MODERADO\"}",
        "{\"intereses\":[],\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[],\"distanciaKm\":3}"
    })
    void noSustituyeSilenciosamenteCamposAusentes(String cuerpo) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void permiteCorregirYReenviarLaConfiguracion() throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON)
                .content(solicitud("\"ECONOMICO\"", "0"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON)
                .content(solicitud("\"FLEXIBLE\"", "7"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.nivelGasto").value("FLEXIBLE"))
                .andExpect(jsonPath("$.distanciaKm").value(7.0));
    }
}
