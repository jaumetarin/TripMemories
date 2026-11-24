package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.AlojamientoDto;
import es.upv.pin.backend.dto.FeatureLugarGeoapifyDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;
import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;

class AlojamientoServiceTest {

    private final GeoapifyClient cliente = mock(GeoapifyClient.class);

    private final AlojamientoService servicio =
            new AlojamientoService(cliente);

    @Test
    void transformaResultadosYUsaLaDireccionCuandoFaltaElNombre() {
        var hotel = resultado(
                "hotel", "Hotel Centro", " Calle de Prueba 10 ",
                40.4, -3.7, "amenity");

        var vivienda = resultado(
                "vivienda", null, " Calle de Prueba 20 ",
                40.5, -3.8, "building");

        when(cliente.buscarAlojamientos("Hotel", "destino-prueba"))
                .thenReturn(new RespuestaLugaresGeoapifyDto(
                        List.of(hotel, vivienda)));

        List<AlojamientoDto> alojamientos =
                servicio.buscar("  Hotel  ", "  destino-prueba  ");

        assertEquals(
                List.of(
                        new AlojamientoDto(
                                "hotel", "Hotel Centro",
                                "Calle de Prueba 10", 40.4, -3.7),
                        new AlojamientoDto(
                                "vivienda", "Calle de Prueba 20",
                                "Calle de Prueba 20", 40.5, -3.8)),
                alojamientos);

        verify(cliente).buscarAlojamientos("Hotel", "destino-prueba");
    }

    @Test
    void descartaDuplicadosCiudadesYCoordenadasInvalidas() {
        var hotel = resultado(
                "hotel", "Hotel Centro", "Calle de Prueba 10",
                40.4, -3.7, "amenity");

        var ciudad = resultado(
                "ciudad", "Madrid", "Madrid, España",
                40.4, -3.7, "city");

        var sinCoordenadas = resultado(
                "sin-coordenadas", "Hotel sin ubicación", "Calle 20",
                null, -3.7, "amenity");

        var coordenadasIncorrectas = resultado(
                "incorrecto", "Hotel incorrecto", "Calle 30",
                91.0, -3.7, "building");

        when(cliente.buscarAlojamientos("Hotel", "destino-prueba"))
                .thenReturn(new RespuestaLugaresGeoapifyDto(
                        List.of(
                                hotel, hotel, ciudad,
                                sinCoordenadas, coordenadasIncorrectas)));

        List<AlojamientoDto> alojamientos =
                servicio.buscar("Hotel", "destino-prueba");

        assertEquals(
                List.of(new AlojamientoDto(
                        "hotel", "Hotel Centro",
                        "Calle de Prueba 10", 40.4, -3.7)),
                alojamientos);

        verify(cliente).buscarAlojamientos("Hotel", "destino-prueba");
    }
    @Test
    void noConsultaAlProveedorConMenosDeTresCaracteres() {
        assertTrue(servicio.buscar(null, "destino-prueba").isEmpty());

        for (String texto : List.of("", "   ", "H", "Ho", "  Ho  ")) {
            assertTrue(servicio.buscar(texto, "destino-prueba").isEmpty());
        }

        verifyNoInteractions(cliente);
    }

    @Test
    void devuelveComoMaximoCincoSugerencias() {
        var resultadosGeoapify = IntStream.range(0, 7)
                .mapToObj(i -> resultado(
                        "hotel-" + i,
                        "Hotel " + i,
                        "Calle de Prueba " + i,
                        40.4,
                        -3.7,
                        "amenity"))
                .toList();

        when(cliente.buscarAlojamientos("Hotel", "destino-prueba"))
                .thenReturn(new RespuestaLugaresGeoapifyDto(
                        resultadosGeoapify));

        List<AlojamientoDto> alojamientos =
                servicio.buscar("Hotel", "destino-prueba");

        assertEquals(
                List.of(
                        "hotel-0", "hotel-1", "hotel-2",
                        "hotel-3", "hotel-4"),
                alojamientos.stream().map(AlojamientoDto::id).toList());

        verify(cliente).buscarAlojamientos("Hotel", "destino-prueba");
    }

    @Test
    void comunicaElFalloDelProveedor() {
        when(cliente.buscarAlojamientos("Hotel", "destino-prueba"))
                .thenThrow(new IllegalStateException(
                        "No se ha podido consultar el servicio de alojamientos."));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> servicio.buscar("Hotel", "destino-prueba"));

        assertEquals(
                "No se ha podido consultar el servicio de alojamientos.",
                error.getMessage());

        verify(cliente).buscarAlojamientos("Hotel", "destino-prueba");
    }
    private FeatureLugarGeoapifyDto resultado(
            String id,
            String nombre,
            String direccion,
            Double latitud,
            Double longitud,
            String tipo) {

        return new FeatureLugarGeoapifyDto(new LugarGeoapifyDto(
                id,
                nombre,
                latitud,
                longitud,
                List.of(),
                direccion,
                null,
                null,
                null,
                null,
                tipo));
    }
}
