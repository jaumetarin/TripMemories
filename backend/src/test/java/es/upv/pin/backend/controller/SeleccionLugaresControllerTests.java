package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.exception.CandidatoExceptionHandler;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SeleccionLugaresControllerTests {
    private final PreparacionCandidatosService preparacion = mock(PreparacionCandidatosService.class);
    private final CatalogoLugaresService servicio = new CatalogoLugaresService(preparacion,
            new ConfiguracionItinerarioService(new FechaHoraService(), new PreferenciasService()), new MuestraLugaresService());
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeleccionLugaresController(servicio))
            .setControllerAdvice(new CandidatoExceptionHandler()).build();
    private final String config = """
        {"destino":{"id":"madrid","nombre":"Madrid","latitud":40.4,"longitud":-3.7},
        "disponibilidad":{"llegada":"2026-12-01T10:00","salida":"2026-12-01T18:00"},
        "preferencias":{"intereses":["ARTE_MUSEOS"],"categoriasExcluidas":[],"subcategoriasExcluidas":[],
        "nivelGasto":"MODERADO","distanciaKm":3},"selecciones":[]}
        """;

    @Test void buscarYCambiarDevuelveUnEstadoUnicoYNoCacheable() throws Exception {
        var sesion = new MockHttpSession();
        when(preparacion.muestraInicial(any())).thenReturn(List.of(new LugarDto("prado", "Museo del Prado", "Madrid",
                40.4, -3.7, List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null)));
        mvc.perform(post("/api/lugares/buscar").session(sesion).contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuracion\":" + config + ",\"texto\":\"\",\"ampliar\":false}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.lugares[0].id").value("prado"));
        mvc.perform(post("/api/lugares/seleccion").session(sesion).contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuracion\":" + config + ",\"lugarId\":\"prado\",\"estado\":\"OBLIGATORIO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selecciones.length()").value(1))
                .andExpect(jsonPath("$.selecciones[0].estado").value("OBLIGATORIO"));
        verify(preparacion).muestraInicial(any());
    }

    @Test void rechazaEstadoDesconocidoYBusquedaCortaAntesDeProveedores() throws Exception {
        mvc.perform(post("/api/lugares/seleccion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuracion\":" + config + ",\"lugarId\":\"prado\",\"estado\":\"OTRO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isString());
        mvc.perform(post("/api/lugares/buscar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuracion\":" + config + ",\"texto\":\"ab\",\"ampliar\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(preparacion);
    }
}
