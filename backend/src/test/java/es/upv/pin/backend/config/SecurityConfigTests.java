package es.upv.pin.backend.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTests {
    @Test
    void origenLocalConPuertoAdmiteAmbosNombresYProteccionCsrf() {
        var fuente = new SecurityConfig().corsConfigurationSource("http://localhost:5173");
        var cors = fuente.getCorsConfiguration(new MockHttpServletRequest("POST", "/api/itinerarios/validar-preferencias"));

        assertNotNull(cors);
        assertEquals(List.of("http://localhost:5173", "http://127.0.0.1:5173"), cors.getAllowedOrigins());
        assertEquals("http://127.0.0.1:5173", cors.checkOrigin("http://127.0.0.1:5173"));
        assertNull(cors.checkOrigin("http://localhost:5174"));
        assertTrue(cors.getAllowCredentials());
        assertTrue(cors.getAllowedHeaders().contains("X-CSRF-TOKEN"));
        assertTrue(cors.getAllowedMethods().containsAll(List.of("POST", "PUT", "DELETE")));
    }

    @Test
    void origenLocalSinPuertoNoIntroduceUnPuertoInvalido() {
        var fuente = new SecurityConfig().corsConfigurationSource("http://127.0.0.1");
        var cors = fuente.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/auth/csrf"));

        assertNotNull(cors);
        assertEquals(List.of("http://localhost", "http://127.0.0.1"), cors.getAllowedOrigins());
        assertNull(cors.checkOrigin("http://127.0.0.1:5173"));
    }

    @Test
    void origenRemotoAdmiteSoloElOrigenConfigurado() {
        var fuente = new SecurityConfig().corsConfigurationSource("https://rute.example.com");
        var cors = fuente.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/auth/sesion"));

        assertNotNull(cors);
        assertEquals(List.of("https://rute.example.com"), cors.getAllowedOrigins());
        assertEquals("https://rute.example.com", cors.checkOrigin("https://rute.example.com"));
        assertNull(cors.checkOrigin("https://otro.example.com"));
        assertNull(cors.checkOrigin("http://localhost:5173"));
        assertNull(cors.checkOrigin("null"));
    }
}
