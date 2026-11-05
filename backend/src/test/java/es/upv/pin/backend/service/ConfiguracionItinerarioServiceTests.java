package es.upv.pin.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.FechaHoraDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.model.NivelGasto;

import static es.upv.pin.backend.model.CategoriaTuristica.*;
import static es.upv.pin.backend.model.SubcategoriaTuristica.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionItinerarioServiceTests {
    private final ConfiguracionItinerarioService servicio =
            new ConfiguracionItinerarioService(new FechaHoraService(), new PreferenciasService());
    private final DestinoDto destino = new DestinoDto("madrid-id", "Madrid", "Madrid", "España", 40.4168, -3.7038);
    private final FechaHoraDto fechas = new FechaHoraDto(
            LocalDateTime.parse("2026-11-10T10:00"), LocalDateTime.parse("2026-11-12T18:00"), 999);
    private final PreferenciasDto preferencias = new PreferenciasDto(
            List.of(GASTRONOMIA), List.of(ARTE_MUSEOS), List.of(COMIDA_RAPIDA), NivelGasto.ECONOMICO, 3.0);

    private ConfiguracionItinerarioDto viaje(DestinoDto lugar, FechaHoraDto intervalo, String alojamiento,
            PreferenciasDto seleccion) {
        return new ConfiguracionItinerarioDto(lugar, intervalo, alojamiento, seleccion);
    }

    @Test
    void normalizaLaConfiguracionCompletaYRecalculaLaDuracion() {
        var resultado = servicio.validar(viaje(destino, fechas, "  Hotel Madrid  ", preferencias));
        assertEquals(destino, resultado.destino());
        assertEquals(3, resultado.disponibilidad().duracionDias());
        assertEquals(fechas.llegada(), resultado.disponibilidad().llegada());
        assertEquals(fechas.salida(), resultado.disponibilidad().salida());
        assertEquals("Hotel Madrid", resultado.alojamiento());
        assertEquals(preferencias, resultado.preferencias());
    }

    @Test
    void cambiarUnParametroConservaLosDemasYNoSustituyeOtraConfirmacion() {
        var anterior = servicio.validar(viaje(destino, fechas, "Hotel", preferencias));
        var modificadas = new PreferenciasDto(preferencias.intereses(), preferencias.categoriasExcluidas(),
                preferencias.subcategoriasExcluidas(), NivelGasto.MODERADO, preferencias.distanciaKm());
        var actual = servicio.validar(viaje(anterior.destino(), anterior.disponibilidad(), anterior.alojamiento(),
                modificadas));
        assertEquals(NivelGasto.MODERADO, actual.preferencias().nivelGasto());
        assertEquals(3.0, actual.preferencias().distanciaKm());
        assertEquals(anterior.destino(), actual.destino());
        assertEquals(anterior.disponibilidad(), actual.disponibilidad());
        assertEquals(anterior.alojamiento(), actual.alojamiento());
        assertEquals(preferencias.intereses(), actual.preferencias().intereses());
        assertEquals(preferencias.categoriasExcluidas(), actual.preferencias().categoriasExcluidas());
        assertEquals(preferencias.subcategoriasExcluidas(), actual.preferencias().subcategoriasExcluidas());
        assertEquals(NivelGasto.ECONOMICO, anterior.preferencias().nivelGasto());
    }

    @Test
    void lasListasConfirmadasNoDependenDeLasListasMutablesDelCliente() {
        var intereses = new ArrayList<>(List.of(GASTRONOMIA));
        var entrada = new PreferenciasDto(intereses, List.of(), List.of(), NivelGasto.FLEXIBLE, 8.0);
        var confirmada = servicio.validar(viaje(destino, fechas, "", entrada));
        intereses.clear();
        assertEquals(List.of(GASTRONOMIA), confirmada.preferencias().intereses());
        assertThrows(UnsupportedOperationException.class, () -> confirmada.preferencias().intereses().clear());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void permiteNoIndicarAlojamiento(String alojamiento) {
        assertEquals("", servicio.validar(viaje(destino, fechas, alojamiento, preferencias)).alojamiento());
    }

    @Test
    void rechazaAlojamientoDemasiadoLargoPeroPermiteElLimite() {
        assertEquals(250, servicio.validar(viaje(destino, fechas, "a".repeat(250), preferencias)).alojamiento().length());
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(viaje(destino, fechas, "a".repeat(251), preferencias)));
    }

    @Test
    void rechazaConfiguracionYApartadosObligatoriosAusentes() {
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(null));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(viaje(null, fechas, "", preferencias)));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(viaje(destino, null, "", preferencias)));
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(viaje(destino, fechas, "", null)));
    }

    @ParameterizedTest
    @CsvSource({"91,0", "-91,0", "0,181", "0,-181", "NaN,0", "0,Infinity"})
    void rechazaCoordenadasInvalidas(Double latitud, Double longitud) {
        var lugar = new DestinoDto("id", "Madrid", "", "", latitud, longitud);
        assertThrows(IllegalArgumentException.class, () -> servicio.validar(viaje(lugar, fechas, "", preferencias)));
    }

    @Test
    void rechazaIdentidadOCoordenadasAusentesYNormalizaTextosOpcionales() {
        for (DestinoDto lugar : List.of(
                new DestinoDto("", "Madrid", "", "", 0.0, 0.0),
                new DestinoDto("id", "", "", "", 0.0, 0.0),
                new DestinoDto("id", "Madrid", "", "", null, 0.0),
                new DestinoDto("id", "Madrid", "", "", 0.0, null))) {
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.validar(viaje(lugar, fechas, "", preferencias)));
        }
        var lugar = new DestinoDto(" id ", " Madrid ", null, null, 0.0, 0.0);
        var normalizado = servicio.validar(viaje(lugar, fechas, "", preferencias)).destino();
        assertEquals("id", normalizado.id());
        assertEquals("Madrid", normalizado.nombre());
        assertEquals("", normalizado.region());
        assertEquals("", normalizado.pais());
    }

    @Test
    void vuelveAComprobarFechasYPreferenciasAunqueElClienteLasHayaDadoPorValidas() {
        var invertidas = new FechaHoraDto(fechas.salida(), fechas.llegada(), 1);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(viaje(destino, invertidas, "", preferencias)));
        var contradiccion = new PreferenciasDto(List.of(GASTRONOMIA), List.of(GASTRONOMIA),
                List.of(), NivelGasto.MODERADO, 3.0);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(viaje(destino, fechas, "", contradiccion)));
        var distanciaInvalida = new PreferenciasDto(List.of(), List.of(), List.of(), NivelGasto.MODERADO, 11.0);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.validar(viaje(destino, fechas, "", distanciaInvalida)));
    }
}
