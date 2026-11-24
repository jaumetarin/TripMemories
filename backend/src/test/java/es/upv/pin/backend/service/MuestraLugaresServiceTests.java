package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static es.upv.pin.backend.model.TipoLugar.*;

class MuestraLugaresServiceTests {
    private final MuestraLugaresService servicio = new MuestraLugaresService();
    private ConfiguracionItinerarioDto config(CategoriaTuristica... intereses) {
        return new ConfiguracionItinerarioDto(new DestinoDto("madrid", "Madrid", null, "España", 40.4168, -3.7038), null, "",
                new PreferenciasDto(List.of(intereses), List.of(), List.of(), NivelGasto.SIN_PREFERENCIA, 3.0));
    }
    private LugarDto lugar(String nombre, TipoLugar tipo, boolean documentado, CategoriaTuristica... categorias) {
        return new LugarDto(nombre, nombre, "Madrid", 40.4168, -3.7038, List.of(categorias), List.of(), null, tipo, documentado);
    }
    private List<LugarDto> variados() {
        List<LugarDto> lugares = new ArrayList<>();
        for (int i = 0; i < 100; i++) lugares.add(lugar("Centro comercial " + i, CENTRO_COMERCIAL, true, OCIO_ENTRETENIMIENTO));
        for (int i = 0; i < 12; i++) {
            lugares.add(lugar("Monumento " + i, MONUMENTO, true, HISTORIA_ARQUITECTURA));
            lugares.add(lugar("Teatro " + i, TEATRO, true, CULTURA_LOCAL));
            lugares.add(lugar("Parque " + i, PARQUE, true, NATURALEZA_PARQUES));
            lugares.add(lugar("Bolera " + i, BOLERA, false, OCIO_ENTRETENIMIENTO));
            lugares.add(lugar("Escape " + i, ESCAPE_ROOM, false, OCIO_ENTRETENIMIENTO));
        }
        lugares.add(lugar("Zoo", ZOO_ACUARIO, true, OCIO_ENTRETENIMIENTO));
        lugares.add(lugar("Parque de atracciones", PARQUE_ATRACCIONES, true, OCIO_ENTRETENIMIENTO));
        return lugares;
    }

    @Test void reparteSietePorInteresSinRellenarConCentrosComercialesYEsIndependienteDelOrdenDelCache() {
        var datos = variados();
        var config = config(HISTORIA_ARQUITECTURA, CULTURA_LOCAL, NATURALEZA_PARQUES, OCIO_ENTRETENIMIENTO);
        var muestra = servicio.seleccionar(datos, config, 30, true);
        assertEquals(28, muestra.lugares().size());
        assertTrue(muestra.reparto().stream().allMatch(r -> r.mostrados() == 7 && r.objetivo() == 7));
        assertEquals(28, muestra.lugares().stream().map(LugarDto::id).distinct().count());
        assertTrue(muestra.lugares().stream().filter(l -> l.tipo() == CENTRO_COMERCIAL).count() <= 2);
        assertTrue(muestra.lugares().stream().anyMatch(l -> l.tipo() == BOLERA));
        assertTrue(muestra.lugares().stream().anyMatch(l -> l.tipo() == ZOO_ACUARIO));
        Collections.reverse(datos);
        assertEquals(muestra, servicio.seleccionar(datos, config, 30, true));
    }

    @Test void resolverCategoriasCompartidasNoDuplicaNiCreaUnaFalsaEscasez() {
        var lugares = List.of(lugar("a", PLAZA, true, HISTORIA_ARQUITECTURA, CULTURA_LOCAL),
                lugar("b", PLAZA, true, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES),
                lugar("c", PLAZA, true, CULTURA_LOCAL, NATURALEZA_PARQUES));
        var muestra = servicio.seleccionar(lugares, config(HISTORIA_ARQUITECTURA, CULTURA_LOCAL, NATURALEZA_PARQUES), 3, true);
        assertEquals(3, muestra.lugares().size());
        assertTrue(muestra.reparto().stream().allMatch(r -> r.mostrados() == 1));
        assertEquals(3, muestra.categorias().size());
    }

