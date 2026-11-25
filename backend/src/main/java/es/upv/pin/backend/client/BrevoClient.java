package es.upv.pin.backend.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

@Component
public class BrevoClient {
    private final String clave;
    private final String remitente;
    private final String nombreRemitente;
    private final RestClient cliente;

    @org.springframework.beans.factory.annotation.Autowired
    public BrevoClient(@Value("${brevo.api-key}") String clave, @Value("${brevo.sender-email}") String remitente,
            @Value("${brevo.sender-name}") String nombreRemitente) {
        this(clave, remitente, nombreRemitente, nuevoCliente());
    }
    public BrevoClient(String clave, String remitente, String nombreRemitente, RestClient cliente) {
        this.clave = clave;
        this.remitente = remitente;
        this.nombreRemitente = nombreRemitente;
        this.cliente = cliente;
    }
    public boolean disponible() { return !clave.isBlank() && !remitente.isBlank() && !nombreRemitente.isBlank(); }

    public void enviarVerificacion(String correo, String nombre, String enlace) {
        if (!disponible()) throw new IllegalStateException("El envío de correo no está configurado.");
        String contenido = "<h1>Verifica tu cuenta de Rute</h1><p>Hola, " + HtmlUtils.htmlEscape(nombre)
                + ".</p><p>Confirma tu correo para empezar a planificar tus viajes.</p><p><a href=\""
                + HtmlUtils.htmlEscape(enlace) + "\">Verificar mi correo</a></p>"
                + "<p>El enlace caduca en 30 minutos y solo puede utilizarse una vez. "
                + "Te pediremos la contraseña que elegiste al registrarte para confirmar la solicitud.</p>"
                + "<p>Si no has solicitado esta cuenta, ignora este mensaje.</p>";
        try {
            cliente.post().uri("/v3/smtp/email").header("api-key", clave)
                    .body(Map.of("sender", Map.of("email", remitente, "name", nombreRemitente),
                            "to", List.of(Map.of("email", correo)), "subject", "Verifica tu cuenta de Rute", "htmlContent", contenido))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException error) {
            // No propagar respuestas, URLs ni cabeceras que puedan contener datos privados.
            throw new IllegalStateException("El proveedor no ha confirmado el envío del correo.");
        }
    }
    private static RestClient nuevoCliente() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl("https://api.brevo.com").requestFactory(factory).build();
    }
}
