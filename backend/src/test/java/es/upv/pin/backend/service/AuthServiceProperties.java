package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import es.upv.pin.backend.client.BrevoClient;
import es.upv.pin.backend.dto.RegistroDto;
import es.upv.pin.backend.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceProperties {
    private static final Instant BASE = Instant.parse("2026-11-26T12:00:00Z");

    @Property(tries = 100)
    void normalizacionCorreoEsIdempotenteYEnMinusculas(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String local,
            @ForAll @AlphaChars @StringLength(min = 2, max = 10) String dominio,
            @ForAll @IntRange(min = 0, max = 3) int espacios) {
        String pad = " ".repeat(espacios);
        String entrada = pad + local.toUpperCase() + "@" + dominio.toUpperCase() + ".COM" + pad;
        String normalizado = AuthService.normalizarCorreo(entrada);
        assertEquals(normalizado, AuthService.normalizarCorreo(normalizado));
        assertFalse(normalizado.startsWith(" "));
        assertFalse(normalizado.endsWith(" "));
        assertEquals(normalizado.toLowerCase(java.util.Locale.ROOT), normalizado);
    }

    @Property(tries = 100)
    void hashTokenEsDeterministaDe64CaracteresHexYDistintoDelToken(
            @ForAll @StringLength(min = 8, max = 64) String token) {
        String h1 = AuthService.hashToken(token);
        String h2 = AuthService.hashToken(token);
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
        assertTrue(h1.matches("[0-9a-f]{64}"));
        assertNotEquals(token, h1);
    }

    @Property(tries = 40)
    void registroRechazaConfirmacionDistintaSinTocarRepositorio(
            @ForAll @AlphaChars @StringLength(min = 8, max = 20) String clave,
            @ForAll @AlphaChars @StringLength(min = 8, max = 20) String otra) {
        Assume.that(!clave.equals(otra));
        UsuarioRepository repo = mock(UsuarioRepository.class);
        BrevoClient brevo = mock(BrevoClient.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        AuthService servicio = new AuthService(
                repo, new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
                brevo, eventos, mock(TransactionTemplate.class), "https://rute.example.com",
                Clock.fixed(BASE, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class, () ->
                servicio.registrar(new RegistroDto("Ana", "ana@upv.es", clave + "1!", otra + "2!")));
        verifyNoInteractions(repo, eventos);
    }

    @Property(tries = 30)
    void rechazaUrlsFrontendNoSeguras(@ForAll("urlsInvalidas") String url) {
        assertThrows(IllegalArgumentException.class, () -> new AuthService(
                mock(UsuarioRepository.class),
                new Argon2PasswordEncoder(16, 32, 1, 19456, 2),
                mock(BrevoClient.class),
                mock(ApplicationEventPublisher.class),
                mock(TransactionTemplate.class),
                url,
                Clock.fixed(BASE, ZoneOffset.UTC)));
    }

    @Property(tries = 60)
    void limiteAuthPermiteExactamenteNIntentosPorVentanaDe900Segundos(
            @ForAll @IntRange(min = 1, max = 12) int limite,
            @ForAll @AlphaChars @StringLength(min = 2, max = 8) String op,
            @ForAll @AlphaChars @StringLength(min = 3, max = 12) String ip) {
        var reloj = new Clock() {
            Instant t = BASE;
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId z) { return this; }
            @Override public Instant instant() { return t; }
        };
        LimiteAuthService servicio = new LimiteAuthService(reloj);
        for (int i = 0; i < limite; i++) {
            assertDoesNotThrow(() -> servicio.comprobar(op, ip, limite));
        }
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.comprobar(op, ip, limite));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());

        reloj.t = BASE.plusSeconds(900);
        assertDoesNotThrow(() -> servicio.comprobar(op, ip, limite));
    }

    @Provide
    Arbitrary<String> urlsInvalidas() {
        return Arbitraries.of(
                "http://rute.example.com",
                "ftp://localhost:5173",
                "https://user:pass@rute.example.com",
                "https://rute.example.com?q=1",
                "https://rute.example.com#frag",
                "/ruta-relativa");
    }
}
