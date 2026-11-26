package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.ContenidoAlbumDto.LimitesDto;
import es.upv.pin.backend.model.FotoPreparada;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ValidadorFotos {
    private final LimitesDto limites;
    public ValidadorFotos(@Value("${fotos.max-archivo-bytes}") long maxArchivo,
            @Value("${fotos.max-por-album}") int maxFotos, @Value("${fotos.cuota-bytes}") long cuota,
            @Value("${fotos.max-pixeles}") long pixeles) {
        if (maxArchivo < 1 || maxArchivo > 100 * 1024 * 1024 || maxFotos < 1 || cuota < 1 || pixeles < 1 || pixeles > 40000000)
            throw new IllegalArgumentException("Límites de fotos no válidos.");
        limites = new LimitesDto(List.of("JPEG", "PNG"), maxArchivo, maxFotos, cuota, pixeles);
    }
    public LimitesDto limites() { return limites; }
    public FotoPreparada preparar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) throw invalida("Selecciona una fotografía JPEG o PNG.");
        if (archivo.getSize() > limites.maxArchivoBytes()) throw invalida("La foto supera el tamaño máximo permitido.");
        String nombre = archivo.getOriginalFilename() == null ? "foto" : archivo.getOriginalFilename().replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (nombre.length() > 200) nombre = nombre.substring(nombre.length() - 200);
        String extension = nombre.toLowerCase(java.util.Locale.ROOT);
        if (!extension.endsWith(".jpg") && !extension.endsWith(".jpeg") && !extension.endsWith(".png"))
            throw invalida("Solo se admiten fotografías JPEG y PNG.");
        try (var entrada = archivo.getInputStream()) {
            byte[] original = entrada.readNBytes(Math.toIntExact(limites.maxArchivoBytes() + 1));
            if (original.length > limites.maxArchivoBytes()) throw invalida("La foto supera el tamaño máximo permitido.");
            try (var imagen = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
                var lectores = ImageIO.getImageReaders(imagen);
                if (!lectores.hasNext()) throw invalida("El archivo no contiene una imagen válida o está dañado.");
                var lector = lectores.next();
                try {
                    String formato = lector.getFormatName().toUpperCase(java.util.Locale.ROOT);
                    boolean png = formato.equals("PNG");
                    if (!png && !formato.equals("JPEG")) throw invalida("Solo se admiten fotografías JPEG y PNG.");
                    if (png != extension.endsWith(".png")) throw invalida("El formato real de la imagen no coincide con su extensión.");
                    boolean[] advertencia = {false};
                    lector.addIIOReadWarningListener((fuente, texto) -> advertencia[0] = true);
                    lector.setInput(imagen, true, true);
                    int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                    if (ancho < 1 || alto < 1 || (long) ancho * alto > limites.maxPixeles())
                        throw invalida("La imagen tiene demasiados píxeles. Reduce sus dimensiones antes de subirla.");
                    var leida = lector.read(0);
                    if (leida == null || advertencia[0]) throw invalida("La imagen está dañada o incompleta.");
                    double escala = Math.min(1, 480.0 / Math.max(ancho, alto));
                    var miniatura = new BufferedImage(Math.max(1, (int) (ancho * escala)), Math.max(1, (int) (alto * escala)), BufferedImage.TYPE_INT_RGB);
                    var pincel = miniatura.createGraphics();
                    try {
                        pincel.setColor(Color.WHITE); pincel.fillRect(0, 0, miniatura.getWidth(), miniatura.getHeight());
                        pincel.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        pincel.drawImage(leida, 0, 0, miniatura.getWidth(), miniatura.getHeight(), null);
                    } finally { pincel.dispose(); leida.flush(); }
                    var salida = new ByteArrayOutputStream();
                    if (!ImageIO.write(miniatura, "JPEG", salida)) throw new IOException();
                    return new FotoPreparada(nombre, png ? "image/png" : "image/jpeg", original, salida.toByteArray(), ancho, alto);
                } finally { lector.dispose(); }
            }
        } catch (IOException | IllegalArgumentException e) { throw invalida("No se puede leer la imagen. Comprueba que no esté dañada."); }
    }
    private static ResponseStatusException invalida(String mensaje) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje); }
}
