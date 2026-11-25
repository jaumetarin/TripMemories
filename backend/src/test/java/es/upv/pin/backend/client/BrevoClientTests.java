package es.upv.pin.backend.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrevoClientTests {
    @Test
    void enviaSoloHtmlConCabeceraYRemitenteSinInterpretarElNombreComoHtml() {
        var builder = RestClient.builder().baseUrl("https://api.brevo.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new BrevoClient("clave-simulada", "rute@example.test", "Rute", builder.build());
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "clave-simulada"))
                .andExpect(jsonPath("$.sender.email").value("rute@example.test"))
                .andExpect(jsonPath("$.to[0].email").value("ana@example.test"))
                .andExpect(jsonPath("$.subject").value("Verifica tu cuenta de Rute"))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(jsonPath("$.textContent").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));
        cliente.enviarVerificacion("ana@example.test", "<script>", "http://localhost:5173/#verificar-correo=simulado");
        servidor.verify();
    }

    @Test
    void respuestaDeErrorNoExponeElCuerpoDelProveedor() {
        var builder = RestClient.builder().baseUrl("https://api.brevo.com");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("clave y correo privados"));
        var cliente = new BrevoClient("clave-simulada", "rute@example.test", "Rute", builder.build());
        var error = assertThrows(IllegalStateException.class,
                () -> cliente.enviarVerificacion("ana@example.test", "Ana", "http://localhost:5173/#verificar-correo=simulado"));
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("privados"));
        servidor.verify();
    }

    @Test
    void sinClaveORemitenteNoHaceLlamadas() {
        var cliente = new BrevoClient("", "", "Rute", RestClient.create());
        assertFalse(cliente.disponible());
        assertThrows(IllegalStateException.class, () -> cliente.enviarVerificacion("a@example.test", "Ana", "enlace"));
    }
}
