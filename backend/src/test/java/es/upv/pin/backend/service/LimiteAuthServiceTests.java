package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LimiteAuthServiceTests {
    @Test
    void limitaPorOperacionEIpYRenuevaLaVentana() {
        var reloj = new RelojPrueba();
        var limites = new LimiteAuthService(reloj);
        limites.comprobar("login", "ip-a", 1);
        var error = assertThrows(ResponseStatusException.class, () -> limites.comprobar("login", "ip-a", 1));
        assertEquals(429, error.getStatusCode().value());
        assertDoesNotThrow(() -> limites.comprobar("registro", "ip-a", 1));
        assertDoesNotThrow(() -> limites.comprobar("login", "ip-b", 1));
        reloj.ahora = reloj.ahora.plusSeconds(900);
        assertDoesNotThrow(() -> limites.comprobar("login", "ip-a", 1));
    }
    private static class RelojPrueba extends Clock {
        Instant ahora = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zona) { return this; }
        public Instant instant() { return ahora; }
    }
}
