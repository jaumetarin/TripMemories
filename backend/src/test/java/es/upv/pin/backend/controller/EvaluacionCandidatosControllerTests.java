package es.upv.pin.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import es.upv.pin.backend.client.AiProvider;
import es.upv.pin.backend.client.ResearchProvider;
import es.upv.pin.backend.exception.CandidatoExceptionHandler;
import es.upv.pin.backend.service.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class EvaluacionCandidatosControllerTests {
    private final PreparacionCandidatosService preparacion =
            mock(PreparacionCandidatosService.class);
    private MockMvc mvc;
    private final String configuracion =
            """
            {"destino":{"id":"madrid","nombre":"Madrid","latitud":40.4,"longitud":-3.7},
             "disponibilidad":{"llegada":"2026-11-10T10:00","salida":"2026-11-10T18:00"},
             "alojamiento":"Hotel","preferencias":{"intereses":["GASTRONOMIA"],
             "categoriasExcluidas":[],"subcategoriasExcluidas":[],"nivelGasto":"MODERADO","distanciaKm":3}}
            """;

    @BeforeEach
    void preparar() {
        var validacion =
                new ConfiguracionItinerarioService(
                        new FechaHoraService(), new PreferenciasService());
        var servicio =
                new EvaluacionCandidatosService(
                        validacion,
                        preparacion,
                        mock(AiProvider.class),
                        mock(ResearchProvider.class));
        mvc =
                MockMvcBuilders.standaloneSetup(new EvaluacionCandidatosController(servicio))
                        .setControllerAdvice(new CandidatoExceptionHandler())
                        .build();
    }

    @Test
    void devuelveRespuestaSinCache() throws Exception {
        when(preparacion.preparar(any())).thenReturn(List.of());
        mvc.perform(
                        post("/api/candidatos/evaluar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(configuracion))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.candidatos").isEmpty())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.minutosDisponibles").value(480));
    }

    @Test
    void rechazaJsonYConfiguracionInvalidos() throws Exception {
        for (String cuerpo : List.of("{mal-json}", "{}")) {
            mvc.perform(
                            post("/api/candidatos/evaluar")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }
        verifyNoInteractions(preparacion);
    }

    @Test
    void falloDePreparacionDevuelve503() throws Exception {
        when(preparacion.preparar(any()))
                .thenThrow(new IllegalStateException("Servicio de lugares no disponible."));
        mvc.perform(
                        post("/api/candidatos/evaluar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(configuracion))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }
}
