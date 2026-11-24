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
        proveedor = MockRestServiceServer.bindTo(constructor).ignoreExpectOrder(true).build();
        servicio = new PreparacionCandidatosService(new GeoapifyClient(constructor.build(), "clave-de-prueba"),
                new ConfiguracionItinerarioService(new FechaHoraService(java.time.Clock.fixed(java.time.Instant.parse("2026-01-01T12:00:00Z"), java.time.ZoneOffset.UTC)), new PreferenciasService()), clasificacion);
    }

    private ConfiguracionItinerarioDto configuracion(String intereses, String exclusiones) {
        return JsonMapper.builder().build().readValue("""
                {"destino":{"id":"madrid","nombre":"Madrid","region":"Madrid","pais":"España","latitud":40.4,"longitud":-3.7},
                 "disponibilidad":{"llegada":"2026-11-10T10:00","salida":"2026-11-12T18:00"},"alojamiento":"Hotel",
                "alojamientoLocalizado":{
                "id":"hotel-prueba",
                "nombre":"Hotel",
                "direccion":"Calle de Prueba 10",
                "latitud":40.4168,
                "longitud":-3.7038
                },
                 "preferencias":{"intereses":[%s],"categoriasExcluidas":[],"subcategoriasExcluidas":[%s],
                  "nivelGasto":"ECONOMICO","distanciaKm":3}}
                """.formatted(intereses, exclusiones), ConfiguracionItinerarioDto.class);
    }

    private org.springframework.test.web.client.ResponseActions esperar(CategoriaTuristica categoria) {
        var principal = proveedor.expect(requestTo(url(categoria)));
        List<String> consultas = clasificacion.consultasPorInteres(categoria);
        for (String consulta : consultas.subList(1, consultas.size())) {
            proveedor.expect(org.springframework.test.web.client.ExpectedCount.between(0, 1), requestTo(
                    "https://api.geoapify.com/v2/places?categories=" + consulta.replace(",", "%2C")
                    + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba"))
                    .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        }
        return principal;
    }

    private String url(CategoriaTuristica categoria) {
        return "https://api.geoapify.com/v2/places?categories=" + clasificacion.consultasPorInteres(categoria).getFirst().replace(",", "%2C")
                + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba";
    }

    private String lugares(String categoria) {
        return """
                {"features":[{"properties":{"place_id":"a","name":"Lugar A","formatted":"Calle A, Madrid","lat":40.4,"lon":-3.7,
                  "categories":["%s"]}}]}
                """.formatted(categoria);
    }

    private void responderGrupos(CategoriaTuristica categoria, List<String> respuestas) {
        List<String> consultas = clasificacion.consultasPorInteres(categoria);
        for (int i = 0; i < consultas.size(); i++) {
            proveedor.expect(requestTo("https://api.geoapify.com/v2/places?categories="
                    + consultas.get(i).replace(",", "%2C")
                    + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba"))
                    .andRespond(withSuccess(i < respuestas.size() ? respuestas.get(i) : "{\"features\":[]}", MediaType.APPLICATION_JSON));
        }
    }

    private String grupoGenerado(String prefijo, int cantidad, String categoria) {
        return java.util.stream.IntStream.range(0, cantidad).mapToObj(i ->
                "{\"properties\":{\"place_id\":\"" + prefijo + i + "\",\"name\":\"" + prefijo + i
                + "\",\"categories\":[\"" + categoria + "\"]}}")
                .collect(java.util.stream.Collectors.joining(",", "{\"features\":[", "]}"));
    }

    @Test
    void agrupaElParqueYElLaberintoConDistintosIdsSinPerderDatosNiFusionarOtrosJardines() {
        responderGrupos(NATURALEZA_PARQUES, List.of("""
                {"features":[
                 {"properties":{"place_id":"parque","name":"Parc del Laberint d'Horta","lat":41.439,"lon":2.145,"categories":["leisure.park"]}},
                 {"properties":{"place_id":"laberinto","name":"Laberinto de Horta","lat":41.440,"lon":2.146,
                    "formatted":"Passeig dels Castanyers, Barcelona","opening_hours":"Mo-Su 10:00-18:00","categories":["tourism.attraction"]}},
                 {"properties":{"place_id":"otro","name":"Jardines del Palau","lat":41.439,"lon":2.145,"categories":["leisure.park"]}}]}
                """, "{\"features\":[]}", "{\"features\":[]}"));
        var resultado = servicio.preparar(configuracion("\"NATURALEZA_PARQUES\"", ""));
        assertEquals(2, resultado.size());
        var parque = resultado.stream().filter(l -> l.id().equals("parque")).findFirst().orElseThrow();
        assertEquals("Mo-Su 10:00-18:00", parque.horario());
        assertEquals("Passeig dels Castanyers, Barcelona", parque.direccion());
        assertTrue(parque.categorias().contains(CULTURA_LOCAL));
        assertTrue(resultado.stream().anyMatch(l -> l.id().equals("otro")));
        proveedor.verify();
    }

    @Test
    void lasExclusionesDeUnaCopiaSeAplicanTambienAlAliasConOtroIdentificador() {
        responderGrupos(NATURALEZA_PARQUES, List.of("""
                {"features":[
                 {"properties":{"place_id":"parque","name":"Parque del Laberinto de Horta","lat":41.439,"lon":2.145,"categories":["leisure.park"]}},
                 {"properties":{"place_id":"laberinto","name":"Laberinto de Horta","lat":41.440,"lon":2.146,
                  "categories":["leisure.park"],"datasource":{"raw":{"route":"hiking"}}}}]}
                """, "{\"features\":[]}", "{\"features\":[]}"));
        assertTrue(servicio.preparar(configuracion("\"NATURALEZA_PARQUES\"", "\"SENDERISMO\"")).isEmpty());
        proveedor.verify();
    }

    @Test
    void horariosDistintosEntreAliasNoSeTransformanEnUnaDisponibilidadSegura() {
        responderGrupos(NATURALEZA_PARQUES, List.of("""
                {"features":[
                 {"properties":{"place_id":"parque","name":"Parque del Laberinto de Horta","lat":41.439,"lon":2.145,
                  "categories":["leisure.park"],"opening_hours":"Mo-Su 09:00-18:00"}},
                 {"properties":{"place_id":"laberinto","name":"Laberinto de Horta","lat":41.440,"lon":2.146,
                  "categories":["leisure.park"],"opening_hours":"Tu-Su 10:00-20:00"}}]}
                """, "{\"features\":[]}", "{\"features\":[]}"));
        var resultado = servicio.preparar(configuracion("\"NATURALEZA_PARQUES\"", ""));
        assertEquals(1, resultado.size());
        assertNull(resultado.getFirst().horario());
        proveedor.verify();
    }

    @Test
    void ocioIncluyeComprasJuegosYAnimalesPeroNoCinesCafesOBaresAunqueTenganEtiquetasCruzadas() {
        responderGrupos(OCIO_ENTRETENIMIENTO, List.of("""
                {"features":[
                 {"properties":{"place_id":"mall","name":"Centro comercial","categories":["commercial.shopping_mall"]}},
                 {"properties":{"place_id":"cine","name":"Cine del centro","categories":["commercial.shopping_mall","entertainment.cinema"]}}]}
                """, """
                {"features":[
                 {"properties":{"place_id":"bolera","name":"Bolera","categories":["entertainment.bowling_alley"]}},
                 {"properties":{"place_id":"juegos","name":"Recreativos","categories":["entertainment.amusement_arcade"]}},
                 {"properties":{"place_id":"bar","name":"Bar con juegos","categories":["entertainment.amusement_arcade","catering.bar"]}},
                 {"properties":{"place_id":"cafe","name":"Café con juegos","categories":["entertainment.activity_park"],"datasource":{"raw":{"amenity":"cafe"}}}}]}
                """, """
                {"features":[
                 {"properties":{"place_id":"zoo","name":"Parque de animales","categories":["entertainment.zoo"]}},
                 {"properties":{"place_id":"acuario","name":"Acuario","categories":["entertainment.aquarium"]}},
                 {"properties":{"place_id":"tematico","name":"Parque temático","categories":["entertainment.theme_park"]}}]}
                """, "{\"features\":[]}", """
                {"features":[{"properties":{"place_id":"almacenes","name":"Grandes almacenes","categories":["commercial.department_store"]}}]}
                """, "{\"features\":[]}"));
        var resultado = servicio.preparar(configuracion("\"OCIO_ENTRETENIMIENTO\"", ""));
        assertEquals(java.util.Set.of("mall", "bolera", "juegos", "zoo", "acuario", "tematico", "almacenes"),
                resultado.stream().map(LugarDto::id).collect(java.util.stream.Collectors.toSet()));
        proveedor.verify();
    }

    @Test
    void excluirComprasEvitaSusConsultasYConservaElRestoDeOcio() {
        for (String consulta : clasificacion.consultasPorInteres(OCIO_ENTRETENIMIENTO)) {
            if (consulta.startsWith("commercial.")) continue;
            proveedor.expect(requestTo("https://api.geoapify.com/v2/places?categories=" + consulta.replace(",", "%2C")
                    + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba"))
                    .andRespond(withSuccess(lugares("entertainment.zoo"), MediaType.APPLICATION_JSON));
        }
        assertEquals(1, servicio.preparar(configuracion("\"OCIO_ENTRETENIMIENTO\"", "\"COMPRAS\"")).size());
        proveedor.verify();
    }

    @Test
    void convierteLosComerciosEnUnaCalleVerificadaSinProgramarCadaTiendaNiCopiarSusHorarios() {
        responderGrupos(OCIO_ENTRETENIMIENTO, List.of("{\"features\":[]}", "{\"features\":[]}", "{\"features\":[]}", """
                {"features":[
                 {"properties":{"place_id":"t1","name":"Tienda 1","lat":40.42,"lon":-3.706,"street":"Gran Vía","housenumber":"1","categories":["commercial.clothing"],"opening_hours":"Mo-Fr 10:00-20:00"}},
                 {"properties":{"place_id":"t2","name":"Tienda 2","lat":40.42,"lon":-3.704,"street":"Gran Vía","housenumber":"3","categories":["commercial.clothing"]}},
                 {"properties":{"place_id":"t3","name":"Tienda 3","lat":40.42,"lon":-3.702,"street":"Gran Vía","housenumber":"9","categories":["commercial.clothing"]}}]}
                """, "{\"features\":[]}", "{\"features\":[]}"));
        proveedor.expect(peticion -> {
            assertEquals("/v1/geocode/search", peticion.getURI().getPath());
            String parametros = java.net.URLDecoder.decode(peticion.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(parametros.contains("text=Gran Vía, Madrid"));
            assertTrue(parametros.contains("type=street"));
            assertTrue(parametros.contains("filter=place:madrid"));
        }).andRespond(withSuccess("""
                {"features":[{"properties":{"place_id":"gran-via","street":"Gran Vía","lat":40.42,"lon":-3.704,
                 "formatted":"Gran Vía, Madrid","result_type":"street"}}]}
                """, MediaType.APPLICATION_JSON));
        var resultado = servicio.preparar(configuracion("\"OCIO_ENTRETENIMIENTO\"", ""));
        assertEquals(1, resultado.size());
        assertEquals("gran-via", resultado.getFirst().id());
        assertEquals("Gran Vía", resultado.getFirst().nombre());
        assertTrue(resultado.getFirst().subcategorias().contains(COMPRAS));
        assertNull(resultado.getFirst().horario());
        proveedor.verify();
    }

    @Test
    void losMercadosYEdificiosSecundariosNoDesplazanLosReferentesDeLaMuestraDeIa() {
        String palacios = grupoGenerado("palacio", 60, "tourism.sights.manor");
        String respaldo = palacios.substring(0, palacios.length() - 2) + """
                ,{"properties":{"place_id":"conjunto","name":"Conjunto arquitectónico","categories":["tourism.attraction"]}},
                 {"properties":{"place_id":"plaza","name":"Plaza principal","categories":["tourism.sights.square"]}}]}
                """;
        responderGrupos(HISTORIA_ARQUITECTURA, List.of(palacios,
                grupoGenerado("edificio", 100, "tourism.sights.building"), respaldo));
        proveedor.expect(requestTo(url(CULTURA_LOCAL))).andRespond(withSuccess(
                grupoGenerado("mercado", 100, "commercial.marketplace"), MediaType.APPLICATION_JSON));

        var muestra = servicio.preparar(configuracion("\"HISTORIA_ARQUITECTURA\",\"CULTURA_LOCAL\"", ""))
                .stream().limit(120).map(LugarDto::id).toList();

        assertTrue(muestra.contains("conjunto"));
        assertTrue(muestra.contains("plaza"));
        assertTrue(muestra.stream().anyMatch(id -> id.startsWith("mercado")));
        proveedor.verify();
    }

    @Test
    void recuperaReferentesDeValenciaPorSusTiposSinListasDeNombresEnElServicio() {
        responderGrupos(HISTORIA_ARQUITECTURA, List.of("""
                {"features":[
                 {"properties":{"place_id":"serrans","name":"Torres dels Serrans","categories":["tourism.sights.city_gate"]}},
                 {"properties":{"place_id":"quart","name":"Torres de Quart","categories":["tourism.sights.city_gate"]}}]}
                """, "{\"features\":[]}", """
                {"features":[
                 {"properties":{"place_id":"artes","name":"Ciutat de les Arts i les Ciències","categories":["tourism.attraction"]}},
                 {"properties":{"place_id":"plaza","name":"Plaça de l'Ajuntament","categories":["tourism.sights.square"]}},
                 {"properties":{"place_id":"toros","name":"Plaça de Bous de València","categories":["sport.stadium","tourism.attraction"]}}]}
                """));
        var resultado = servicio.preparar(configuracion("\"HISTORIA_ARQUITECTURA\"", ""));
        assertEquals(java.util.Set.of("serrans", "quart", "artes", "plaza", "toros"),
                resultado.stream().map(LugarDto::id).collect(java.util.stream.Collectors.toSet()));
        proveedor.verify();
    }

    @Test
    void unaListaLargaDeMuseosNoDesplazaGaleriasYLasCopiasNoDuplicanElResultado() {
        String museos = java.util.stream.IntStream.range(0, 100).mapToObj(i ->
                "{\"properties\":{\"place_id\":\"m" + i + "\",\"name\":\"Museo " + i
                + "\",\"categories\":[\"entertainment.museum\"]}}")
                .collect(java.util.stream.Collectors.joining(",", "{\"features\":[", "]}"));
        responderGrupos(ARTE_MUSEOS, List.of(museos, """
                {"features":[
                 {"properties":{"place_id":"galeria","name":"Galería","categories":["entertainment.culture.gallery"]}},
                 {"properties":{"place_id":"m0","name":"Museo 0","categories":["entertainment.culture.gallery"]}}]}
                """, "{\"features\":[]}"));
        var resultado = servicio.preparar(configuracion("\"ARTE_MUSEOS\"", ""));
        assertEquals(101, resultado.size());
        assertEquals(List.of("m0", "galeria"), resultado.stream().limit(2).map(LugarDto::id).toList());
        assertEquals(1, resultado.stream().filter(l -> l.id().equals("m0")).count());
        proveedor.verify();
    }

    @Test
    void historiaYCulturaLocalReutilizanConsultasCompartidasYSiguenRespetandoExclusiones() {
        responderGrupos(HISTORIA_ARQUITECTURA, List.of("""
                {"features":[{"properties":{"place_id":"puerta","name":"Puerta histórica",
                 "categories":["tourism.sights.city_gate"]}}]}
                """, "{\"features\":[]}", """
                {"features":[{"properties":{"place_id":"castillo","name":"Castillo",
                 "categories":["tourism.sights.castle"]}}]}
                """));
        proveedor.expect(requestTo(url(CULTURA_LOCAL))).andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        var resultado = servicio.preparar(configuracion("\"HISTORIA_ARQUITECTURA\",\"CULTURA_LOCAL\"", "\"CASTILLOS_PALACIOS\""));
        assertEquals(List.of("puerta"), resultado.stream().map(LugarDto::id).toList());
        proveedor.verify();
    }

    @Test
    void paginaElRespaldoYPriorizaAtraccionesGeneralesAntesDeRecortarLaMuestra() {
        String memoriales = java.util.stream.IntStream.range(0, 100).mapToObj(i ->
                "{\"properties\":{\"place_id\":\"memorial" + i + "\",\"name\":\"Memorial " + i
                + "\",\"categories\":[\"tourism.sights.memorial\"]}}")
                .collect(java.util.stream.Collectors.joining(",", "{\"features\":[", "]}"));
        responderGrupos(HISTORIA_ARQUITECTURA, List.of("{\"features\":[]}", "{\"features\":[]}", memoriales));
        String consultaGeneral = clasificacion.consultasPorInteres(HISTORIA_ARQUITECTURA).get(2);
        proveedor.expect(requestTo("https://api.geoapify.com/v2/places?categories=" + consultaGeneral.replace(",", "%2C")
                + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba&offset=100"))
                .andRespond(withSuccess("""
                        {"features":[{"properties":{"place_id":"conjunto","name":"Conjunto arquitectónico",
                         "categories":["tourism.attraction"]}}]}
                        """, MediaType.APPLICATION_JSON));
        var resultado = servicio.preparar(configuracion("\"HISTORIA_ARQUITECTURA\"", ""));
        assertEquals(101, resultado.size());
        assertEquals("conjunto", resultado.getFirst().id());
        proveedor.verify();
    }

    @Test
    void noConvierteCualquierEstadioOParqueDeAtraccionesEnPatrimonio() {
        responderGrupos(HISTORIA_ARQUITECTURA, List.of("""
                {"features":[
                 {"properties":{"place_id":"estadio","name":"Campo de fútbol","categories":["sport.stadium"]}},
                 {"properties":{"place_id":"arena","name":"Arena turística","categories":["sport.stadium","tourism.attraction"]}}]}
                """, "{\"features\":[]}", """
                {"features":[{"properties":{"place_id":"parque","name":"Parque de atracciones",
                 "categories":["entertainment.theme_park","tourism.attraction"]}}]}
                """));
        assertEquals(List.of("arena"), servicio.preparar(configuracion("\"HISTORIA_ARQUITECTURA\"", ""))
                .stream().map(LugarDto::id).toList());
        proveedor.verify();
    }

    @Test
    void detieneLaPaginacionTrasCincoPaginasAunqueTodasEstenLlenas() {
        List<String> paginas = new java.util.ArrayList<>();
        for (int pagina = 0; pagina < 5; pagina++) {
            final int indice = pagina;
            paginas.add(java.util.stream.IntStream.range(0, 100).mapToObj(i ->
                    "{\"properties\":{\"place_id\":\"p" + indice + "-" + i + "\",\"name\":\"Atracción " + i
                    + "\",\"categories\":[\"tourism.attraction\"]}}")
                    .collect(java.util.stream.Collectors.joining(",", "{\"features\":[", "]}")));
        }
        responderGrupos(HISTORIA_ARQUITECTURA, List.of("{\"features\":[]}", "{\"features\":[]}", paginas.getFirst()));
        String consulta = clasificacion.consultasPorInteres(HISTORIA_ARQUITECTURA).get(2).replace(",", "%2C");
        for (int pagina = 1; pagina < 5; pagina++) {
            proveedor.expect(requestTo("https://api.geoapify.com/v2/places?categories=" + consulta
                    + "&filter=place:madrid&limit=100&lang=es&apiKey=clave-de-prueba&offset=" + pagina * 100))
                    .andRespond(withSuccess(paginas.get(pagina), MediaType.APPLICATION_JSON));
        }
        assertEquals(500, servicio.preparar(configuracion("\"HISTORIA_ARQUITECTURA\"", "")).size());
        proveedor.verify();
    }

    @Test
    void consultaTresGruposPorInteresSinRepetirInteresesYConLimiteCien() {
        esperar(GASTRONOMIA).andRespond(withSuccess(lugares("catering.restaurant"), MediaType.APPLICATION_JSON));
        esperar(NATURALEZA_PARQUES).andRespond(withSuccess(lugares("leisure.park"), MediaType.APPLICATION_JSON));
        var resultado = servicio.preparar(configuracion("\"GASTRONOMIA\",\"NATURALEZA_PARQUES\",\"GASTRONOMIA\"", ""));
        assertEquals(List.of(new LugarDto("a", "Lugar A", "Calle A, Madrid", 40.4, -3.7,
                List.of(GASTRONOMIA, NATURALEZA_PARQUES), List.of(PARQUES_JARDINES), null)), resultado);
        proveedor.verify();
    }

    @Test
    void excluyeUnaSubcategoriaSinEliminarElRestoDelInteres() {
        esperar(GASTRONOMIA).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Restaurante","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":"Comida rápida","categories":["catering.fast_food.burger"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(List.of(new LugarDto("a", "Restaurante", null, null, null,
                        List.of(GASTRONOMIA), List.of(), null)),
                servicio.preparar(configuracion("\"GASTRONOMIA\"", "\"COMIDA_RAPIDA\"")));
        proveedor.verify();
    }

    @Test
    void unaCopiaExcluidaDescartaElLugarCompletoAunqueOtraNoTraigaEsaEtiqueta() {
        esperar(GASTRONOMIA).andRespond(withSuccess("""
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
        esperar(GASTRONOMIA).andRespond(withSuccess("""
                {"features":[{"properties":{"place_id":"a","name":"Mercado","categories":["catering.restaurant"],
                 "datasource":{"raw":{"amenity":"marketplace","marketplace":"food"}}}}]}
                """, MediaType.APPLICATION_JSON));
        assertTrue(servicio.preparar(configuracion("\"GASTRONOMIA\"", "\"MERCADOS_GASTRONOMICOS\"")).isEmpty());
        proveedor.verify();
    }

    @Test
    void culturaLocalConsultaMercadosYLosConservaSinEtiquetasDeOrigen() {
        // La URL es explícita para detectar que se deje de consultar esta categoría.
        esperar(CULTURA_LOCAL)
                .andRespond(withSuccess("""
                        {"features":[{"properties":{"place_id":"mercado-a","name":"Mercado de prueba",
                         "formatted":"Plaza del Mercado, Madrid","categories":["commercial.marketplace"]}}]}
                        """, MediaType.APPLICATION_JSON));
        // Un mercado sin información gastronómica no se considera automáticamente gastronómico.
        assertEquals(List.of(new LugarDto("mercado-a", "Mercado de prueba", "Plaza del Mercado, Madrid", null, null,
                        List.of(CULTURA_LOCAL), List.of(MERCADOS_TRADICIONALES), null)),
                servicio.preparar(configuracion("\"CULTURA_LOCAL\"", "\"MERCADOS_GASTRONOMICOS\"")));
        proveedor.verify();
    }

    @Test
    void excluyeMercadosTradicionalesSinEtiquetasDeOrigenYConservaElRestoDeCulturaLocal() {
        esperar(CULTURA_LOCAL).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"mercado-a","name":"Mercado de prueba","categories":["commercial.marketplace"]}},
                 {"properties":{"place_id":"taller-a","name":"Taller de cerámica","categories":["production.pottery"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(List.of(new LugarDto("taller-a", "Taller de cerámica", null, null, null,
                        List.of(CULTURA_LOCAL), List.of(TALLERES_ARTESANALES), null)),
                servicio.preparar(configuracion("\"CULTURA_LOCAL\"", "\"MERCADOS_TRADICIONALES\"")));
        proveedor.verify();
    }

    @Test
    void conservaEstablecimientosDistintosQueTienenElMismoNombre() {
        esperar(GASTRONOMIA).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Local","formatted":"Calle A","categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":"Local","formatted":"Calle B","categories":["catering.restaurant"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        var resultado = servicio.preparar(configuracion("\"GASTRONOMIA\"", ""));
        assertEquals(List.of("a", "b"), resultado.stream().map(LugarDto::id).toList());
        assertEquals(List.of("Calle A", "Calle B"), resultado.stream().map(LugarDto::direccion).toList());
        proveedor.verify();
    }

    @Test
    void descartaNombresAusentesVaciosODesconocidosYRecuperaElNombreDeOtraCopia() {
        esperar(GASTRONOMIA).andRespond(withSuccess("""
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
        assertEquals(List.of(new LugarDto("j", "Restaurante con nombre", null, null, null,
                        List.of(GASTRONOMIA), List.of(), null),
                        new LugarDto("k", "La Desconocida", null, null, null, List.of(GASTRONOMIA), List.of(), null)),
                servicio.preparar(configuracion("\"GASTRONOMIA\"", "")));
        proveedor.verify();
    }

    @Test
    void rescataElHorarioDisponibleSinInventarloYLoConservaAlAgrupar() {
        esperar(GASTRONOMIA).andRespond(withSuccess("""
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
        assertEquals(List.of(new LugarDto("a", "Directo", null, null, null, List.of(GASTRONOMIA), List.of(), "Mo-Fr 10:00-18:00"),
                        new LugarDto("b", "Origen", null, null, null, List.of(GASTRONOMIA), List.of(), "Sa-Su 09:00-14:00"),
                        new LugarDto("c", "Sin horario", null, null, null, List.of(GASTRONOMIA), List.of(), null),
                        new LugarDto("d", "Otra copia", null, null, null, List.of(GASTRONOMIA), List.of(), "24/7")),
                servicio.preparar(configuracion("\"GASTRONOMIA\"", "")));
        proveedor.verify();
    }

    @Test
    void conservaIdentidadCoordenadasYClasificacionDeTodasLasCopias() {
        esperar(ARTE_MUSEOS).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"museo-a","name":"Museo y galería","categories":["entertainment.museum"]}},
                 {"properties":{"place_id":"museo-a","lat":40.41,"lon":-3.69,"formatted":"Calle del Museo, Madrid",
                  "categories":["entertainment.culture.gallery"],"opening_hours":"Tu-Su 10:00-18:00"}}
                ]}
                """, MediaType.APPLICATION_JSON));
        assertEquals(List.of(new LugarDto("museo-a", "Museo y galería", "Calle del Museo, Madrid", 40.41, -3.69,
                        List.of(ARTE_MUSEOS, CULTURA_LOCAL), List.of(MUSEOS, GALERIAS_ARTE), "Tu-Su 10:00-18:00")),
                servicio.preparar(configuracion("\"ARTE_MUSEOS\"", "")));
        proveedor.verify();
    }

    @Test
    void recuperaUnParValidoSinMezclarCoordenadasNiInventarLasAusentes() {
        esperar(GASTRONOMIA).andRespond(withSuccess("""
                {"features":[
                 {"properties":{"place_id":"a","name":"Recuperado","lat":40.4,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"a","lat":40.5,"lon":-3.8,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","name":"Incompleto","lat":40.4,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"b","lon":-3.7,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"c","name":"Fuera de rango","lat":91,"lon":-181,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"d","name":"Coordenadas cero","lat":0,"lon":0,"categories":["catering.restaurant"]}},
                 {"properties":{"place_id":"e","name":"Sin coordenadas","categories":["catering.restaurant"]}}
                ]}
                """, MediaType.APPLICATION_JSON));
        var resultado = servicio.preparar(configuracion("\"GASTRONOMIA\"", ""));
        assertEquals(5, resultado.size());
        assertEquals(40.5, resultado.get(0).latitud());
        assertEquals(-3.8, resultado.get(0).longitud());
        for (int indice : List.of(1, 2, 4)) {
            assertNull(resultado.get(indice).latitud());
            assertNull(resultado.get(indice).longitud());
        }
        assertEquals(0.0, resultado.get(3).latitud());
        assertEquals(0.0, resultado.get(3).longitud());
        proveedor.verify();
    }

    @Test
    void sinInteresesConsultaLasCategoriasGenerales() {
        for (CategoriaTuristica categoria : CategoriaTuristica.values()) {
            esperar(categoria).andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        }
        assertTrue(servicio.preparar(configuracion("", "")).isEmpty());
        proveedor.verify();
    }

    @Test
    void omiteCategoriasCompletasExcluidasYFiltraLasEtiquetasCruzadas() {
        esperar(GASTRONOMIA).andRespond(withSuccess("""
                {"features":[{"properties":{"place_id":"a","name":"Local","categories":["catering.restaurant","entertainment.bowling_alley"]}}]}
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
        esperar(GASTRONOMIA).andRespond(withSuccess(lugares("catering.fast_food"), MediaType.APPLICATION_JSON));
        esperar(GASTRONOMIA).andRespond(withSuccess(lugares("catering.fast_food"), MediaType.APPLICATION_JSON));
        assertEquals(1, servicio.preparar(configuracion("\"GASTRONOMIA\"", "")).size());
        assertTrue(servicio.preparar(configuracion("\"GASTRONOMIA\"", "\"COMIDA_RAPIDA\"")).isEmpty());
        proveedor.verify();
    }

    @Test
    void unaRespuestaInvalidaOUnFalloNoSeConfundenConUnaListaVacia() {
        esperar(GASTRONOMIA).andRespond(withSuccess("{\"features\":[{}]}", MediaType.APPLICATION_JSON));
        esperar(GASTRONOMIA).andRespond(withServerError());
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
