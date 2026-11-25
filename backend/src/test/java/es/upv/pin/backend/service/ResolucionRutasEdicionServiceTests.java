package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.service.NecesidadesRutasEdicionService.Solicitud;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class ResolucionRutasEdicionServiceTests {
    private final CacheRutasEdicionService cache = new CacheRutasEdicionService();
    private final RutasGuardadasEdicionService guardadas = mock(RutasGuardadasEdicionService.class);
    private final OpenRouteServiceClient cliente = mock(OpenRouteServiceClient.class);
    private final ViajeGuardadoDto viaje = mock(ViajeGuardadoDto.class);
    private final MockHttpSession sesion = new MockHttpSession();
    private final ResolucionRutasEdicionService servicio = new ResolucionRutasEdicionService(cache, guardadas, cliente);
    private final PuntoRutaDto origen = new PuntoRutaDto("origen", 39.47, -0.37);
    private final Solicitud a = new Solicitud(origen, destino(0));
    private final Solicitud b = new Solicitud(origen, destino(1));
    private final Solicitud c = new Solicitud(origen, destino(2));

    @BeforeEach
    void preparar() {
        when(guardadas.recuperar(any(), anyList())).thenReturn(List.of());
        when(cliente.calcularDesde(any(), anyList())).thenAnswer(invocacion -> {
            List<PuntoRutaDto> destinos = invocacion.getArgument(1);
            return matriz(destinos.size());
        });
    }

    @Test
    void usaLaCacheSinConsultarElViajeNiElProveedor() {
        var ruta = ruta(a, 123.0);
        cache.guardar(a, ruta, sesion);

        var resultado = servicio.resolver(viaje, List.of(a), sesion);

        assertEquals(List.of(ruta), resultado.rutas());
        assertFalse(resultado.consultaFallida());
        verifyNoInteractions(guardadas, cliente);
    }

    @Test
    void reutilizaLaRutaGuardadaYLaIncorporaALaCache() {
        var ruta = ruta(a, 123.0);
        when(guardadas.recuperar(viaje, List.of(a))).thenReturn(List.of(ruta));

        assertEquals(List.of(ruta), servicio.resolver(viaje, List.of(a), sesion).rutas());
        assertEquals(ruta, cache.buscar(a, sesion).orElseThrow());
        servicio.resolver(viaje, List.of(a), sesion);

        verify(guardadas, times(1)).recuperar(viaje, List.of(a));
        verifyNoInteractions(cliente);
    }

    @Test
    void mezclaCacheGuardadasYProveedorConservandoElOrdenSolicitado() {
        cache.guardar(a, ruta(a, 111.0), sesion);
        when(guardadas.recuperar(viaje, List.of(c, b))).thenReturn(List.of(ruta(b, 222.0)));

        var resultado = servicio.resolver(viaje, List.of(c, a, b), sesion);

        assertEquals(List.of(c.destino().id(), a.destino().id(), b.destino().id()),
                resultado.rutas().stream().map(DesplazamientoDto::destinoId).toList());
        assertEquals(111.0, resultado.rutas().get(1).distanciaMetros().doubleValue());
        assertEquals(222.0, resultado.rutas().get(2).distanciaMetros().doubleValue());
        assertFalse(resultado.consultaFallida());
        verify(cliente, times(1)).calcularDesde(origen, List.of(c.destino()));
        assertTrue(cache.buscar(c, sesion).isPresent());
    }

    @Test
    void agrupaPorOrigenYEliminaSolicitudesRepetidas() {
        var otroOrigen = new PuntoRutaDto("otro-origen", 40.0, -4.0);
        var otra = new Solicitud(otroOrigen, c.destino());

        var resultado = servicio.resolver(viaje, List.of(a, otra, b, a), sesion);

        assertEquals(List.of(a.destino().id(), c.destino().id(), b.destino().id()),
                resultado.rutas().stream().map(DesplazamientoDto::destinoId).toList());
        verify(cliente).calcularDesde(origen, List.of(a.destino(), b.destino()));
        verify(cliente).calcularDesde(otroOrigen, List.of(c.destino()));
        verify(cliente, times(2)).calcularDesde(any(), anyList());
    }

    @Test
    void divideUnOrigenConMasDeCincuentaDestinosEnLotesLimitados() {
        var solicitudes = IntStream.range(0, 51).mapToObj(i -> new Solicitud(origen, destino(i))).toList();

        var resultado = servicio.resolver(viaje, solicitudes, sesion);

        assertEquals(51, resultado.rutas().size());
        assertFalse(resultado.consultaFallida());
        verify(cliente).calcularDesde(origen, solicitudes.subList(0, 50).stream().map(Solicitud::destino).toList());
        verify(cliente).calcularDesde(origen, List.of(solicitudes.getLast().destino()));
        verify(cliente, times(2)).calcularDesde(any(), anyList());
    }

    @Test
    void unaSegundaResolucionNoRepiteLasConsultasCorrectas() {
        var primero = servicio.resolver(viaje, List.of(a, b), sesion);
        var segundo = servicio.resolver(viaje, List.of(a, b), sesion);

        assertEquals(primero, segundo);
        verify(cliente, times(1)).calcularDesde(origen, List.of(a.destino(), b.destino()));
        verify(guardadas, times(1)).recuperar(viaje, List.of(a, b));
    }

    @Test
    void dosSesionesNoCompartenLosResultadosTemporales() {
        servicio.resolver(viaje, List.of(a), sesion);
        servicio.resolver(viaje, List.of(a), new MockHttpSession());
        verify(cliente, times(2)).calcularDesde(origen, List.of(a.destino()));
    }

    @Test
    void lasCoordenadasAusentesNoProvocanPeticionesNiTiemposInventados() {
        var sinCoordenadas = new Solicitud(origen, new PuntoRutaDto("sin-coordenadas", null, -0.37));

        var resultado = servicio.resolver(viaje, List.of(sinCoordenadas), sesion);

        assertEquals(EstadoDesplazamiento.COORDENADAS_AUSENTES, resultado.rutas().getFirst().estado());
        assertNull(resultado.rutas().getFirst().distanciaMetros());
        assertNull(resultado.rutas().getFirst().duracionSegundos());
        assertFalse(resultado.consultaFallida());
        verifyNoInteractions(guardadas, cliente);
    }

    @Test
    void puntosConLaMismaUbicacionNoNecesitanConsultarUnaRuta() {
        var mismaUbicacion = new Solicitud(origen, new PuntoRutaDto("otro-id", origen.latitud(), origen.longitud()));

        var resultado = servicio.resolver(viaje, List.of(mismaUbicacion), sesion);

        assertEquals(EstadoDesplazamiento.DISPONIBLE, resultado.rutas().getFirst().estado());
        assertEquals(0.0, resultado.rutas().getFirst().duracionSegundos().doubleValue());
        assertEquals("misma ubicación", resultado.rutas().getFirst().fuente());
        verifyNoInteractions(guardadas, cliente);
    }

    @Test
    void unaRutaNoDisponibleNoSeGuardaEnCacheNiSeConfundeConUnFalloHttp() {
        var nulos = Collections.<Double>singletonList(null);
        when(cliente.calcularDesde(origen, List.of(a.destino())))
                .thenReturn(new RespuestaMatrizOrsDto(List.of(nulos), List.of(nulos)));

        var resultado = servicio.resolver(viaje, List.of(a), sesion);

        assertEquals(EstadoDesplazamiento.RUTA_NO_DISPONIBLE, resultado.rutas().getFirst().estado());
        assertNull(resultado.rutas().getFirst().duracionSegundos());
        assertFalse(resultado.consultaFallida());
        assertTrue(cache.buscar(a, sesion).isEmpty());
    }

    @Test
    void alFallarElProveedorConservaLoCalculadoYNoConsultaLosGruposRestantes() {
        var otroOrigen = new PuntoRutaDto("otro-origen", 40.0, -4.0);
        var tercerOrigen = new PuntoRutaDto("tercer-origen", 41.0, -5.0);
        var otra = new Solicitud(otroOrigen, b.destino());
        var tercera = new Solicitud(tercerOrigen, c.destino());
        when(cliente.calcularDesde(otroOrigen, List.of(b.destino())))
                .thenThrow(new IllegalStateException("Fallo simulado"));

        var resultado = servicio.resolver(viaje, List.of(a, otra, tercera), sesion);

        assertTrue(resultado.consultaFallida());
        assertEquals(EstadoDesplazamiento.DISPONIBLE, resultado.rutas().getFirst().estado());
        assertEquals(EstadoDesplazamiento.RUTA_NO_DISPONIBLE, resultado.rutas().get(1).estado());
        assertEquals(EstadoDesplazamiento.RUTA_NO_DISPONIBLE, resultado.rutas().get(2).estado());
        assertTrue(cache.buscar(a, sesion).isPresent());
        assertTrue(cache.buscar(otra, sesion).isEmpty());
        assertTrue(cache.buscar(tercera, sesion).isEmpty());
        verify(cliente, never()).calcularDesde(eq(tercerOrigen), anyList());

        doReturn(matriz(1)).when(cliente).calcularDesde(otroOrigen, List.of(b.destino()));
        assertFalse(servicio.resolver(viaje, List.of(a, otra, tercera), sesion).consultaFallida());
        verify(cliente, times(1)).calcularDesde(origen, List.of(a.destino()));
    }

    @Test
    void noConsultaConSolicitudesVaciasNiAceptaCoordenadasContradictorias() {
        assertTrue(servicio.resolver(viaje, List.of(), sesion).rutas().isEmpty());
        var contradictoria = new Solicitud(new PuntoRutaDto(origen.id(), 42.0, -6.0), b.destino());
        assertThrows(IllegalArgumentException.class,
                () -> servicio.resolver(viaje, List.of(a, contradictoria), sesion));
        verifyNoInteractions(guardadas, cliente);
    }

    @Test
    void evitaConsultasSimultaneasEnLaMismaSesionYLiberaElControlAlTerminar() throws Exception {
        var consultando = new CountDownLatch(1);
        var terminar = new CountDownLatch(1);
        when(cliente.calcularDesde(origen, List.of(a.destino()))).thenAnswer(invocacion -> {
            consultando.countDown();
            if (!terminar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Espera agotada");
            return matriz(1);
        });
        try (var hilo = Executors.newSingleThreadExecutor()) {
            var primera = hilo.submit(() -> servicio.resolver(viaje, List.of(a), sesion));
            try {
                assertTrue(consultando.await(5, TimeUnit.SECONDS));
                var error = assertThrows(ResponseStatusException.class,
                        () -> servicio.resolver(viaje, List.of(a), sesion));
                assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
            } finally { terminar.countDown(); }
            assertFalse(primera.get(5, TimeUnit.SECONDS).consultaFallida());
        }
        assertFalse(servicio.resolver(viaje, List.of(a), sesion).consultaFallida());
        verify(cliente, times(1)).calcularDesde(origen, List.of(a.destino()));
    }

    @Test
    void elPlazoSeComparteEntreResolucionesYDetieneNuevasConsultas() {
        var reloj = new java.util.concurrent.atomic.AtomicLong();
        var tiempo = new TiempoEdicion(reloj::get);
        when(cliente.calcularDesde(any(), anyList())).thenAnswer(invocacion -> {
            reloj.addAndGet(java.time.Duration.ofSeconds(25).toNanos());
            return matriz(((List<?>) invocacion.getArgument(1)).size());
        });
        TiempoEdicion.ejecutar(tiempo, () -> {
            for (int i=0; i<6; i++) {
                var solicitud = new Solicitud(new PuntoRutaDto("origen-"+i,39.4+i*.001,-.3),destino(i));
                var resultado = servicio.resolver(viaje,List.of(solicitud),sesion);
                assertEquals(i>=4,resultado.consultaFallida());
            }
            return null;
        });
        verify(cliente,times(4)).calcularDesde(any(),anyList());
        assertEquals(java.time.Duration.ofSeconds(100).toNanos(),reloj.get());
    }

    @Test
    void laCacheSigueDisponibleSinNuevasLlamadasAlAgotarTiempoDeConsultas() {
        var reloj = new java.util.concurrent.atomic.AtomicLong();
        var tiempo = new TiempoEdicion(reloj::get);
        cache.guardar(a,ruta(a,100),sesion);
        reloj.set(java.time.Duration.ofSeconds(100).toNanos());
        var resultado = TiempoEdicion.ejecutar(tiempo,()->servicio.resolver(viaje,List.of(a),sesion));
        assertFalse(resultado.consultaFallida());
        assertEquals(EstadoDesplazamiento.DISPONIBLE,resultado.rutas().getFirst().estado());
        verifyNoInteractions(cliente);
    }

    private static PuntoRutaDto destino(int i) {
        return new PuntoRutaDto("destino-" + i, 39.48 + i * .001, -0.38);
    }

    private static DesplazamientoDto ruta(Solicitud solicitud, double distancia) {
        return new DesplazamientoDto(solicitud.origen().id(), solicitud.destino().id(), distancia,
                600.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice");
    }

    private static RespuestaMatrizOrsDto matriz(int cantidad) {
        return new RespuestaMatrizOrsDto(List.of(Collections.nCopies(cantidad, 500.0)),
                List.of(Collections.nCopies(cantidad, 600.0)));
    }
}
