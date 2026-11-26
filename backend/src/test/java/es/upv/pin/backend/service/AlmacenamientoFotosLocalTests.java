package es.upv.pin.backend.service;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AlmacenamientoFotosLocalTests {
    @TempDir Path carpeta;

    @Test void conservaDosArchivosYElBorradoSePuedeReintentar() throws Exception {
        var archivos = new AlmacenamientoFotosLocal(carpeta.toString());
        String id = UUID.randomUUID().toString();
        archivos.guardar(id + "/original", new byte[] {1,2,3});
        archivos.guardar(id + "/miniatura", new byte[] {4,5});
        assertArrayEquals(new byte[] {1,2,3}, archivos.leer(id + "/original").getContentAsByteArray());
        assertThrows(IllegalStateException.class, () -> archivos.guardar(id + "/original", new byte[] {9}));
        archivos.borrar(id + "/original");
        assertArrayEquals(new byte[] {4,5}, archivos.leer(id + "/miniatura").getContentAsByteArray());
        archivos.borrar(id + "/miniatura");
        archivos.borrar(id + "/original");
        assertThrows(IllegalStateException.class, () -> archivos.leer(id + "/miniatura"));
    }

    @Test void rechazaNombresDeUsuarioRutasAbsolutasYTraversal() {
        var archivos = new AlmacenamientoFotosLocal(carpeta.toString());
        for (String clave : new String[] {"../foto", "C:/Windows/foto", "/foto", "foto.png", UUID.randomUUID()+"/../../foto"}) {
            assertThrows(IllegalArgumentException.class, () -> archivos.guardar(clave, new byte[] {1}));
            assertThrows(IllegalArgumentException.class, () -> archivos.leer(clave));
            assertThrows(IllegalArgumentException.class, () -> archivos.borrar(clave));
        }
    }
}
