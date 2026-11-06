package es.upv.pin.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import es.upv.pin.backend.dto.DiaItinerarioDto;
import es.upv.pin.backend.dto.CandidatoEvaluadoDto;
import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.model.CostLevel;
import es.upv.pin.backend.model.CostConfidence;
import es.upv.pin.backend.model.ScheduleStatus;
import es.upv.pin.backend.dto.BloqueItinerarioDto;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import es.upv.pin.backend.dto.ItinerarioGeneradoDto;
import es.upv.pin.backend.exception.GeneracionItinerarioExceptionHandler;
import es.upv.pin.backend.service.GeneracionItinerarioService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GeneracionItinerarioControllerTests {
    private final GeneracionItinerarioService servicio = mock(GeneracionItinerarioService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new GeneracionItinerarioController(servicio))
            .setControllerAdvice(new GeneracionItinerarioExceptionHandler()).build();
    private final String configuracion = """
            {"destino":{"id":"madrid","nombre":"Madrid","latitud":40.4,"longitud":-3.7},
             "disponibilidad":{"llegada":"2026-11-10T10:00","salida":"2026-11-10T18:00"},
             "alojamiento":"Hotel","preferencias":{"intereses":["GASTRONOMIA"],
             "categoriasExcluidas":[],"subcategoriasExcluidas":[],"nivelGasto":"MODERADO","distanciaKm":3}}
            """;

    @Test
    void devuelveItinerarioSinCache() throws Exception {
        var inicio = LocalDateTime.of(2026, 11, 10, 10, 0);
        var fin = inicio.plusHours(1);
        var bloque = new BloqueItinerarioDto(TipoBloqueItinerario.TIEMPO_LIBRE, inicio, fin,
                null, null, null, null, null, null, false, "Tiempo libre");
        var dia = new DiaItinerarioDto(LocalDate.of(2026, 11, 10), inicio, fin, List.of(bloque));
        var lugar = new LugarDto("museo", "Museo", null, 40.4, -3.7, List.of(), List.of(), null);
        var candidato = new CandidatoEvaluadoDto(lugar, 80, 60, null, null, CostLevel.FREE,
                null, List.of(), null, null, "LOCAL", CostConfidence.UNKNOWN,
                ScheduleStatus.UNVERIFIED, null, null, null, null, null);
        when(servicio.generar(any())).thenReturn(new ItinerarioGeneradoDto(
                List.of(dia), List.of("Aviso"), true, 5, 3, List.of(candidato), true));
        mvc.perform(post("/api/itinerarios/generar").contentType(MediaType.APPLICATION_JSON).content(configuracion))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.dias[0].fecha").value("2026-11-10"))
                .andExpect(jsonPath("$.dias[0].bloques[0].tipo").value("TIEMPO_LIBRE"))
                .andExpect(jsonPath("$.advertencias[0]").value("Aviso"))
                .andExpect(jsonPath("$.parcial").value(true)).andExpect(jsonPath("$.candidatosEvaluados").value(5))
                .andExpect(jsonPath("$.candidatosEnMatriz").value(3))
                .andExpect(jsonPath("$.candidatos[0].lugar.id").value("museo"))
                .andExpect(jsonPath("$.candidatos[0].afinidad").value(80))
                .andExpect(jsonPath("$.candidatos[0].origenEvaluacion").value("LOCAL"))
                .andExpect(jsonPath("$.evaluacionFallback").value(true));
        verify(servicio).generar(any());
    }

    @Test
    void rechazaJsonIncorrecto() throws Exception {
        mvc.perform(post("/api/itinerarios/generar").contentType(MediaType.APPLICATION_JSON).content("{mal-json}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        verifyNoInteractions(servicio);
    }

    @Test
    void rechazaConfiguracionInvalida() throws Exception {
        when(servicio.generar(any())).thenThrow(new IllegalArgumentException("Configuracion invalida"));
        mvc.perform(post("/api/itinerarios/generar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("Configuracion invalida"));
    }

    @Test
    void informaFalloDelProveedor() throws Exception {
        when(servicio.generar(any())).thenThrow(new IllegalStateException("ORS no disponible"));
        mvc.perform(post("/api/itinerarios/generar").contentType(MediaType.APPLICATION_JSON).content(configuracion))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.mensaje").value("ORS no disponible"));
    }
}