    @Test void unaCategoriaConPocosLugaresNoSeSustituyePorOtra() {
        var datos = variados().stream().filter(l -> !l.categorias().contains(NATURALEZA_PARQUES)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        datos.add(lugar("Único parque", PARQUE, true, NATURALEZA_PARQUES));
        var muestra = servicio.seleccionar(datos, config(HISTORIA_ARQUITECTURA, CULTURA_LOCAL, NATURALEZA_PARQUES, OCIO_ENTRETENIMIENTO), 30, true);
        assertEquals(22, muestra.lugares().size());
        assertEquals(1, muestra.reparto().stream().filter(r -> r.codigo() == NATURALEZA_PARQUES).findFirst().orElseThrow().mostrados());
        assertTrue(muestra.reparto().stream().allMatch(r -> r.mostrados() <= 7));
    }

    @Test void ocioSinAlternativasNoRellenaTodaLaMuestraConCompras() {
        var datos = java.util.stream.IntStream.range(0, 50).mapToObj(i -> lugar("Centro " + i, CENTRO_COMERCIAL, true, OCIO_ENTRETENIMIENTO)).toList();
        var muestra = servicio.seleccionar(datos, config(OCIO_ENTRETENIMIENTO), 9, true);
        assertEquals(3, muestra.lugares().size());
        assertEquals(9, muestra.reparto().getFirst().objetivo());
    }

    @Test void referenciasDocumentalesOrientanSinConfundirCercaniaConFama() {
        var cercano = lugar("Jardín de barrio", PARQUE, false, NATURALEZA_PARQUES);
        var retiro = new LugarDto("retiro", "Parque del Retiro", "Madrid", 40.4145, -3.6831,
                List.of(NATURALEZA_PARQUES), List.of(), null, PARQUE, true);
        assertEquals(List.of(retiro), servicio.seleccionar(List.of(cercano, retiro), config(NATURALEZA_PARQUES), 1, true).lugares());
    }

    @Test void buscarUnNombreNoOcultaCoincidenciasPorLasCuotasDeOtrosIntereses() {
        var plaza = lugar("Plaza de España", PLAZA, true, HISTORIA_ARQUITECTURA, CULTURA_LOCAL, NATURALEZA_PARQUES);
        var resultado = servicio.seleccionar(List.of(plaza), config(HISTORIA_ARQUITECTURA, CULTURA_LOCAL, NATURALEZA_PARQUES, OCIO_ENTRETENIMIENTO), 30, false);
        assertEquals(List.of(plaza), resultado.lugares());
        assertTrue(resultado.reparto().isEmpty());
    }

    @Test void referenciasConMasNombresPublicadosTienenPreferenciaDentroDelMismoTipo() {
        var cercano = lugar("Jardín documentado", PARQUE, true, NATURALEZA_PARQUES);
        var destacado = new LugarDto("parque", "Parque con referencias internacionales", "Madrid", 40.4145, -3.6831,
                List.of(NATURALEZA_PARQUES), List.of(), null, PARQUE, true, 7);
        assertEquals(List.of(destacado), servicio.seleccionar(List.of(cercano, destacado), config(NATURALEZA_PARQUES), 1, true).lugares());
    }

    @Test void compartirUnaCategoriaNoDesplazaUnLugarDestacadoSiQuedanAlternativasLibres() {
        var plaza = new LugarDto("plaza", "Plaza documentada", "Madrid", 40.4168, -3.7038,
                List.of(HISTORIA_ARQUITECTURA, CULTURA_LOCAL), List.of(), null, PLAZA, true, 8);
        var datos = List.of(plaza, lugar("Monumento", MONUMENTO, false, HISTORIA_ARQUITECTURA),
                lugar("Mercado", MERCADO, false, CULTURA_LOCAL));
        var muestra = servicio.seleccionar(datos, config(HISTORIA_ARQUITECTURA, CULTURA_LOCAL), 2, true);
        assertTrue(muestra.lugares().contains(plaza));
        assertEquals(2, muestra.lugares().size());
        assertTrue(muestra.reparto().stream().allMatch(r -> r.mostrados() == 1));
    }
}
