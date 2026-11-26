package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.repository.AlbumRepository;
import es.upv.pin.backend.repository.AlbumRepository.FotoPrivada;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlbumService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AlbumService.class);
    private final AlbumRepository albumes;
    private final AlmacenamientoFotos archivos;
    private final ValidadorFotos validador;
    private final TransactionTemplate transacciones;
    private final Semaphore subidas = new Semaphore(2);
    public AlbumService(AlbumRepository albumes, AlmacenamientoFotos archivos, ValidadorFotos validador, TransactionTemplate transacciones) {
        this.albumes=albumes; this.archivos=archivos; this.validador=validador; this.transacciones=transacciones;
    }
    public List<AlbumDto> listar(UUID usuario) { return albumes.listar(usuario); }
    private AlbumDto album(UUID usuario, UUID id) {
        return albumes.propio(usuario,id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"No se ha encontrado el álbum."));
    }
    public ContenidoAlbumDto abrir(UUID usuario, UUID id) {
        var album=album(usuario,id);
        return new ContenidoAlbumDto(album,albumes.fotos(usuario,id),validador.limites(),albumes.bytesUsados(usuario));
    }
    private FotoPrivada foto(UUID usuario, UUID id) {
        return albumes.fotoPropia(usuario,id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"No se ha encontrado la foto."));
    }
    public FotoDto subir(UUID usuario, UUID albumId, MultipartFile archivo) {
        comprobarSubida(usuario,albumId);
        if (!subidas.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Hay otras subidas en curso. Espera unos instantes y reintenta.");
        try {
            var preparada=validador.preparar(archivo);
            UUID id=UUID.randomUUID();
            transacciones.executeWithoutResult(tx -> {
                albumes.bloquearCuenta(usuario); comprobarSubida(usuario,albumId);
                if (albumes.numeroFotos(albumId)>=validador.limites().maxFotosAlbum())
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"El álbum ha alcanzado su límite de fotos.");
                if (preparada.bytesTotales()>validador.limites().cuotaBytes()-albumes.bytesUsados(usuario))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"No queda espacio suficiente en tu cuenta para esta foto y su miniatura.");
                albumes.reservar(id,albumId,preparada);
            });
            try {
                archivos.guardar(id+"/original",preparada.original());
                archivos.guardar(id+"/miniatura",preparada.miniatura());
                if (!albumes.estado(usuario,id,"DISPONIBLE","PENDIENTE"))
                    throw new IllegalStateException("No se pudo confirmar el almacenamiento.");
            } catch (RuntimeException fallo) {
                // Si una confirmación ya se hubiera aplicado, conservar la foto completa.
                var actual=albumes.fotoPropia(usuario,id);
                if (actual.isEmpty()) {
                    // Un borrado pudo retirar la reserva antigua mientras aún se escribían sus archivos.
                    // El UUID es exclusivo de esta subida; ninguna otra foto puede usar estas claves.
                    if (!limpiar(id+"/original",id+"/miniatura"))
                        LOG.error("No se pudieron limpiar los archivos de una subida sin registro: {}",id);
                } else if (actual.get().datos().estado().equals("ELIMINANDO")) {
                    // La eliminación de una foto puede seguir entre el borrado físico y el de su fila.
                    if (!limpiar(id+"/original",id+"/miniatura"))
                        LOG.error("No se pudieron limpiar los archivos de una foto en eliminación: {}",id);
                } else if (actual.get().datos().estado().equals("PENDIENTE")) {
                    boolean limpio=limpiar(id+"/original",id+"/miniatura");
                    if (limpio) albumes.eliminar(usuario,id,"PENDIENTE");
                    else albumes.estado(usuario,id,"ERROR","PENDIENTE");
                }
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No se ha podido confirmar la subida. Revisa el álbum antes de reintentar; ninguna subida pendiente se muestra como disponible.");
            }
            return foto(usuario,id).datos();
        } finally { subidas.release(); }
    }
    private void comprobarSubida(UUID usuario, UUID albumId) {
        if (album(usuario,albumId).eliminando())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"El álbum se está eliminando y no admite nuevas fotos.");
    }
    private boolean limpiar(String original, String miniatura) {
        boolean correcto=true;
        try { archivos.borrar(original); } catch (RuntimeException e) { correcto=false; }
        try { archivos.borrar(miniatura); } catch (RuntimeException e) { correcto=false; }
        return correcto;
    }
    public record ArchivoFoto(Resource recurso, String nombre, String mime, long bytes) {}
    public ArchivoFoto contenido(UUID usuario, UUID id, boolean miniatura) {
        var foto=foto(usuario,id);
        if (!foto.datos().estado().equals("DISPONIBLE")) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"La foto no está disponible.");
        try {
            var recurso=archivos.leer(miniatura?foto.miniatura():foto.original());
            long bytes=miniatura?foto.datos().bytesMiniatura():foto.datos().bytesOriginal();
            if (recurso.contentLength()!=bytes) throw new IllegalStateException();
            return new ArchivoFoto(recurso,miniatura?"miniatura.jpg":foto.datos().nombre(),miniatura?"image/jpeg":foto.datos().mime(),bytes);
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se puede leer esta foto en este momento. Reintenta más tarde."); }
    }
    public void eliminar(UUID usuario, UUID id) {
        var pendiente=transacciones.execute(tx -> {
            albumes.bloquearCuenta(usuario);
            var foto=foto(usuario,id);
            if (foto.datos().estado().equals("PENDIENTE") && foto.datos().subidaEn().plus(Duration.ofMinutes(10)).isAfter(Instant.now()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"La subida aún está en curso. Espera antes de retirarla.");
            if (!albumes.estado(usuario,id,"ELIMINANDO",foto.datos().estado()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"La foto ha cambiado. Actualiza el álbum.");
            return foto;
        });
        if (!limpiar(pendiente.original(),pendiente.miniatura()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se ha completado la eliminación. La foto está bloqueada y puedes reintentar el borrado.");
        transacciones.executeWithoutResult(tx -> {
            albumes.bloquearCuenta(usuario);
            if (!albumes.eliminar(usuario,id,"ELIMINANDO"))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"No se ha podido confirmar la eliminación. Actualiza el álbum.");
        });
    }
}
