package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.NivelGasto;
import es.upv.pin.backend.model.SubcategoriaTuristica;
import java.util.HashSet;
import java.util.List;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

class PreferenciasServiceProperties {
    private final PreferenciasService servicio = new PreferenciasService();

    @Property(tries = 120)
    void preferenciasSinContradiccionesEliminanDuplicadosConservandoOrden(
            @ForAll List<CategoriaTuristica> intereses,
            @ForAll List<CategoriaTuristica> excluidas,
            @ForAll List<SubcategoriaTuristica> subexcluidas,
            @ForAll NivelGasto gasto,
            @ForAll @IntRange(min = 1, max = 10) int distanciaKm) {
        Assume.that(intereses.stream().noneMatch(excluidas::contains));

        PreferenciasDto res = servicio.validar(new PreferenciasDto(
                intereses, excluidas, subexcluidas, gasto, (double) distanciaKm));

        assertEquals(new HashSet<>(intereses).size(), res.intereses().size());
        assertEquals(new HashSet<>(excluidas).size(), res.categoriasExcluidas().size());
        assertEquals(new HashSet<>(subexcluidas).size(), res.subcategoriasExcluidas().size());
        assertEquals(gasto, res.nivelGasto());
        assertEquals((double) distanciaKm, res.distanciaKm());
    }

    @Property(tries = 80)
    void cualquierCategoriaEnInteresesYExcluidasProduceError(
            @ForAll CategoriaTuristica comun,
            @ForAll List<CategoriaTuristica> otrosIntereses,
            @ForAll NivelGasto gasto,
            @ForAll @IntRange(min = 1, max = 10) int distanciaKm) {
        var intereses = new java.util.ArrayList<>(otrosIntereses);
        intereses.add(comun);
        var excluidas = List.of(comun);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(new PreferenciasDto(intereses, excluidas, List.of(), gasto, (double) distanciaKm)));
        assertTrue(ex.getMessage().contains(comun.getNombre()));
    }

    @Property(tries = 80)
    void distanciasFueraDeRangoONoEnterasSonRechazadas(
            @ForAll("distanciasInvalidas") double distancia) {
        PreferenciasDto entrada = new PreferenciasDto(
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), List.of(), NivelGasto.MODERADO, distancia);
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(entrada));
    }

    @Provide
    Arbitrary<Double> distanciasInvalidas() {
        return Arbitraries.oneOf(
                Arbitraries.doubles().between(-100.0, 0.99),
                Arbitraries.doubles().between(10.01, 100.0),
                Arbitraries.of(1.5, 2.25, 3.7, Double.NaN, Double.POSITIVE_INFINITY));
    }
}
