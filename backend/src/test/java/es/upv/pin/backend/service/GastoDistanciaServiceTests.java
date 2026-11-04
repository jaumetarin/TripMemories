package es.upv.pin.backend.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.model.NivelGasto;

import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static es.upv.pin.backend.model.SubcategoriaTuristica.*;
import static org.junit.jupiter.api.Assertions.*;

class GastoDistanciaServiceTests {
    private final PreferenciasService servicio = new PreferenciasService();

    @ParameterizedTest
    @EnumSource(NivelGasto.class)
    void aceptaTodosLosNivelesConAmbosLimitesDeDistancia(NivelGasto nivel) {
        for (double distancia : List.of(1.0, 10.0)) {
            PreferenciasDto entrada = new PreferenciasDto(
                    List.of(GASTRONOMIA), List.of(ARTE_MUSEOS), List.of(COMIDA_RAPIDA), nivel, distancia);
            assertEquals(entrada, servicio.validar(entrada));
        }
    }

    @Test
    void elCatalogoExplicaElSignificadoYDefineLosValoresIniciales() {
        var opciones = servicio.obtenerOpcionesGastoDistancia();
        assertEquals(List.of(NivelGasto.values()),
                opciones.nivelesGasto().stream().map(nivel -> nivel.codigo()).toList());
        assertTrue(opciones.nivelesGasto().stream()
                .allMatch(nivel -> !nivel.nombre().isBlank() && !nivel.descripcion().isBlank()));
        assertEquals(1, opciones.distanciaMinimaKm());
        assertEquals(10, opciones.distanciaMaximaKm());
        assertEquals(1, opciones.pasoDistanciaKm());
        assertEquals(NivelGasto.SIN_PREFERENCIA, opciones.nivelGastoInicial());
        assertEquals(3, opciones.distanciaInicialKm());
        assertTrue(opciones.significadoGasto().contains("no es un presupuesto"));
        assertTrue(opciones.significadoDistancia().contains("una actividad y la siguiente"));
        assertTrue(opciones.significadoDistancia().contains("No es la distancia total del día"));
        assertTrue(opciones.significadoDistancia().contains("ni un límite obligatorio"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {-1, 0, 0.9, 1.5, 9.9, 10.1, 11, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rechazaDistanciasAusentesNoFinitasFueraDeRangoOFraccionarias(Double distancia) {
        var entrada = new PreferenciasDto(List.of(), List.of(), List.of(), NivelGasto.MODERADO, distancia);
        String error = assertThrows(IllegalArgumentException.class, () -> servicio.validar(entrada)).getMessage();
        assertTrue(error.contains("distancia"));
    }

    @Test
    void rechazaNivelAusenteConUnMensajeCorregible() {
        var entrada = new PreferenciasDto(List.of(), List.of(), List.of(), null, 3.0);
        String error = assertThrows(IllegalArgumentException.class, () -> servicio.validar(entrada)).getMessage();
        assertTrue(error.contains("Elige un nivel de gasto"));
        assertTrue(error.contains("Sin preferencia"));
    }

    @Test
    void editarGastoYDistanciaConservaElRestoDePreferencias() {
        var iniciales = new PreferenciasDto(List.of(GASTRONOMIA, GASTRONOMIA, NATURALEZA_PARQUES),
                List.of(ARTE_MUSEOS), List.of(COMIDA_RAPIDA, SENDERISMO), NivelGasto.ECONOMICO, 2.0);
        var primeraValidacion = servicio.validar(iniciales);
        var editadas = new PreferenciasDto(primeraValidacion.intereses(), primeraValidacion.categoriasExcluidas(),
                primeraValidacion.subcategoriasExcluidas(), NivelGasto.FLEXIBLE, 8.0);
        var resultado = servicio.validar(editadas);
        assertEquals(NivelGasto.FLEXIBLE, resultado.nivelGasto());
        assertEquals(8.0, resultado.distanciaKm());
        assertEquals(primeraValidacion.intereses(), resultado.intereses());
        assertEquals(primeraValidacion.categoriasExcluidas(), resultado.categoriasExcluidas());
        assertEquals(primeraValidacion.subcategoriasExcluidas(), resultado.subcategoriasExcluidas());
        assertEquals(NivelGasto.ECONOMICO, primeraValidacion.nivelGasto());
        assertEquals(2.0, primeraValidacion.distanciaKm());
    }
}
