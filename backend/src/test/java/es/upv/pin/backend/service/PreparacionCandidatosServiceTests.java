package es.upv.pin.backend.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static es.upv.pin.backend.model.SubcategoriaTuristica.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PreparacionCandidatosServiceTests {
    private MockRestServiceServer proveedor;
    private PreparacionCandidatosService servicio;
    private final ClasificacionLugaresService clasificacion = new ClasificacionLugaresService();

    @BeforeEach
    void preparar() {
        var constructor = RestClient.builder().baseUrl("https://api.geoapify.com");
        proveedor = MockRestServiceServer.bindTo(constructor).build();
        servicio = new PreparacionCandidatosService(new GeoapifyClient(constructor.build(), "clave-de-prueba"),
                new ConfiguracionItinerarioService(new FechaHoraService(), new PreferenciasService()), clasificacion);
    }

    private ConfiguracionItinerarioDto configuracion(String intereses, String exclusiones) {
        return JsonMapper.builder().build().readValue("""
                {"destino":{"id":"madrid","nombre":"Madrid","region":"Madrid","pais":"España","latitud":40.4,"longitud":-3.7},
                 "disponibilidad":{"llegada":"2026-11-10T10:00","salida":"2026-11-12T18:00"},"alojamiento":"Hotel",
                 "preferencias":{"intereses":[%s],"categoriasExcluidas":[],"subcategoriasExcluidas":[%s],
                  "nivelGasto":"ECONOMICO","distanciaKm":3}}
                """.formatted(intereses, exclusiones), ConfiguracionItinerarioDto.class);
    }

    private String url(CategoriaTuristica categoria) {
        return "https://api.geoapify.com/v2/places?categories=" + clasificacion.categoriasConsulta(categoria).replace(",", "%2C")
                + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba";
    }

    private String lugares(String categoria) {
        return """
                {"features":[{"properties":{"place_id":"a","name":"Lugar A","formatted":"Calle A, Madrid",
                  "categories":["%s"]}}]}
                """.formatted(categoria);
    }

    @Test
    void consultaUnaVezCadaInteresConLimiteCien() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess(lugares("catering.restaurant"), MediaType.APPLICATION_JSON));
        proveedor.expect(requestTo(url(NATURALEZA_PARQUES))).andRespond(withSuccess(lugares("leisure.park"), MediaType.APPLICATION_JSON));
        var resultado = servicio.preparar(configuracion("\"GASTRONOMIA\",\"NATURALEZA_PARQUES\",\"GASTRONOMIA\"", ""));
        assertEquals(List.of(new LugarDto("Lugar A", "Calle A, Madrid", null)), resultado);
        proveedor.verify();
    }

    @Test
    void excluyeUnaSubcategoriaSinEliminarElRestoDelInteres() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Restaurante","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":"Comida rápida","categories":["catering.fast_food.burger"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(List.of(new LugarDto("Restaurante", null, null)),
                servicio.preparar(configuracion("\"GASTRONOMIA\"", "\"COMIDA_RAPIDA\"")));
        proveedor.verify();
    }

    @Test
    void unaCopiaExcluidaDescartaElLugarCompletoAunqueOtraNoTraigaEsaEtiqueta() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Local","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"a","name":"Local","categories":["catering.fast_food"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertTrue(servicio.preparar(configuracion("\"GASTRONOMIA\"", "\"COMIDA_RAPIDA\"")).isEmpty());
        proveedor.verify();
    }

    @Test
    void reconoceExclusionesEnLasEtiquetasDeOrigenSinDevolverlas() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("""
                {"features":[{"properties":{"place_id":"a","name":"Mercado","categories":["catering.restaurant"],
                 "datasource":{"raw":{"amenity":"marketplace","marketplace":"food"}}}}]}
                """, MediaType.APPLICATION_JSON));
        assertTrue(servicio.preparar(configuracion("\"GASTRONOMIA\"", "\"MERCADOS_GASTRONOMICOS\"")).isEmpty());
        proveedor.verify();
    }

    @Test
    void conservaEstablecimientosDistintosQueTienenElMismoNombre() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Local","formatted":"Calle A","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":"Local","formatted":"Calle B","categories":["catering.restaurant"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(2, servicio.preparar(configuracion("\"GASTRONOMIA\"", "")).size());
        proveedor.verify();
    }

    @Test
    void descartaNombresAusentesVaciosODesconocidosYRecuperaElNombreDeOtraCopia() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":null,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"c","name":"   ","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"d","name":" DESCONOCIDO ","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"e","name":"Sin nombre","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"f","name":"unknown","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"g","name":"Unnamed","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"h","name":"Nombre desconocido","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"i","name":"n/a","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"j","name":"unknown","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"j","name":"  Restaurante con nombre  ","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"k","name":"La Desconocida","categories":["catering.restaurant"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(List.of(new LugarDto("Restaurante con nombre", null, null),
                        new LugarDto("La Desconocida", null, null)),
                servicio.preparar(configuracion("\"GASTRONOMIA\"", "")));
        proveedor.verify();
    }

    @Test
    void rescataElHorarioDisponibleSinInventarloYLoConservaAlAgrupar() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Directo","opening_hours":"  Mo-Fr 10:00-18:00  ",
                  "categories":["catering.restaurant"],"datasource":{"raw":{"opening_hours":"24/7"}}}},
                 {"properties":{"place_id":"b","name":"Origen","opening_hours":" ",
                  "categories":["catering.restaurant"],"datasource":{"raw":{"opening_hours":"Sa-Su 09:00-14:00"}}}},
                 {"properties":{"place_id":"c","name":"Sin horario","opening_hours":" ",
                  "categories":["catering.restaurant"],"datasource":{"raw":{"opening_hours":"  "}}}},
                 {"properties":{"place_id":"d","name":"Otra copia","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"d","name":"Otra copia","opening_hours":"24/7","categories":["catering.restaurant"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(List.of(new LugarDto("Directo", null, "Mo-Fr 10:00-18:00"),
                        new LugarDto("Origen", null, "Sa-Su 09:00-14:00"),
                        new LugarDto("Sin horario", null, null), new LugarDto("Otra copia", null, "24/7")),
                servicio.preparar(configuracion("\"GASTRONOMIA\"", "")));
        proveedor.verify();
    }

    @Test
    void sinInteresesConsultaLasCategoriasGenerales() {
        for (CategoriaTuristica categoria : CategoriaTuristica.values()) {
            proveedor.expect(requestTo(url(categoria))).andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        }
        assertTrue(servicio.preparar(configuracion("", "")).isEmpty());
        proveedor.verify();
    }

    @Test
    void omiteCategoriasCompletasExcluidasYFiltraLasEtiquetasCruzadas() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("""
                {"features":[{"properties":{"place_id":"a","name":"Local","categories":["catering.restaurant","catering.bar"]}}]}
                """, MediaType.APPLICATION_JSON));
        var base = configuracion("", "");
        var preferencias = new PreferenciasDto(List.of(),
                List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES, CULTURA_LOCAL, OCIO_ENTRETENIMIENTO),
                List.of(), base.preferencias().nivelGasto(), base.preferencias().distanciaKm());
        assertTrue(servicio.preparar(new ConfiguracionItinerarioDto(base.destino(), base.disponibilidad(), "", preferencias)).isEmpty());
        proveedor.verify();
    }

    @Test
    void cambiarUnaExclusionUtilizaLosValoresActuales() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess(lugares("catering.fast_food"), MediaType.APPLICATION_JSON));
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess(lugares("catering.fast_food"), MediaType.APPLICATION_JSON));
        assertEquals(1, servicio.preparar(configuracion("\"GASTRONOMIA\"", "")).size());
        assertTrue(servicio.preparar(configuracion("\"GASTRONOMIA\"", "\"COMIDA_RAPIDA\"")).isEmpty());
        proveedor.verify();
    }

    @Test
    void unaRespuestaInvalidaOUnFalloNoSeConfundenConUnaListaVacia() {
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withSuccess("{\"features\":[{}]}", MediaType.APPLICATION_JSON));
        proveedor.expect(requestTo(url(GASTRONOMIA))).andRespond(withServerError());
        assertThrows(IllegalStateException.class, () -> servicio.preparar(configuracion("\"GASTRONOMIA\"", "")));
        assertThrows(IllegalStateException.class, () -> servicio.preparar(configuracion("\"GASTRONOMIA\"", "")));
        proveedor.verify();
    }

    @Test
    void validaLaConfiguracionAntesDeConsultar() {
        assertThrows(IllegalArgumentException.class, () -> servicio.preparar(null));
        proveedor.verify();
    }
}
