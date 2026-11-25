package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.service.NecesidadesRutasEdicionService.Solicitud;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpSession;

class CacheRutasEdicionServiceTests {
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-25T10:00:00Z"));
    private final Clock reloj = mock(Clock.class);
    private final CacheRutasEdicionService cache = new CacheRutasEdicionService(reloj);
    private final MockHttpSession sesion = new MockHttpSession();
    private final Solicitud solicitud = solicitud(0);

    @BeforeEach
    void prepararReloj() {
        when(reloj.instant()).thenAnswer(invocacion -> ahora.get());
    }

    @Test
    void devuelveUnaRutaGuardadaYUnaClaveDesconocidaNoTieneResultado() {
        assertTrue(cache.buscar(solicitud, sesion).isEmpty());
        var ruta = ruta(solicitud, 500.0, 600.0);
        cache.guardar(solicitud, ruta, sesion);

        assertEquals(ruta, cache.buscar(solicitud, sesion).orElseThrow());
        assertTrue(cache.buscar(solicitud(1), sesion).isEmpty());
    }

    @Test
    void caducaExactamenteALosQuinceMinutosSinRenovarsePorLasConsultas() {
        cache.guardar(solicitud, ruta(solicitud, 500.0, 600.0), sesion);
        avanzar(899);
        assertTrue(cache.buscar(solicitud, sesion).isPresent());
        avanzar(1);
        assertTrue(cache.buscar(solicitud, sesion).isEmpty());
    }

    @Test
    void cadaEntradaTieneSuPropiaCaducidad() {
        cache.guardar(solicitud, ruta(solicitud, 500.0, 600.0), sesion);
        avanzar(60);
        var segunda = solicitud(1);
        cache.guardar(segunda, ruta(segunda, 700.0, 800.0), sesion);
        avanzar(840);

        assertTrue(cache.buscar(solicitud, sesion).isEmpty());
        assertTrue(cache.buscar(segunda, sesion).isPresent());
    }

    @Test
    void lasSesionesNoCompartenEntradasYUnaSesionNuevaNoRecuperaLaAnterior() {
        cache.guardar(solicitud, ruta(solicitud, 500.0, 600.0), sesion);
        var otraSesion = new MockHttpSession();
        assertTrue(cache.buscar(solicitud, otraSesion).isEmpty());
        cache.guardar(solicitud, ruta(solicitud, 100.0, 200.0), otraSesion);

        assertEquals(600.0, cache.buscar(solicitud, sesion).orElseThrow().duracionSegundos().doubleValue());
        assertEquals(200.0, cache.buscar(solicitud, otraSesion).orElseThrow().duracionSegundos().doubleValue());
        sesion.invalidate();
        assertTrue(cache.buscar(solicitud, new MockHttpSession()).isEmpty());
    }

    @Test
    void diferenciaElSentidoDelTrayecto() {
        cache.guardar(solicitud, ruta(solicitud, 500.0, 600.0), sesion);
        var inversa = new Solicitud(solicitud.destino(), solicitud.origen());
        assertTrue(cache.buscar(inversa, sesion).isEmpty());
        cache.guardar(inversa, ruta(inversa, 700.0, 800.0), sesion);

        assertEquals(600.0, cache.buscar(solicitud, sesion).orElseThrow().duracionSegundos().doubleValue());
        assertEquals(800.0, cache.buscar(inversa, sesion).orElseThrow().duracionSegundos().doubleValue());
    }

    @Test
    void cambiarCoordenadasOIdentificadoresInvalidaLaCoincidencia() {
        cache.guardar(solicitud, ruta(solicitud, 500.0, 600.0), sesion);
        var origen = solicitud.origen();
        var destino = solicitud.destino();

        assertTrue(cache.buscar(new Solicitud(new PuntoRutaDto(origen.id(), 40.0, origen.longitud()), destino), sesion).isEmpty());
        assertTrue(cache.buscar(new Solicitud(origen, new PuntoRutaDto(destino.id(), destino.latitud(), -0.9)), sesion).isEmpty());
        assertTrue(cache.buscar(new Solicitud(new PuntoRutaDto("otro", origen.latitud(), origen.longitud()), destino), sesion).isEmpty());
        assertTrue(cache.buscar(new Solicitud(origen, new PuntoRutaDto("otro", destino.latitud(), destino.longitud())), sesion).isEmpty());
        assertTrue(cache.buscar(solicitud, sesion).isPresent());
    }

    @Test
    void alSuperarElLimiteExpulsaLaMenosUtilizadaRecientemente() {
        for (int i = 0; i < 512; i++) {
            var entrada = solicitud(i);
            cache.guardar(entrada, ruta(entrada, 500.0, 600.0), sesion);
        }
        assertTrue(cache.buscar(solicitud(0), sesion).isPresent());
        var nueva = solicitud(512);
        cache.guardar(nueva, ruta(nueva, 500.0, 600.0), sesion);

        assertTrue(cache.buscar(solicitud(0), sesion).isPresent());
        assertTrue(cache.buscar(solicitud(1), sesion).isEmpty());
        assertTrue(cache.buscar(solicitud(2), sesion).isPresent());
        assertTrue(cache.buscar(nueva, sesion).isPresent());
    }

    @Test
    void retiraCaducadasAntesDeExpulsarEntradasVigentes() {
        cache.guardar(solicitud, ruta(solicitud, 500.0, 600.0), sesion);
        avanzar(60);
        for (int i = 1; i < 512; i++) {
            var entrada = solicitud(i);
            cache.guardar(entrada, ruta(entrada, 500.0, 600.0), sesion);
        }
        avanzar(840);
        var nueva = solicitud(512);
        cache.guardar(nueva, ruta(nueva, 500.0, 600.0), sesion);

        assertTrue(cache.buscar(solicitud, sesion).isEmpty());
        for (int i = 1; i <= 512; i++) assertTrue(cache.buscar(solicitud(i), sesion).isPresent());
    }

