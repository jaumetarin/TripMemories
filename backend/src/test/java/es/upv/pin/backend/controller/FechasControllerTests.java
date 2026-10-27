package es.upv.pin.backend.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import es.upv.pin.backend.exception.FechasExceptionHandler;
import es.upv.pin.backend.service.FechasService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FechasControllerTests {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FechasController(new FechasService()))
            .setControllerAdvice(new FechasExceptionHandler()).build();

    @ParameterizedTest
    @CsvSource({"2026-11-01, 1", "2026-11-10, 10"})
    void aceptaLosLimitesYDevuelveFechasEnFormatoIso(String fin, int dias) throws Exception {
        String cuerpo = """
                {"fechaInicio":"2026-11-01","fechaFin":"%s","duracionDias":999}
                """.formatted(fin);
        mvc.perform(post("/api/itinerarios/validar-fechas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaInicio").value("2026-11-01"))
                .andExpect(jsonPath("$.fechaFin").value(fin))
                .andExpect(jsonPath("$.duracionDias").value(dias));
    }

    @Test
    void explicaLasFechasPendientes() throws Exception {
        mvc.perform(post("/api/itinerarios/validar-fechas").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Selecciona una fecha de inicio."));
        mvc.perform(post("/api/itinerarios/validar-fechas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-11-01\",\"fechaFin\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Selecciona una fecha de fin."));
    }

    @Test
    void bloqueaFechasInvertidasYSuperioresADiezDiasYPermiteCorregirlas() throws Exception {
        mvc.perform(post("/api/itinerarios/validar-fechas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-11-10\",\"fechaFin\":\"2026-11-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fecha de fin debe ser igual o posterior a la de inicio."));
        mvc.perform(post("/api/itinerarios/validar-fechas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-11-01\",\"fechaFin\":\"2026-11-11\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El viaje puede durar como máximo 10 días, contando el inicio y el fin."));
        mvc.perform(post("/api/itinerarios/validar-fechas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-11-01\",\"fechaFin\":\"2026-11-10\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duracionDias").value(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{mal-json}", "null", "",
        "{\"fechaInicio\":\"no-es-una-fecha\",\"fechaFin\":\"2026-11-01\"}",
        "{\"fechaInicio\":\"2026-02-30\",\"fechaFin\":\"2026-03-01\"}"
    })
    void rechazaPeticionesQueNoContienenFechasValidas(String cuerpo) throws Exception {
        mvc.perform(post("/api/itinerarios/validar-fechas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Envía fechas válidas con el formato AAAA-MM-DD."));
    }
}
