package es.upv.pin.backend.service;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.PreferenciasDto;
import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static es.upv.pin.backend.model.SubcategoriaTuristica.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class RecomendacionComidaServiceTests {
 private MockRestServiceServer servidor;
 private RecomendacionComidaService servicio;
 private static final String URL = "https://api.geoapify.com/v2/places?categories=catering.restaurant&filter=place:madrid&limit=100&lang=es&apiKey=clave";
 private PreferenciasDto preferencias() { return new PreferenciasDto(List.of(GASTRONOMIA), List.of(), List.of(COMIDA_RAPIDA), null, null); }
 @BeforeEach void preparar() {
  var builder = RestClient.builder().baseUrl("https://api.geoapify.com");
  servidor = MockRestServiceServer.bindTo(builder).build();
  servicio = new RecomendacionComidaService(new GeoapifyClient(builder.build(), "clave"), new ClasificacionLugaresService());
 }
 @Test void sinInteresGastronomicoTambienBuscaRestaurantes() {
  servidor.expect(requestTo(URL)).andRespond(withSuccess("""
   {"features":[{"properties":{"place_id":"r","name":"Restaurante","lat":40.4,"lon":-3.7}}]}
   """, MediaType.APPLICATION_JSON));
  var r = servicio.buscar("madrid", new PreferenciasDto(List.of(ARTE_MUSEOS), List.of(), List.of(), null, null), Set.of());
  assertEquals("r", r.opciones().getFirst().id()); assertTrue(r.advertencias().isEmpty()); servidor.verify();
 }
 @Test void exclusionDeGastronomiaImpideConsulta() {
  var r = servicio.buscar("madrid", new PreferenciasDto(List.of(ARTE_MUSEOS), List.of(GASTRONOMIA), List.of(), null, null), Set.of());
  assertTrue(r.opciones().isEmpty()); servidor.verify();
 }
 @Test void priorizaRestaurantesCercaDeLasVisitasAunqueElProveedorLosDevuelvaAlFinal() {
  var lejanos = java.util.stream.IntStream.range(0, 12).mapToObj(i ->
    "{\"properties\":{\"place_id\":\"lejano" + i + "\",\"name\":\"Lejano " + i + "\",\"lat\":40.6,\"lon\":-3.9}}")
    .collect(java.util.stream.Collectors.joining(","));
  servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"features\":[" + lejanos
    + ",{\"properties\":{\"place_id\":\"cercano\",\"name\":\"Cercano\",\"lat\":40.4001,\"lon\":-3.7001}}]}", MediaType.APPLICATION_JSON));
  var visita = new es.upv.pin.backend.dto.LugarDto("v", "Museo", null, 40.4, -3.7, List.of(ARTE_MUSEOS), List.of(), null);
  var r = servicio.buscar("madrid", preferencias(), Set.of(), List.of(visita));
  assertEquals(10, r.opciones().size()); assertEquals("cercano", r.opciones().getFirst().id()); servidor.verify();
 }
 @Test void normalizaFiltraYDeduplica() {
  servidor.expect(requestTo(URL)).andRespond(withSuccess("""
   {"features":[
    {"properties":{"place_id":" a ","name":" Restaurante ","lat":40.4,"lon":-3.7,"formatted":" Calle ","categories":["catering.restaurant"]}},
    {"properties":{"place_id":"a","name":"Duplicado","lat":40.4,"lon":-3.7}},
    {"properties":{"place_id":"b","name":"Fuera","lat":91,"lon":0}},
    {"properties":{"place_id":"c","name":"Sin nombre","lat":0,"lon":0}},
    {"properties":{"place_id":"d","name":"Excluido","lat":0,"lon":0}},
    {"properties":{"place_id":"e","name":"Rapida","lat":0,"lon":0,"categories":["catering.fast_food"]}},
    {"properties":{"place_id":"f","name":"Copia","lat":0,"lon":0}},
    {"properties":{"place_id":"f","name":"Copia","lat":0,"lon":0,"categories":["catering.fast_food"]}}
   ]}
   """, MediaType.APPLICATION_JSON));
  var r = servicio.buscar("madrid", preferencias(), Set.of("d"));
  assertEquals(1, r.opciones().size()); assertEquals("a", r.opciones().getFirst().id());
  assertEquals("Restaurante", r.opciones().getFirst().nombre()); assertEquals("Calle", r.opciones().getFirst().direccion());
  assertTrue(r.advertencias().isEmpty()); servidor.verify();
 }
 @Test void falloProduceListaVaciaYAdvertencia() {
  servidor.expect(requestTo(URL)).andRespond(withServerError());
  var r = servicio.buscar("madrid", preferencias(), Set.of());
  assertTrue(r.opciones().isEmpty()); assertFalse(r.advertencias().isEmpty()); servidor.verify();
 }
}
