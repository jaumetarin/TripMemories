package es.upv.pin.backend.service;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class AlmacenamientoFotosLocal implements AlmacenamientoFotos {
    private final Path raiz;
    public AlmacenamientoFotosLocal(@Value("${fotos.directorio}") String directorio) {
        try {
            raiz = Files.createDirectories(Path.of(directorio).toAbsolutePath().normalize()).toRealPath();
        } catch (IOException e) { throw new IllegalStateException("No se puede preparar el almacenamiento privado de fotos."); }
    }
    private Path resolver(String clave) {
        if (clave == null || !clave.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/(original|miniatura)"))
            throw new IllegalArgumentException("Clave de archivo no válida.");
        return raiz.resolve(clave).normalize();
    }
    private void comprobarCarpeta(Path archivo) throws IOException {
        if (!archivo.getParent().toRealPath().startsWith(raiz)) throw new IOException("Ubicación no permitida");
    }
    @Override public void guardar(String clave, byte[] contenido) {
        var archivo = resolver(clave);
        try {
            Files.createDirectories(archivo.getParent());
            comprobarCarpeta(archivo);
            Files.write(archivo, contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) { throw new IllegalStateException("No se ha podido almacenar la foto."); }
    }
    @Override public Resource leer(String clave) {
        var archivo = resolver(clave);
        try {
            comprobarCarpeta(archivo);
            if (!Files.isRegularFile(archivo, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(archivo)) throw new IOException();
            return new FileSystemResource(archivo);
        } catch (IOException e) { throw new IllegalStateException("El archivo no está disponible en el almacenamiento."); }
    }
    @Override public void borrar(String clave) {
        var archivo = resolver(clave);
        try {
            if (!Files.exists(archivo.getParent(), LinkOption.NOFOLLOW_LINKS)) return;
            comprobarCarpeta(archivo);
            if (Files.isSymbolicLink(archivo)) throw new IOException();
            Files.deleteIfExists(archivo);
            // La otra versión puede seguir presente hasta terminar el borrado.
            try { Files.delete(archivo.getParent()); } catch (DirectoryNotEmptyException ignored) { }
        } catch (NoSuchFileException e) {
            // Otro borrado equivalente puede haber retirado ya el archivo o su carpeta.
        } catch (IOException e) { throw new IllegalStateException("No se ha podido eliminar el archivo del almacenamiento."); }
    }
}
