package es.upv.pin.backend.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import es.upv.pin.backend.exception.FechaHoraExceptionHandler;
import es.upv.pin.backend.service.FechaHoraService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FechaHoraControllerTests {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FechaHoraController(new FechaHoraService()))
            .setControllerAdvice(new FechaHoraExceptionHandler()).build();

    @ParameterizedTest
    @CsvSource({"2026-11-01T18:00, 1", "2026-11-10T18:00, 10", "2026-11-02T07:00, 2"})
    void aceptaLosLimitesYConservaFechaYHora(String salida, int dias) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-fecha-hora").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("2026-11-01T10:00", salida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llegada").value("2026-11-01T10:00:00"))
                .andExpect(jsonPath("$.salida").value(salida + ":00"))
                .andExpect(jsonPath("$.duracionDias").value(dias));
    }

    @Test
    void explicaLosCamposPendientes() throws Exception {
        mvc.perform(post("/api/itinerarios/validar-fecha-hora").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Completa la fecha y la hora de llegada."));
        mvc.perform(post("/api/itinerarios/validar-fecha-hora").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"llegada\":\"2026-11-01T10:00\",\"salida\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Completa la fecha y la hora de salida."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-01T09:00", "2026-11-01T10:00", "2026-10-31T18:00", "2026-11-11T08:00"})
    void bloqueaIntervalosInvalidosYPermiteCorregirlos(String salida) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-fecha-hora").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("2026-11-01T10:00", salida)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(post("/api/itinerarios/validar-fecha-hora").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("2026-11-01T10:00", "2026-11-01T18:00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duracionDias").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{mal-json}", "null", "",
        "{\"llegada\":\"2026-11-01\",\"salida\":\"2026-11-01T18:00\"}",
        "{\"llegada\":\"2026-02-30T10:00\",\"salida\":\"2026-03-01T18:00\"}",
        "{\"llegada\":\"2026-11-01T24:00\",\"salida\":\"2026-11-02T18:00\"}",
        "{\"llegada\":\"2026-11-01T10:60\",\"salida\":\"2026-11-02T18:00\"}"
    })
    void rechazaFormatosInvalidosYFechasImposibles(String cuerpo) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-fecha-hora").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Envía una fecha y hora válidas para llegada y salida, con el formato AAAA-MM-DDTHH:mm."));
    }

    private String cuerpo(String llegada, String salida) {
        return """
                {"llegada":"%s","salida":"%s","duracionDias":999}
                """.formatted(llegada, salida);
    }
}
