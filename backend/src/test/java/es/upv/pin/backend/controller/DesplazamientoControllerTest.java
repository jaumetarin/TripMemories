package es.upv.pin.backend.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.dto.RespuestaMatrizOrsDto;
import es.upv.pin.backend.exception.DesplazamientoExceptionHandler;
import es.upv.pin.backend.service.DesplazamientoService;

class DesplazamientoControllerTest {

    private final OpenRouteServiceClient cliente =
            mock(OpenRouteServiceClient.class);

    private MockMvc mvc;

    private final String cuerpo = """
            [
              {
                "id": "museo",
                "latitud": 40.4138,
                "longitud": -3.6921
              },
              {
                "id": "parque",
                "latitud": 40.4153,
                "longitud": -3.6844
              }
            ]
            """;

    @BeforeEach
    void preparar() {
        DesplazamientoService servicio =
                new DesplazamientoService(cliente);

        mvc = MockMvcBuilders
                .standaloneSetup(new DesplazamientoController(servicio))
                .setControllerAdvice(new DesplazamientoExceptionHandler())
                .build();
    }

    @Test
    void devuelveLosDesplazamientosConEstadoYFuente() throws Exception {
        RespuestaMatrizOrsDto matriz = new RespuestaMatrizOrsDto(
                List.of(
                        List.of(0.0, 1200.0),
                        List.of(1300.0, 0.0)),
                List.of(
                        List.of(0.0, 900.0),
                        List.of(960.0, 0.0)));

        when(cliente.calcularMatriz(anyList())).thenReturn(matriz);

        mvc.perform(post("/api/desplazamientos/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].origenId").value("museo"))
                .andExpect(jsonPath("$[0].destinoId").value("parque"))
                .andExpect(jsonPath("$[0].distanciaMetros").value(1200.0))
                .andExpect(jsonPath("$[0].duracionSegundos").value(900.0))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[0].fuente").value("openrouteservice"))
                .andExpect(jsonPath("$[1].origenId").value("parque"))
                .andExpect(jsonPath("$[1].destinoId").value("museo"))
                .andExpect(jsonPath("$[1].distanciaMetros").value(1300.0))
                .andExpect(jsonPath("$[1].duracionSegundos").value(960.0));

        verify(cliente).calcularMatriz(List.of(
                new PuntoRutaDto("museo", 40.4138, -3.6921),
                new PuntoRutaDto("parque", 40.4153, -3.6844)));
    }

    @Test
    void devuelve400SiLosIdentificadoresSeRepiten() throws Exception {
        String cuerpoRepetido =
                cuerpo.replace("\"parque\"", "\"museo\"");

        mvc.perform(post("/api/desplazamientos/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRepetido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "Los identificadores de los puntos no pueden repetirse."));

        verifyNoInteractions(cliente);
    }

    @Test
    void devuelve400SiElCuerpoNoEsUnaListaValida() throws Exception {
        for (String cuerpoIncorrecto :
                List.of("{mal-json}", "{}", "null", "")) {

            mvc.perform(post("/api/desplazamientos/calcular")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoIncorrecto))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(
                            "Envía una lista válida de puntos con identificador, latitud y longitud."));
        }

        verifyNoInteractions(cliente);
    }

    @Test
    void devuelve503SiFallaElProveedor() throws Exception {
        when(cliente.calcularMatriz(anyList()))
                .thenThrow(new IllegalStateException(
                        "No se ha podido consultar el servicio de desplazamientos."));

        mvc.perform(post("/api/desplazamientos/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(
                        "No se ha podido consultar el servicio de desplazamientos."));
    }
}