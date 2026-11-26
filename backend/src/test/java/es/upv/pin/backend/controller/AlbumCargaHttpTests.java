package es.upv.pin.backend.controller;

import es.upv.pin.backend.PostgresTest;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "fotos.max-archivo-bytes=1024", "spring.servlet.multipart.max-file-size=1024B",
        "spring.servlet.multipart.max-request-size=2048B", "server.tomcat.max-swallow-size=8192B"
})
class AlbumCargaHttpTests extends PostgresTest {
    @LocalServerPort int puerto;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @DynamicPropertySource static void carpeta(DynamicPropertyRegistry propiedades) throws Exception {
        String temporal = Files.createTempDirectory("rute-carga-http-").toString();
        propiedades.add("fotos.directorio", () -> temporal);
    }

    @Test void elContenedorRechazaElExcesoConUnMensajeAntesDeAlmacenar() throws Exception {
        String correo = UUID.randomUUID()+"@example.test";
        jdbc.sql("INSERT INTO usuario(nombre,correo,password_hash,correo_verificado) VALUES('Prueba HTTP',:correo,:hash,true)")
                .param("correo",correo).param("hash",encoder.encode("Clave de prueba 2026!")).update();
        var cookies = new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        try (var cliente = HttpClient.newBuilder().cookieHandler(cookies).version(HttpClient.Version.HTTP_1_1).build()) {
            String base = "http://localhost:"+puerto;
            String token = csrf(cliente,base);
            var login = cliente.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
                    .header("X-CSRF-TOKEN",token).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\""+correo+"\",\"contrasena\":\"Clave de prueba 2026!\"}")).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,login.statusCode());
            token = csrf(cliente,base);
            String limite = "limite-rute-"+UUID.randomUUID();
            byte[] cuerpo = ("--"+limite+"\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"grande.png\"\r\nContent-Type: image/png\r\n\r\n"
                    + "x".repeat(4096)+"\r\n--"+limite+"--\r\n").getBytes(StandardCharsets.UTF_8);
            var respuesta = cliente.send(HttpRequest.newBuilder(URI.create(base+"/api/albumes/"+UUID.randomUUID()+"/fotos"))
                    .header("X-CSRF-TOKEN",token).header("Content-Type","multipart/form-data; boundary="+limite)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo)).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(413,respuesta.statusCode());
            assertTrue(json.readTree(respuesta.body()).path("mensaje").asText().contains("tamaño permitido"));
        }
    }
    private String csrf(HttpClient cliente,String base) throws Exception {
        var respuesta = cliente.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        return json.readTree(respuesta.body()).path("token").asText();
    }
}
