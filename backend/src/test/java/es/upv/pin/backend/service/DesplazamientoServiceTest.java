package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.IntStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.dto.RespuestaMatrizOrsDto;
import es.upv.pin.backend.model.EstadoDesplazamiento;


class DesplazamientoServiceTest {

    private final OpenRouteServiceClient cliente =
            mock(OpenRouteServiceClient.class);

    private final DesplazamientoService servicio =
            new DesplazamientoService(cliente);

    private final PuntoRutaDto museo =
            new PuntoRutaDto("museo", 40.4138, -3.6921);

    private final PuntoRutaDto parque =
            new PuntoRutaDto("parque", 40.4153, -3.6844);

    private final RespuestaMatrizOrsDto matriz =
            new RespuestaMatrizOrsDto(
                    List.of(
                            List.of(0.0, 1200.0),
                            List.of(1300.0, 0.0)),
                    List.of(
                            List.of(0.0, 900.0),
                            List.of(960.0, 0.0)));

    @Test
    void relacionaCadaSentidoConSusIdentificadores() {
        List<PuntoRutaDto> puntos = List.of(museo, parque);

        when(cliente.calcularMatriz(puntos)).thenReturn(matriz);

        List<DesplazamientoDto> resultado = servicio.calcular(puntos);

        assertEquals(
                List.of(
                        new DesplazamientoDto(
                                "museo",
                                "parque",
                                1200.0,
                                900.0,
                                EstadoDesplazamiento.DISPONIBLE,
                                "openrouteservice"),
                        new DesplazamientoDto(
                                "parque",
                                "museo",
                                1300.0,
                                960.0,
                                EstadoDesplazamiento.DISPONIBLE,
                                "openrouteservice")),
                resultado);

        verify(cliente).calcularMatriz(puntos);
    }

    @Test
    void conservaLosPuntosSinCoordenadasSinDesplazarLosIndices() {
        PuntoRutaDto sinCoordenadas =
                new PuntoRutaDto("sin-coordenadas", null, null);

        List<PuntoRutaDto> puntos =
                List.of(sinCoordenadas, museo, parque);

        when(cliente.calcularMatriz(List.of(museo, parque)))
                .thenReturn(matriz);

        List<DesplazamientoDto> resultado = servicio.calcular(puntos);

        assertEquals(6, resultado.size());

        assertTrue(resultado.contains(new DesplazamientoDto(
                "museo",
                "parque",
                1200.0,
                900.0,
                EstadoDesplazamiento.DISPONIBLE,
                "openrouteservice")));

        assertTrue(resultado.contains(new DesplazamientoDto(
                "parque",
                "museo",
                1300.0,
                960.0,
                EstadoDesplazamiento.DISPONIBLE,
                "openrouteservice")));

        List<DesplazamientoDto> sinCalculo = resultado.stream()
                .filter(desplazamiento -> desplazamiento.estado()
                        == EstadoDesplazamiento.COORDENADAS_AUSENTES)
                .toList();

        assertEquals(4, sinCalculo.size());

        for (DesplazamientoDto desplazamiento : sinCalculo) {
            assertTrue(
                    desplazamiento.origenId().equals("sin-coordenadas")
                            || desplazamiento.destinoId()
                                    .equals("sin-coordenadas"));

            assertNull(desplazamiento.distanciaMetros());
            assertNull(desplazamiento.duracionSegundos());
            assertNull(desplazamiento.fuente());
        }

        verify(cliente).calcularMatriz(List.of(museo, parque));
    }
    @Test
    void marcaLaRutaAusenteSinAlterarElSentidoDisponible() {
        List<PuntoRutaDto> puntos = List.of(museo, parque);

        RespuestaMatrizOrsDto matrizConRutaAusente =
                new RespuestaMatrizOrsDto(
                        List.of(
                                Arrays.asList(0.0, null),
                                List.of(1300.0, 0.0)),
                        List.of(
                                Arrays.asList(0.0, null),
                                List.of(960.0, 0.0)));

        when(cliente.calcularMatriz(puntos))
                .thenReturn(matrizConRutaAusente);

        List<DesplazamientoDto> resultado = servicio.calcular(puntos);

        assertEquals(
                List.of(
                        new DesplazamientoDto(
                                "museo",
                                "parque",
                                null,
                                null,
                                EstadoDesplazamiento.RUTA_NO_DISPONIBLE,
                                "openrouteservice"),
                        new DesplazamientoDto(
                                "parque",
                                "museo",
                                1300.0,
                                960.0,
                                EstadoDesplazamiento.DISPONIBLE,
                                "openrouteservice")),
                resultado);

        verify(cliente).calcularMatriz(puntos);
    }
    @Test
    void noConsultaAlProveedorSiSoloUnPuntoTieneCoordenadas() {
        PuntoRutaDto sinCoordenadas =
                new PuntoRutaDto("sin-coordenadas", null, null);

        List<DesplazamientoDto> resultado =
                servicio.calcular(List.of(museo, sinCoordenadas));

        assertEquals(
                List.of(
                        new DesplazamientoDto(
                                "museo",
                                "sin-coordenadas",
                                null,
                                null,
                                EstadoDesplazamiento.COORDENADAS_AUSENTES,
                                null),
                        new DesplazamientoDto(
                                "sin-coordenadas",
                                "museo",
                                null,
                                null,
                                EstadoDesplazamiento.COORDENADAS_AUSENTES,
                                null)),
                resultado);

        verifyNoInteractions(cliente);
    }
    @Test
    void comunicaElFalloDelProveedor() {
        List<PuntoRutaDto> puntos = List.of(museo, parque);

        when(cliente.calcularMatriz(puntos))
                .thenThrow(new IllegalStateException(
                        "Proveedor no disponible"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> servicio.calcular(puntos));

        assertEquals(
                "Proveedor no disponible",
                error.getMessage());

        verify(cliente).calcularMatriz(puntos);
    }
    @Test
    void rechazaIdentificadoresRepetidosSinConsultarAlProveedor() {
        PuntoRutaDto repetido =
                new PuntoRutaDto("museo", 40.4153, -3.6844);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.calcular(List.of(museo, repetido)));

        verifyNoInteractions(cliente);
    }
    @ParameterizedTest
    @CsvSource({
            "91.0, -3.7",
            "-91.0, -3.7",
            "40.4, 181.0",
            "40.4, -181.0",
            "NaN, -3.7",
            "40.4, Infinity"
    })
    void rechazaCoordenadasInvalidasSinConsultarAlProveedor(
            double latitud, double longitud) {

        PuntoRutaDto incorrecto =
                new PuntoRutaDto("incorrecto", latitud, longitud);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.calcular(List.of(museo, incorrecto)));

        verifyNoInteractions(cliente);
    }
    @Test
    void rechazaMasDeCincuentaPuntosSinConsultarAlProveedor() {
        List<PuntoRutaDto> puntos = IntStream.range(0, 51)
                .mapToObj(i -> new PuntoRutaDto(
                        "punto-" + i,
                        40.4,
                        -3.7))
                .toList();

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.calcular(puntos));

        verifyNoInteractions(cliente);
    }
    @Test
    void devuelveUnaListaVaciaSiNoHayParejasDeLugares() {
        assertTrue(servicio.calcular(List.of()).isEmpty());

        assertTrue(servicio.calcular(List.of(museo)).isEmpty());

        verifyNoInteractions(cliente);
    }
}