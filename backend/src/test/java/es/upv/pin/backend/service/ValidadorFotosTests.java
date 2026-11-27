package es.upv.pin.backend.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

public class ValidadorFotosTests {
    private final ValidadorFotos validador = new ValidadorFotos(10485760, 100, 40000000);

    public static byte[] imagen(Color color) throws IOException {
        var imagen = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        var pincel = imagen.createGraphics();
        pincel.setColor(color); pincel.fillRect(0, 0, 900, 600); pincel.dispose();
        var salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "PNG", salida);
        return salida.toByteArray();
    }

    @Test void conservaElOriginalYGeneraUnaMiniaturaLegible() throws Exception {
        byte[] original = imagen(Color.RED);
        var foto = validador.preparar(new MockMultipartFile("foto", "C:\\fakepath\\vacaciones.png", "application/octet-stream", original));
        assertEquals("vacaciones.png", foto.nombre());
        assertEquals("image/png", foto.mime());
        assertArrayEquals(original, foto.original());
        var miniatura = ImageIO.read(new ByteArrayInputStream(foto.miniatura()));
        assertEquals(480, miniatura.getWidth());
        assertEquals(320, miniatura.getHeight());
        assertTrue(new Color(miniatura.getRGB(50, 50)).getRed() > 240);
    }

    @Test void aceptaJpegReal() throws Exception {
        var salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "JPEG", salida);
        assertEquals("image/jpeg", validador.preparar(new MockMultipartFile("foto", "foto.jpeg", "image/jpeg", salida.toByteArray())).mime());
    }

    @Test void rechazaArchivosVaciosCorruptosYFormatosDisfrazados() throws Exception {
        for (var archivo : new MockMultipartFile[] {
            new MockMultipartFile("foto", "foto.png", "image/png", new byte[0]),
            new MockMultipartFile("foto", "foto.png", "image/png", "no soy una imagen".getBytes()),
            new MockMultipartFile("foto", "foto.svg", "image/svg+xml", "<svg/>".getBytes()),
            new MockMultipartFile("foto", "foto.jpg", "image/jpeg", imagen(Color.BLUE))
        }) assertThrows(ResponseStatusException.class, () -> validador.preparar(archivo));
    }

    @Test void compruebaTantoTamanoAnunciadoComoBytesLeidos() throws Exception {
        var pequeno = new ValidadorFotos(50, 100, 40000000);
        byte[] bytes = imagen(Color.RED);
        assertThrows(ResponseStatusException.class, () -> pequeno.preparar(new MockMultipartFile("foto", "foto.png", "image/png", bytes)));
        var tamanoFalso = new MockMultipartFile("foto", "foto.png", "image/png", bytes) {
            @Override public long getSize() { return 1; }
        };
        assertThrows(ResponseStatusException.class, () -> pequeno.preparar(tamanoFalso));
    }

    @Test void limitaPixelesAntesDeDecodificarLaImagenCompleta() throws Exception {
        var pequeno = new ValidadorFotos(10485760, 100, 1000);
        byte[] bytes = imagen(Color.RED);
        var error = assertThrows(ResponseStatusException.class, () -> pequeno.preparar(new MockMultipartFile("foto", "foto.png", "image/png", bytes)));
        assertTrue(error.getReason().contains("píxeles"));
    }
}