    @Test
    void actualizarUnaEntradaSustituyeSusDatosYRenuevaSuCaducidad() {
        cache.guardar(solicitud, ruta(solicitud, 500.0, 600.0), sesion);
        avanzar(600);
        var nueva = ruta(solicitud, 700.0, 800.0);
        cache.guardar(solicitud, nueva, sesion);
        avanzar(300);

        assertEquals(nueva, cache.buscar(solicitud, sesion).orElseThrow());
        avanzar(600);
        assertTrue(cache.buscar(solicitud, sesion).isEmpty());
    }

    @Test
    void permiteDistanciaYDuracionCeroComoResultadoValido() {
        var ruta = ruta(solicitud, 0.0, 0.0);
        cache.guardar(solicitud, ruta, sesion);
        assertEquals(ruta, cache.buscar(solicitud, sesion).orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("rutasInvalidas")
    void unaRutaInvalidaNoSeGuardaNiSobrescribeUnaValida(DesplazamientoDto invalida) {
        cache.guardar(solicitud, invalida, sesion);
        assertTrue(cache.buscar(solicitud, sesion).isEmpty());
        var valida = ruta(solicitud, 500.0, 600.0);
        cache.guardar(solicitud, valida, sesion);
        cache.guardar(solicitud, invalida, sesion);
        assertEquals(valida, cache.buscar(solicitud, sesion).orElseThrow());
    }

    private static Stream<DesplazamientoDto> rutasInvalidas() {
        var s = solicitud(0);
        return Stream.of(null,
                ruta(s, null, 600.0), ruta(s, 500.0, null),
                ruta(s, -1.0, 600.0), ruta(s, 500.0, -1.0),
                ruta(s, Double.NaN, 600.0), ruta(s, 500.0, Double.POSITIVE_INFINITY),
                new DesplazamientoDto("otro", "destino-0", 500.0, 600.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("referencia:inicio", "otro", 500.0, 600.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("referencia:inicio", "destino-0", 500.0, 600.0, EstadoDesplazamiento.RUTA_NO_DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("referencia:inicio", "destino-0", 500.0, 600.0, EstadoDesplazamiento.COORDENADAS_AUSENTES, "openrouteservice"),
                new DesplazamientoDto("referencia:inicio", "destino-0", 500.0, 600.0, EstadoDesplazamiento.DISPONIBLE, "otra fuente"));
    }

    @ParameterizedTest
    @MethodSource("puntosInvalidos")
    void rechazaSolicitudesConPuntosInvalidosEnAmbosExtremos(PuntoRutaDto punto) {
        for (var invalida : new Solicitud[]{new Solicitud(punto, solicitud.destino()), new Solicitud(solicitud.origen(), punto)}) {
            assertThrows(IllegalArgumentException.class, () -> cache.buscar(invalida, sesion));
            assertThrows(IllegalArgumentException.class, () -> cache.guardar(invalida, ruta(solicitud, 500.0, 600.0), sesion));
        }
    }

    private static Stream<PuntoRutaDto> puntosInvalidos() {
        return Stream.of(null, new PuntoRutaDto(null, 39.47, -0.37),
                new PuntoRutaDto(" ", 39.47, -0.37), new PuntoRutaDto("p", null, -0.37),
                new PuntoRutaDto("p", 39.47, null), new PuntoRutaDto("p", 90.1, -0.37),
                new PuntoRutaDto("p", 39.47, -180.1), new PuntoRutaDto("p", Double.NaN, -0.37),
                new PuntoRutaDto("p", 39.47, Double.NEGATIVE_INFINITY));
    }

    @Test
    void rechazaUnaSolicitudAusente() {
        assertThrows(IllegalArgumentException.class, () -> cache.buscar(null, sesion));
        assertThrows(IllegalArgumentException.class, () -> cache.guardar(null, ruta(solicitud, 500.0, 600.0), sesion));
    }

    @Test
    void admiteGuardadosYConsultasConcurrentesEnLaMismaSesion() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(8)) {
            var tareas = new ArrayList<Future<DesplazamientoDto>>();
            for (int i = 0; i < 64; i++) {
                var entrada = solicitud(i);
                tareas.add(hilos.submit(() -> {
                    inicio.await();
                    var ruta = ruta(entrada, 500.0, 600.0);
                    cache.guardar(entrada, ruta, sesion);
                    return cache.buscar(entrada, sesion).orElseThrow();
                }));
            }
            inicio.countDown();
            for (int i = 0; i < tareas.size(); i++) {
                assertEquals(ruta(solicitud(i), 500.0, 600.0), tareas.get(i).get(10, TimeUnit.SECONDS));
            }
        }
    }

    private void avanzar(long segundos) {
        ahora.updateAndGet(instante -> instante.plusSeconds(segundos));
    }

    private static Solicitud solicitud(int numero) {
        return new Solicitud(new PuntoRutaDto("referencia:inicio", 39.47, -0.37),
                new PuntoRutaDto("destino-" + numero, 39.48, -0.38));
    }

    private static DesplazamientoDto ruta(Solicitud solicitud, Double distancia, Double segundos) {
        return new DesplazamientoDto(solicitud.origen().id(), solicitud.destino().id(),
                distancia, segundos, EstadoDesplazamiento.DISPONIBLE, "openrouteservice");
    }
}
