package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static es.upv.pin.backend.model.CategoriaTuristica.*;

class IdentidadLugaresTests {
    private LugarDto lugar(String id, String nombre, double lat, double lon, CategoriaTuristica categoria, String direccion) {
        return new LugarDto(id, nombre, direccion, lat, lon, List.of(categoria), List.of(), null);
    }

    @Test
    void reconoceElParqueYElLaberintoEnDosIdiomasConIdsYCategoriasDistintas() {
        var parque = lugar("parque", "Parc del Laberint d’Horta", 41.439, 2.145, NATURALEZA_PARQUES, null);
        var laberinto = lugar("laberinto", "Laberinto de Horta", 41.440, 2.146, CULTURA_LOCAL, null);
        assertTrue(IdentidadLugares.mismoLugar(parque, laberinto));
        assertTrue(IdentidadLugares.mismoLugar(laberinto, parque));
    }

    @Test
    void noFusionaUnaVisitaIndependienteAunqueEsteEnElMismoPunto() {
        var parque = lugar("parque", "Parque del Laberinto de Horta", 41.439, 2.145, NATURALEZA_PARQUES, null);
        var museo = lugar("museo", "Museo de Horta", 41.439, 2.145, ARTE_MUSEOS, null);
        assertFalse(IdentidadLugares.mismoLugar(parque, museo));
    }

    @Test
    void noFusionaHomónimosLejanosNiSucursalesCercanasConDistintaDireccion() {
        assertFalse(IdentidadLugares.mismoLugar(
                lugar("a", "Parque Central", 40.4, -3.7, NATURALEZA_PARQUES, null),
                lugar("b", "Parque Central", 40.5, -3.7, NATURALEZA_PARQUES, null)));
        assertFalse(IdentidadLugares.mismoLugar(
                lugar("a", "Café de la Plaza", 40.4, -3.7, GASTRONOMIA, "Calle A, 1"),
                lugar("b", "Café de la Plaza", 40.40001, -3.7, GASTRONOMIA, "Calle A, 3")));
    }

    @Test
    void noDeduceIdentidadCuandoFaltanCoordenadas() {
        var a = new LugarDto("a", "Parque del Laberinto de Horta", null, null, null,
                List.of(NATURALEZA_PARQUES), List.of(), null);
        var b = new LugarDto("b", "Laberinto de Horta", null, null, null,
                List.of(NATURALEZA_PARQUES), List.of(), null);
        assertFalse(IdentidadLugares.mismoLugar(a, b));
        assertTrue(IdentidadLugares.mismoLugar(a, a));
    }
}
