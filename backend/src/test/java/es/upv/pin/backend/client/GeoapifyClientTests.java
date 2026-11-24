package es.upv.pin.backend.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.DestinoGeoapifyDto;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpMethod;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;

class GeoapifyClientTests {

    @Test
    void laBusquedaDeAlojamientoUtilizaElLimitadorCompartido() {
        var llamadas = new AtomicInteger();
        var limitador = new LimiteConsultasGeoapify(3, 0) {
            @Override
            public <T> T ejecutar(Supplier<T> consulta) {
                llamadas.incrementAndGet();
                return super.ejecutar(consulta);
            }
        };
        var constructor = RestClient.builder().baseUrl("https://api.geoapify.com");
        var servidor = MockRestServiceServer.bindTo(constructor).build();
        var cliente = new GeoapifyClient(constructor.build(), "clave-de-prueba", limitador);
        servidor.expect(requestTo(urlAlojamientosDePrueba()))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));

        var respuesta = cliente.buscarAlojamientos("Hotel Centro", "destino-prueba");

        assertTrue(respuesta.features().isEmpty());
        assertEquals(1, llamadas.get());
        servidor.verify();
    }

    @Test
    void consultasHistoricasEspecificasNoOrdenanEnElProveedorPeroConservanCiudadYCategorias() {
        var builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoapifyClient(builder.build(), "clave-de-prueba");
        var destino = new DestinoDto("madrid", "Madrid", null, "España", 40.4, -3.7);
        for (var grupo : List.of(
                "tourism.sights.city_gate,tourism.sights.tower,tourism.sights.castle,tourism.sights.manor,tourism.sights.fort",
                "tourism.sights.building,tourism.sights.city_hall,tourism.sights.place_of_worship,tourism.sights.monastery,tourism.sights.archaeological_site")) {
            servidor.expect(requestTo("https://api.geoapify.com/v2/places?categories=" + grupo.replace(",", "%2C")
                    + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba"))
                    .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        }
        for (var grupo : List.of(
                "tourism.sights.city_gate,tourism.sights.tower,tourism.sights.castle,tourism.sights.manor,tourism.sights.fort",
                "tourism.sights.building,tourism.sights.city_hall,tourism.sights.place_of_worship,tourism.sights.monastery,tourism.sights.archaeological_site")) {
            cliente.buscarLugares(destino, grupo, 100, 0, "");
            cliente.buscarLugares(new DestinoDto("madrid", "Madrid", null, "España", 40.42, -3.71), grupo, 100, 0, "");
        }
        servidor.verify(); // Cambiar el centro no duplica una consulta que ya no lleva sesgo.
    }

    @Test
    void elSesgoDelDestinoFormaParteDeLaConsultaYDeLaCache() {
        var builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoapifyClient(builder.build(), "clave-de-prueba");
        servidor.expect(requestTo("https://api.geoapify.com/v2/places?categories=leisure.park&filter=place:madrid"
                + "&limit=100&lang=es&bias=proximity:-3.7,40.4&apiKey=clave-de-prueba"))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.geoapify.com/v2/places?categories=leisure.park&filter=place:madrid"
                + "&limit=100&lang=es&bias=proximity:-3.6,40.4&apiKey=clave-de-prueba"))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        var destino = new DestinoDto("madrid", "Madrid", null, "España", 40.4, -3.7);
        cliente.buscarLugares(destino, "leisure.park", 100, 0, "");
        cliente.buscarLugares(destino, "leisure.park", 100, 0, "");
        cliente.buscarLugares(new DestinoDto("madrid", "Madrid", null, "España", 40.4, -3.6), "leisure.park", 100, 0, "");
        servidor.verify();
    }

    @Test
    void buscarPorNombreMantieneDestinoYCategoriasYCodificaElTexto() {
        var builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoapifyClient(builder.build(), "prueba");
        servidor.expect(requestTo("https://api.geoapify.com/v2/places?categories=entertainment.museum"
                + "&filter=place:madrid&limit=30&lang=es&apiKey=prueba&name=Reina%20Sof%C3%ADa"))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(cliente.buscarLugares("madrid", "entertainment.museum", 30, 0, "Reina Sofía").features().isEmpty());
        servidor.verify();
    }

    @Test
    void interpretaLosDestinosDeGeoapifyComoRecords() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo("https://api.geoapify.com/v1/geocode/autocomplete"
                        + "?text=Madrid&type=city&lang=es&limit=5&format=json"
                        + "&apiKey=clave-de-prueba"))
                .andRespond(withSuccess("""
                        {
                          "query": {"text": "Madrid"},
                          "results": [
                            {
                              "place_id": "madrid-ejemplo",
                              "city": "Madrid",
                              "name": "Madrid",
                              "address_line1": "Madrid",
                              "state": "Comunidad de Madrid",
                              "country": "España",
                              "lat": 40.4168,
                              "lon": -3.7038,
                              "formatted": "Madrid, España"
                            },
                            {
                              "place_id": "otro-destino",
                              "city": "Otro destino"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<DestinoGeoapifyDto> destinos = cliente.autocompletar("Madrid");

        assertEquals(2, destinos.size());
        DestinoGeoapifyDto destino = destinos.get(0);
        assertEquals("madrid-ejemplo", destino.place_id());
        assertEquals("Madrid", destino.city());
        assertEquals("Madrid", destino.name());
        assertEquals("Madrid", destino.address_line1());
        assertEquals("Comunidad de Madrid", destino.state());
        assertEquals("España", destino.country());
        assertEquals(40.4168, destino.lat().doubleValue());
        assertEquals(-3.7038, destino.lon().doubleValue());
        assertNull(destinos.get(1).lat());
        assertNull(destinos.get(1).lon());
        servidor.verify();
    }

    @Test
    void conservaLosNombresDelJsonDeTripMemories() {
        DestinoDto destino = new DestinoDto(
                "madrid-ejemplo", "Madrid", "Comunidad de Madrid",
                "España", 40.4168, -3.7038);

        JsonMapper conversor = JsonMapper.builder().build();
        JsonNode json = conversor.readTree(conversor.writeValueAsString(destino));

        assertEquals(6, json.size());
        assertEquals("madrid-ejemplo", json.path("id").asString());
        assertEquals("Madrid", json.path("nombre").asString());
        assertEquals("Comunidad de Madrid", json.path("region").asString());
        assertEquals("España", json.path("pais").asString());
        assertEquals(40.4168, json.path("latitud").asDouble());
        assertEquals(-3.7038, json.path("longitud").asDouble());
    }
    @Test
    void interpretaLosLugaresDeGeoapify() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo("https://api.geoapify.com/v2/places"
                        + "?categories=entertainment.museum"
                        + "&filter=place:destino-prueba"
                        + "&limit=20"
                        + "&lang=es"
                        + "&apiKey=clave-de-prueba"))
                .andRespond(withSuccess("""
                        {
                        "type": "FeatureCollection",
                        "features": [
                                {
                                "type": "Feature",
                                "properties": {
                                "place_id": "museo-prueba",
                                "name": "Museo de prueba",
                                "lat": 40.4,
                                "lon": -3.7,
                                "categories": ["entertainment.museum"],
                                "formatted": "Dirección de prueba"
                                }
                                }
                        ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RespuestaLugaresGeoapifyDto respuesta = cliente.buscarLugares(
                "destino-prueba", "entertainment.museum", 20);

        assertEquals(1, respuesta.features().size());

        var lugar = respuesta.features().get(0).properties();

        assertEquals("museo-prueba", lugar.place_id());
        assertEquals("Museo de prueba", lugar.name());
        assertEquals(40.4, lugar.lat().doubleValue());
        assertEquals(-3.7, lugar.lon().doubleValue());
        assertEquals(List.of("entertainment.museum"), lugar.categories());
        assertEquals("Dirección de prueba", lugar.formatted());

        servidor.verify();
        }

        private String urlLugaresDePrueba() {
                return "https://api.geoapify.com/v2/places"
                        + "?categories=entertainment.museum"
                        + "&filter=place:destino-prueba"
                        + "&limit=20"
                        + "&lang=es"
                        + "&apiKey=clave-de-prueba";
        }
     @Test
     void aceptaUnaRespuestaSinLugares() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(urlLugaresDePrueba()))
                .andRespond(withSuccess("""
                        {
                        "type": "FeatureCollection",
                        "features": []
                        }
                        """, MediaType.APPLICATION_JSON));

        RespuestaLugaresGeoapifyDto respuesta = cliente.buscarLugares(
                "destino-prueba", "entertainment.museum", 20);

        assertTrue(respuesta.features().isEmpty());

        servidor.verify();
        }
    @Test
    void comunicaUnFalloDelProveedor() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(urlLugaresDePrueba()))
                .andRespond(withServerError());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> cliente.buscarLugares(
                        "destino-prueba", "entertainment.museum", 20));

        assertEquals(
                "No se ha podido consultar el servicio de lugares.",
                error.getMessage());

        servidor.verify();
        }
    @Test
    void buscaAlojamientosDentroDelDestinoYLeeSusCoordenadas() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(
                        "https://api.geoapify.com/v1/geocode/autocomplete"
                                + "?text=Hotel%20Centro"
                                + "&filter=place:destino-prueba"
                                + "&lang=es&limit=5&format=geojson"
                                + "&apiKey=clave-de-prueba"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "features": [
                            {
                              "type": "Feature",
                              "properties": {
                                "place_id": "hotel-prueba",
                                "name": "Hotel Centro",
                                "formatted": "Calle de Prueba 10, Madrid",
                                "lat": 40.4168,
                                "lon": -3.7038,
                                "result_type": "amenity"
                              },
                              "geometry": {
                                "type": "Point",
                                "coordinates": [-3.7038, 40.4168]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RespuestaLugaresGeoapifyDto respuesta =
                cliente.buscarAlojamientos(
                        "  Hotel Centro  ", "  destino-prueba  ");

        assertEquals(1, respuesta.features().size());

        var alojamiento = respuesta.features().getFirst().properties();

        assertEquals("hotel-prueba", alojamiento.place_id());
        assertEquals("Hotel Centro", alojamiento.name());
        assertEquals(
                "Calle de Prueba 10, Madrid",
                alojamiento.formatted());
        assertEquals(40.4168, alojamiento.lat().doubleValue());
        assertEquals(-3.7038, alojamiento.lon().doubleValue());
        assertEquals("amenity", alojamiento.result_type());

        servidor.verify();
    }
    private String urlAlojamientosDePrueba() {
        return "https://api.geoapify.com/v1/geocode/autocomplete"
                + "?text=Hotel%20Centro"
                + "&filter=place:destino-prueba"
                + "&lang=es&limit=5&format=geojson"
                + "&apiKey=clave-de-prueba";
    }
    @Test
    void aceptaBusquedaDeAlojamientoSinCoincidencias() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(urlAlojamientosDePrueba()))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "features": []
                        }
                        """, MediaType.APPLICATION_JSON));

        RespuestaLugaresGeoapifyDto respuesta =
                cliente.buscarAlojamientos("Hotel Centro", "destino-prueba");

        assertTrue(respuesta.features().isEmpty());

        servidor.verify();
    }

    @Test
    void comunicaUnFalloDelProveedorAlBuscarAlojamiento() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(urlAlojamientosDePrueba()))
                .andRespond(withServerError());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> cliente.buscarAlojamientos(
                        "Hotel Centro", "destino-prueba"));

        assertEquals(
                "No se ha podido consultar el servicio de alojamientos.",
                error.getMessage());

        servidor.verify();
    }

    @Test
    void rechazaUnaRespuestaDeAlojamientoSinFeatures() {
        RestClient.Builder constructor = RestClient.builder()
                .baseUrl("https://api.geoapify.com");

        MockRestServiceServer servidor = MockRestServiceServer
                .bindTo(constructor)
                .build();

        GeoapifyClient cliente = new GeoapifyClient(
                constructor.build(), "clave-de-prueba");

        servidor.expect(requestTo(urlAlojamientosDePrueba()))
                .andRespond(withSuccess(
                        "{}", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> cliente.buscarAlojamientos(
                        "Hotel Centro", "destino-prueba"));

        assertEquals(
                "El servicio de alojamientos ha devuelto una respuesta no válida.",
                error.getMessage());

        servidor.verify();
    }
     @Test
     void reintentaUnaVezElTimeoutYConservaLaMismaConsulta() {
        var builder = RestClient.builder()
                .baseUrl("https://api.geoapify.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoapifyClient(builder.build(), "clave-de-prueba");

        servidor.expect(requestTo(urlLugaresDePrueba()))
                .andRespond(request -> {
                    throw new java.net.http.HttpTimeoutException(
                            "URL y clave que no deben propagarse");
                });

        servidor.expect(requestTo(urlLugaresDePrueba()))
                .andRespond(withSuccess(
                        "{\"features\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(cliente.buscarLugares(
                "destino-prueba", "entertainment.museum", 20)
                .features().isEmpty());

        servidor.verify();
    }

    @Test
    void unTimeoutPersistenteTerminaTrasDosIntentosConUnMensajeCorregible() {
        var builder = RestClient.builder()
                .baseUrl("https://api.geoapify.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoapifyClient(builder.build(), "clave-de-prueba");

        servidor.expect(
                org.springframework.test.web.client.ExpectedCount.twice(),
                requestTo(urlLugaresDePrueba()))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("clave-de-prueba");
                });

        var error = assertThrows(
                IllegalStateException.class,
                () -> cliente.buscarLugares(
                        "destino-prueba", "entertainment.museum", 20));

        assertTrue(error.getMessage().contains("tardando demasiado"));
        assertFalse(error.getMessage().contains("clave-de-prueba"));

        servidor.verify();
    }

    @Test
    void noReintentaUnLimiteHttpDelProveedor() {
        var builder = RestClient.builder()
                .baseUrl("https://api.geoapify.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoapifyClient(builder.build(), "clave-de-prueba");

        servidor.expect(requestTo(urlLugaresDePrueba()))
                .andRespond(
                        org.springframework.test.web.client.response
                                .MockRestResponseCreators.withStatus(
                                        org.springframework.http.HttpStatus
                                                .TOO_MANY_REQUESTS));

        assertThrows(
                IllegalStateException.class,
                () -> cliente.buscarLugares(
                        "destino-prueba", "entertainment.museum", 20));

        servidor.verify();
    }
}
