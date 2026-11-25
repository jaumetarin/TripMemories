package es.upv.pin.backend.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class TiempoEdicionTests {
    @Test void reservaElTimeoutDeUnaConsultaYRechazaUnPlazoAgotado() {
        var reloj = new AtomicLong();
        var tiempo = new TiempoEdicion(reloj::get);
        assertTrue(tiempo.permiteConsulta());
        reloj.set(Duration.ofSeconds(95).toNanos());
        assertFalse(tiempo.permiteConsulta());
        assertDoesNotThrow(tiempo::comprobar);
        reloj.set(Duration.ofSeconds(120).toNanos());
        assertThrows(ResponseStatusException.class, tiempo::comprobar);
    }
    @Test void comparteElPlazoAnidadoYLoLimpiaInclusoTrasUnError() {
        var reloj = new AtomicLong();
        var tiempo = new TiempoEdicion(reloj::get);
        assertThrows(IllegalStateException.class, () -> TiempoEdicion.ejecutar(tiempo, () -> {
            assertSame(tiempo, TiempoEdicion.actual());
            TiempoEdicion.ejecutar(() -> {
                assertSame(tiempo, TiempoEdicion.actual());
                reloj.set(Duration.ofSeconds(100).toNanos());
                assertFalse(TiempoEdicion.actual().permiteConsulta());
                return null;
            });
            throw new IllegalStateException("Error simulado");
        }));
        assertNotSame(tiempo, TiempoEdicion.actual());
        assertTrue(TiempoEdicion.actual().permiteConsulta());
    }
}
