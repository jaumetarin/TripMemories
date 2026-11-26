package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.AlbumService;
import es.upv.pin.backend.service.EliminarAlbumService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AlbumController {
    private final AlbumService servicio;
    private final EliminarAlbumService eliminacion;
    public AlbumController(AlbumService servicio, EliminarAlbumService eliminacion) {
        this.servicio=servicio;
        this.eliminacion=eliminacion;
    }
    @DeleteMapping("/albumes/{id}")
    public ResponseEntity<Void> eliminarAlbum(@PathVariable UUID id,@AuthenticationPrincipal UsuarioAutenticado usuario) {
        eliminacion.eliminar(usuario.getId(),id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/albumes")
    public ResponseEntity<List<AlbumDto>> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(usuario.getId()));
    }
    @GetMapping("/albumes/{id}")
    public ResponseEntity<ContenidoAlbumDto> abrir(@PathVariable UUID id,@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.abrir(usuario.getId(),id));
    }
    @PostMapping(value="/albumes/{id}/fotos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoDto> subir(@PathVariable UUID id,@RequestParam("foto") List<MultipartFile> fotos,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (fotos.size()!=1) throw new IllegalArgumentException("Selecciona una sola foto por subida.");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(servicio.subir(usuario.getId(),id,fotos.getFirst()));
    }
    @GetMapping("/fotos/{id}/miniatura")
    public ResponseEntity<Resource> miniatura(@PathVariable UUID id,@AuthenticationPrincipal UsuarioAutenticado usuario) { return archivo(usuario,id,true,false); }
    @GetMapping("/fotos/{id}/contenido")
    public ResponseEntity<Resource> original(@PathVariable UUID id,@AuthenticationPrincipal UsuarioAutenticado usuario) { return archivo(usuario,id,false,false); }
    @GetMapping("/fotos/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable UUID id,@AuthenticationPrincipal UsuarioAutenticado usuario) { return archivo(usuario,id,false,true); }
    private ResponseEntity<Resource> archivo(UsuarioAutenticado usuario,UUID id,boolean miniatura,boolean descargar) {
        var foto=servicio.contenido(usuario.getId(),id,miniatura);
        var disposicion=descargar?ContentDisposition.attachment():ContentDisposition.inline();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).contentType(MediaType.parseMediaType(foto.mime()))
                .contentLength(foto.bytes()).header(HttpHeaders.CONTENT_DISPOSITION,disposicion.filename(foto.nombre(),StandardCharsets.UTF_8).build().toString())
                .body(foto.recurso());
    }
    @DeleteMapping("/fotos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id,@AuthenticationPrincipal UsuarioAutenticado usuario) {
        servicio.eliminar(usuario.getId(),id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
