package es.upv.pin.backend.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import es.upv.pin.backend.exception.PreferenciasExceptionHandler;
import es.upv.pin.backend.service.PreferenciasService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreferenciasControllerTests {
    private String conGastoDistancia(String cuerpo) {
        return cuerpo.replaceFirst("\\{", "{ \"nivelGasto\":\"SIN_PREFERENCIA\", \"distanciaKm\":3,");
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PreferenciasController(new PreferenciasService()))
            .setControllerAdvice(new PreferenciasExceptionHandler()).build();

    @Test
    void devuelveElCatalogoConCodigosNombresYRelaciones() throws Exception {
        mvc.perform(get("/api/itinerarios/catalogo-preferencias"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].codigo").value("GASTRONOMIA"))
                .andExpect(jsonPath("$[0].nombre").value("Gastronomía"))
                .andExpect(jsonPath("$[0].subcategorias.length()").value(3))
                .andExpect(jsonPath("$[0].subcategorias[0].codigo").value("COMIDA_RAPIDA"));
    }

    @Test
    void aceptaInteresesConExclusionesParcialesYEliminaDuplicados() throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON).content(conGastoDistancia("""
                {"intereses":["GASTRONOMIA","GASTRONOMIA","NATURALEZA_PARQUES"],
                 "categoriasExcluidas":["ARTE_MUSEOS","ARTE_MUSEOS"],
                 "subcategoriasExcluidas":["COMIDA_RAPIDA","SENDERISMO","COMIDA_RAPIDA"]}
                """)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intereses.length()").value(2))
                .andExpect(jsonPath("$.intereses[0]").value("GASTRONOMIA"))
                .andExpect(jsonPath("$.categoriasExcluidas.length()").value(1))
                .andExpect(jsonPath("$.subcategoriasExcluidas.length()").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"intereses\":[\"GASTRONOMIA\"],\"categoriasExcluidas\":[\"GASTRONOMIA\"],\"subcategoriasExcluidas\":[]}",
        "{\"categoriasExcluidas\":[\"GASTRONOMIA\"],\"subcategoriasExcluidas\":[],\"intereses\":[\"GASTRONOMIA\"]}"
    })
    void bloqueaLaContradiccionIndependientementeDelOrdenYPermiteCorregirla(String cuerpo) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON).content(conGastoDistancia(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La categoría «Gastronomía» está seleccionada como interés y como categoría a evitar. Desmarca una de las dos opciones para continuar."));
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON).content(conGastoDistancia("""
                {"intereses":["GASTRONOMIA"],"categoriasExcluidas":[],"subcategoriasExcluidas":["COMIDA_RAPIDA"]}
                """)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intereses[0]").value("GASTRONOMIA"))
                .andExpect(jsonPath("$.subcategoriasExcluidas[0]").value("COMIDA_RAPIDA"));
    }

    @Test
    void aceptaListasVacias() throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON).content(conGastoDistancia("""
                {"intereses":[],"categoriasExcluidas":[],"subcategoriasExcluidas":[]}
                """)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intereses").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"intereses\":[],\"categoriasExcluidas\":[]}",
        "{\"intereses\":[null],\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[]}",
        "{\"intereses\":[],\"categoriasExcluidas\":[null],\"subcategoriasExcluidas\":[]}",
        "{\"intereses\":[],\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[null]}"
    })
    void rechazaPreferenciasIncompletasYElementosNulos(String cuerpo) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON).content(conGastoDistancia(cuerpo)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "null", "{mal-json}", "[]",
        "{\"intereses\":[\"DESCONOCIDA\"],\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[]}",
        "{\"intereses\":[],\"categoriasExcluidas\":[\"SENDERISMO\"],\"subcategoriasExcluidas\":[]}",
        "{\"intereses\":[],\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[\"GASTRONOMIA\"]}",
        "{\"intereses\":\"GASTRONOMIA\",\"categoriasExcluidas\":[],\"subcategoriasExcluidas\":[]}"
    })
    void rechazaJsonYTiposIncorrectosOCodigosDesconocidos(String cuerpo) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-preferencias").contentType(MediaType.APPLICATION_JSON).content(conGastoDistancia(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Envía las listas con códigos del catálogo, un nivel de gasto válido y una distancia numérica en kilómetros."));
    }
}
