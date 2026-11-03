package es.upv.pin.backend.service;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import es.upv.pin.backend.dto.CategoriaDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.SubcategoriaTuristica;

import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static es.upv.pin.backend.model.SubcategoriaTuristica.*;
import static org.junit.jupiter.api.Assertions.*;

class PreferenciasServiceTests {
    private final PreferenciasService servicio = new PreferenciasService();

    @Test
    void aceptaUnaConfiguracionSinPreferencias() {
        PreferenciasDto vacias = new PreferenciasDto(List.of(), List.of(), List.of());
        assertEquals(vacias, servicio.validar(vacias));
    }

    @ParameterizedTest
    @EnumSource(SubcategoriaTuristica.class)
    void aceptaExcluirUnaParteDelInteres(SubcategoriaTuristica exclusion) {
        PreferenciasDto entrada = new PreferenciasDto(List.of(exclusion.getCategoria()), List.of(), List.of(exclusion));
        assertEquals(entrada, servicio.validar(entrada));
    }

    @ParameterizedTest
    @EnumSource(CategoriaTuristica.class)
    void bloqueaExcluirTodaUnaCategoriaSeleccionadaComoInteres(CategoriaTuristica categoria) {
        PreferenciasDto entrada = new PreferenciasDto(List.of(categoria), List.of(categoria), List.of());
        String mensaje = assertThrows(IllegalArgumentException.class, () -> servicio.validar(entrada)).getMessage();
        assertTrue(mensaje.contains(categoria.getNombre()));
        assertTrue(mensaje.contains("Desmarca una de las dos opciones"));
        // La corrección corresponde al usuario; el servicio no modifica las listas recibidas.
        assertEquals(List.of(categoria), entrada.intereses());
        assertEquals(List.of(categoria), entrada.categoriasExcluidas());
    }

    @Test
    void eliminaDuplicadosDeLasTresListasYConservaElOrden() {
        PreferenciasDto entrada = new PreferenciasDto(
                List.of(NATURALEZA_PARQUES, GASTRONOMIA, NATURALEZA_PARQUES),
                List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, ARTE_MUSEOS),
                List.of(SENDERISMO, COMIDA_RAPIDA, SENDERISMO));
        PreferenciasDto esperadas = new PreferenciasDto(
                List.of(NATURALEZA_PARQUES, GASTRONOMIA), List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA),
                List.of(SENDERISMO, COMIDA_RAPIDA));
        assertEquals(esperadas, servicio.validar(entrada));
        assertEquals(esperadas, servicio.validar(servicio.validar(entrada)));
    }

    @Test
    void permiteConservarExclusionesAunqueSeRetireElInteresPrincipal() {
        PreferenciasDto entrada = new PreferenciasDto(List.of(GASTRONOMIA), List.of(OCIO_ENTRETENIMIENTO),
                List.of(SENDERISMO, COMIDA_RAPIDA));
        assertEquals(entrada, servicio.validar(entrada));
    }

    @Test
    void permiteExcluirTodaUnaCategoriaYTambienUnaParteDeElla() {
        PreferenciasDto entrada = new PreferenciasDto(List.of(), List.of(GASTRONOMIA), List.of(COMIDA_RAPIDA));
        assertEquals(entrada, servicio.validar(entrada));
    }

    @Test
    void permiteCorregirUnaContradiccionSinPerderLosDemasValores() {
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(new PreferenciasDto(
                List.of(GASTRONOMIA, NATURALEZA_PARQUES), List.of(GASTRONOMIA, ARTE_MUSEOS), List.of(SENDERISMO))));
        PreferenciasDto corregidas = new PreferenciasDto(List.of(GASTRONOMIA, NATURALEZA_PARQUES),
                List.of(ARTE_MUSEOS), List.of(SENDERISMO));
        assertEquals(corregidas, servicio.validar(corregidas));
    }

    @Test
    void noDeduceUnaExclusionCompletaPorMarcarLasTresSubcategorias() {
        PreferenciasDto entrada = new PreferenciasDto(List.of(GASTRONOMIA), List.of(),
                List.of(COMIDA_RAPIDA, CAFETERIAS, MERCADOS_GASTRONOMICOS));
        assertEquals(entrada, servicio.validar(entrada));
    }

    @Test
    void rechazaSolicitudesYListasNulas() {
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(null));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(new PreferenciasDto(null, List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(new PreferenciasDto(List.of(), null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(new PreferenciasDto(List.of(), List.of(), null)));
    }

    @Test
    void rechazaElementosNulosEnCualquieraDeLasListas() {
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(
                new PreferenciasDto(Arrays.asList((CategoriaTuristica) null), List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(
                new PreferenciasDto(List.of(), Arrays.asList((CategoriaTuristica) null), List.of())));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(
                new PreferenciasDto(List.of(), List.of(), Arrays.asList((SubcategoriaTuristica) null))));
    }

    @Test
    void elCatalogoIncluyeSeisCategoriasYTresSubcategoriasPorCategoria() {
        List<CategoriaDto> catalogo = servicio.obtenerCatalogo();
        assertEquals(6, catalogo.size());
        assertEquals(18, catalogo.stream().flatMap(categoria -> categoria.subcategorias().stream())
                .map(subcategoria -> subcategoria.codigo()).distinct().count());
        for (CategoriaDto categoria : catalogo) {
            assertEquals(categoria.codigo().getNombre(), categoria.nombre());
            assertEquals(3, categoria.subcategorias().size());
            for (var subcategoria : categoria.subcategorias()) {
                assertEquals(categoria.codigo(), subcategoria.codigo().getCategoria());
                assertFalse(subcategoria.nombre().isBlank());
            }
        }
    }
}
