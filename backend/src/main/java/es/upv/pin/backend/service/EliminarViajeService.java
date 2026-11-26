package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.EliminarViajeDto;
import es.upv.pin.backend.exception.RevisionViajeException;
import es.upv.pin.backend.repository.AlbumRepository;
import es.upv.pin.backend.repository.AlbumRepository.ArchivoDelViaje;
import es.upv.pin.backend.repository.ViajeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EliminarViajeService {
    private final ViajeRepository viajes;
    private final AlbumRepository albumes;
    private final EliminarAlbumService eliminacionAlbum;
    private final TransactionTemplate transacciones;

    public EliminarViajeService(ViajeRepository viajes, AlbumRepository albumes,
            EliminarAlbumService eliminacionAlbum, TransactionTemplate transacciones) {
        this.viajes=viajes;
        this.albumes=albumes;
        this.eliminacionAlbum=eliminacionAlbum;
        this.transacciones=transacciones;
    }

    public void eliminar(UUID usuario, UUID viaje, EliminarViajeDto entrada) {
        List<ArchivoDelViaje> pendientes=transacciones.execute(tx -> {
            // El mismo bloqueo que usan las subidas: nadie puede reservar una foto entre estas comprobaciones.
            albumes.bloquearCuenta(usuario);
            var estado=viajes.bloquearParaEliminar(usuario,viaje).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND,"No se ha encontrado el viaje."));
            if (estado.eliminado()) return List.of(); // Reintento tras una respuesta perdida.
            if (estado.revision()!=entrada.revision()) throw new RevisionViajeException();
            if (estado.eliminandoRecuerdos() && !entrada.eliminarRecuerdos())
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Ya se inició el borrado de los recuerdos. Reintenta marcando también su eliminación.");

            if (!entrada.eliminarRecuerdos()) {
                albumes.retirarAlbumVacio(usuario,viaje);
                viajes.marcarEliminado(usuario,viaje);
                return List.of();
            }
            if (albumes.haySubidasEnCurso(usuario,viaje))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Hay una foto subiéndose. Espera a que termine antes de eliminar también los recuerdos.");

            var fotos=albumes.archivosDelViaje(usuario,viaje);
            eliminacionAlbum.validarArchivos(fotos);
            // Se confirma el bloqueo antes de tocar el disco: ningún archivo parcialmente borrado se sirve como disponible.
            viajes.bloquearRecuerdos(usuario,viaje);
            return fotos;
        });
        if (!entrada.eliminarRecuerdos()) return;

        eliminacionAlbum.borrarArchivos(pendientes);

        transacciones.executeWithoutResult(tx -> {
            albumes.bloquearCuenta(usuario);
            var estado=viajes.bloquearParaEliminar(usuario,viaje).orElseThrow();
            if (estado.eliminado()) return;
            albumes.retirarReferenciasDelViaje(usuario,viaje);
            viajes.marcarEliminado(usuario,viaje);
        });
    }

}
