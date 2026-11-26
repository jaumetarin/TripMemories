package es.upv.pin.backend.service;

import es.upv.pin.backend.repository.AlbumRepository;
import es.upv.pin.backend.repository.AlbumRepository.ArchivoDelViaje;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EliminarAlbumService {
    private final AlbumRepository albumes;
    private final AlmacenamientoFotos archivos;
    private final TransactionTemplate transacciones;

    public EliminarAlbumService(AlbumRepository albumes, AlmacenamientoFotos archivos, TransactionTemplate transacciones) {
        this.albumes=albumes;
        this.archivos=archivos;
        this.transacciones=transacciones;
    }

    public void eliminar(UUID usuario, UUID albumId) {
        var pendientes=transacciones.execute(tx -> {
            albumes.bloquearCuenta(usuario);
            var album=albumes.propio(usuario,albumId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND,"No se ha encontrado el álbum."));
            if (albumes.haySubidasEnCurso(usuario,album.viajeId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Hay una foto subiéndose. Espera a que termine antes de eliminar el álbum.");
            // Solo existe un álbum por viaje; las claves siempre se resuelven desde el propietario autenticado.
            var fotos=albumes.archivosDelViaje(usuario,album.viajeId());
            validarArchivos(fotos);
            albumes.marcarAlbumEliminando(usuario,albumId);
            return fotos;
        });

        borrarArchivos(pendientes);

        transacciones.executeWithoutResult(tx -> {
            albumes.bloquearCuenta(usuario);
            albumes.retirarReferenciasDelAlbum(usuario,albumId);
        });
    }

    // También se reutiliza cuando se elimina el viaje junto con sus recuerdos.
    public void validarArchivos(List<ArchivoDelViaje> fotos) {
        for (var foto:fotos) {
            if (esEjemplo(foto)) continue;
            if (!(foto.id()+"/original").equals(foto.original()) || !(foto.id()+"/miniatura").equals(foto.miniatura()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"El álbum contiene archivos antiguos que necesitan revisión antes de borrarse.");
        }
    }

    public void borrarArchivos(List<ArchivoDelViaje> fotos) {
        boolean completo=true;
        for (var foto:fotos) {
            if (esEjemplo(foto)) continue;
            try { archivos.borrar(foto.original()); } catch (RuntimeException e) { completo=false; }
            try { archivos.borrar(foto.miniatura()); } catch (RuntimeException e) { completo=false; }
        }
        if (!completo) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se ha completado la eliminación de los recuerdos. Los archivos están bloqueados para evitar mostrar fotos incompletas. Reintenta la eliminación.");
    }

    private boolean esEjemplo(ArchivoDelViaje foto) {
        return foto.original()!=null && foto.original().startsWith("demo://") && foto.miniatura()==null;
    }
}
