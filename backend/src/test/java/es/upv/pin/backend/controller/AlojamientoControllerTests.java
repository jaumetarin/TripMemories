package es.upv.pin.backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.FeatureLugarGeoapifyDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;
import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;
import es.upv.pin.backend.exception.AlojamientoExceptionHandler;
import es.upv.pin.backend.service.AlojamientoService;

class AlojamientoControllerTests {

    private GeoapifyClient cliente;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        cliente = mock(GeoapifyClient.class);

        AlojamientoService servicio = new AlojamientoService(cliente);
        AlojamientoController controlador =
                new AlojamientoController(servicio);

        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new AlojamientoExceptionHandler())
                .build();
    }

    @Test
    void devuelveLasSugerenciasComoJson() throws Exception {
        var hotel = new FeatureLugarGeoapifyDto(
                new LugarGeoapifyDto(
                        "hotel-prueba",
                        "Hotel Centro",
                        40.4,
                        -3.7,
                        List.of(),
                        "Calle de Prueba 10",
                        null,
                        null,
                        null,
                        null,
                        "amenity"));

        when(cliente.buscarAlojamientos("Hotel", "destino-prueba"))
                .thenReturn(new RespuestaLugaresGeoapifyDto(
                        List.of(hotel)));

        mvc.perform(get("/api/alojamientos")
                        .param("texto", "Hotel")
                        .param("destinoId", "destino-prueba"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("hotel-prueba"))
                .andExpect(jsonPath("$[0].nombre").value("Hotel Centro"))
                .andExpect(jsonPath("$[0].direccion")
                        .value("Calle de Prueba 10"))
                .andExpect(jsonPath("$[0].latitud").value(40.4))
                .andExpect(jsonPath("$[0].longitud").value(-3.7));
    }

    @Test
    void devuelveUnaListaVaciaCuandoNoHayCoincidencias() throws Exception {
        when(cliente.buscarAlojamientos("Hotel", "destino-prueba"))
                .thenReturn(new RespuestaLugaresGeoapifyDto(List.of()));

        mvc.perform(get("/api/alojamientos")
                        .param("texto", "Hotel")
                        .param("destinoId", "destino-prueba"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void noConsultaGeoapifyConMenosDeTresCaracteres() throws Exception {
        mvc.perform(get("/api/alojamientos")
                        .param("texto", "Ho")
                        .param("destinoId", "destino-prueba"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verifyNoInteractions(cliente);
    }

    @Test
    void devuelve400CuandoFaltaElDestino() throws Exception {
        mvc.perform(get("/api/alojamientos")
                        .param("texto", "Hotel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "Selecciona un destino válido para buscar alojamiento."));

        verifyNoInteractions(cliente);
    }

    @Test
    void devuelve503CuandoFallaElProveedor() throws Exception {
        when(cliente.buscarAlojamientos("Hotel", "destino-prueba"))
                .thenThrow(new IllegalStateException(
                        "No se ha podido consultar el servicio de alojamientos."));

        mvc.perform(get("/api/alojamientos")
                        .param("texto", "Hotel")
                        .param("destinoId", "destino-prueba"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(
                        "No se ha podido consultar el servicio de alojamientos."));
    }
}