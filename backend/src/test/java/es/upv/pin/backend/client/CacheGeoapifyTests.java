package es.upv.pin.backend.client;

import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheGeoapifyTests {
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-24T10:00:00Z"));
    private final Clock reloj = mock(Clock.class);
    private final RespuestaLugaresGeoapifyDto vacia = new RespuestaLugaresGeoapifyDto(List.of());
    private CacheGeoapify.Consulta clave(String nombre) { return new CacheGeoapify.Consulta("places", "madrid", "tourism.sights", 100, 0, nombre); }

    @Test void reutilizaCaducaYAcotaElNumeroDeEntradas() {
        when(reloj.instant()).thenAnswer(inv -> ahora.get());
        var cache = new CacheGeoapify(reloj, Duration.ofMinutes(15), 2);
        var peticiones = new AtomicInteger();
        java.util.function.Supplier<RespuestaLugaresGeoapifyDto> cargar = () -> { peticiones.incrementAndGet(); return vacia; };
        cache.obtener(clave("a"), cargar);
        cache.obtener(clave("a"), cargar);
        assertEquals(1, peticiones.get());
        cache.obtener(clave("b"), cargar);
        cache.obtener(clave("c"), cargar);
        cache.obtener(clave("a"), cargar); // a se expulsó al superar dos entradas.
        assertEquals(4, peticiones.get());
        ahora.set(ahora.get().plus(Duration.ofMinutes(15)));
        cache.obtener(clave("a"), cargar);
        assertEquals(5, peticiones.get());
    }

    @Test void noGuardaFallosNiRespuestasInvalidasYPermiteReintentar() {
        var cache = new CacheGeoapify();
        assertThrows(IllegalStateException.class, () -> cache.obtener(clave("a"), () -> { throw new IllegalStateException("Proveedor caído"); }));
        assertThrows(IllegalStateException.class, () -> cache.obtener(clave("a"), () -> new RespuestaLugaresGeoapifyDto(null)));
        assertEquals(vacia, cache.obtener(clave("a"), () -> vacia));
    }

    @Test void unaBusquedaSimultaneaEquivalenteSoloConsultaUnaVez() throws Exception {
        var cache = new CacheGeoapify();
        var peticiones = new AtomicInteger();
        var dentro = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.function.Supplier<RespuestaLugaresGeoapifyDto> cargar = () -> {
                peticiones.incrementAndGet(); dentro.countDown();
                try { assertTrue(liberar.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return vacia;
            };
            var primero = ejecutor.submit(() -> cache.obtener(clave("a"), cargar));
            assertTrue(dentro.await(2, TimeUnit.SECONDS));
            var segundo = ejecutor.submit(() -> cache.obtener(clave("a"), cargar));
            liberar.countDown();
            assertEquals(vacia, primero.get(3, TimeUnit.SECONDS));
            assertEquals(vacia, segundo.get(3, TimeUnit.SECONDS));
            assertEquals(1, peticiones.get());
        } finally { liberar.countDown(); }
    }

    @Test void tresPeticionesPuedenSolaparsePeroLaCuartaEspera() throws Exception {
        var limite = new LimiteConsultasGeoapify(3, 0);
        var tresDentro = new CountDownLatch(3);
        var liberar = new CountDownLatch(1);
        var cuartaDentro = new CountDownLatch(1);
        try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tareas = java.util.stream.IntStream.range(0, 3).mapToObj(i -> ejecutor.submit(() -> limite.ejecutar(() -> {
                tresDentro.countDown();
                try { assertTrue(liberar.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
                return i;
            }))).toList();
            assertTrue(tresDentro.await(2, TimeUnit.SECONDS));
            var cuarta = ejecutor.submit(() -> limite.ejecutar(() -> { cuartaDentro.countDown(); return 4; }));
            assertFalse(cuartaDentro.await(50, TimeUnit.MILLISECONDS));
            liberar.countDown();
            for (var tarea : tareas) tarea.get(3, TimeUnit.SECONDS);
            assertEquals(4, cuarta.get(3, TimeUnit.SECONDS));
        } finally { liberar.countDown(); }
    }
}
