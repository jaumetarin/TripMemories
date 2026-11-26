package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.GuardarViajeDto;
import es.upv.pin.backend.dto.EliminarViajeDto;
import es.upv.pin.backend.dto.ActualizarViajeDto;
import es.upv.pin.backend.dto.ViajeActualizadoDto;
import es.upv.pin.backend.dto.ResumenViajeDto;
import es.upv.pin.backend.dto.ViajeCreadoDto;
import es.upv.pin.backend.dto.ViajeGuardadoDto;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.ConsultaViajeService;
import es.upv.pin.backend.service.CatalogoLugaresService;
import es.upv.pin.backend.service.ControlGeneracionService;
import es.upv.pin.backend.service.ViajeService;
import es.upv.pin.backend.service.EliminarViajeService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/viajes")
public class ViajeController {

    private final ControlGeneracionService generaciones;
    private final ViajeService viajes;
    private final ConsultaViajeService consultas;
    private final CatalogoLugaresService lugares;
    private final EliminarViajeService eliminacion;

    public ViajeController(
            ControlGeneracionService generaciones,
            ViajeService viajes,
            ConsultaViajeService consultas,
            CatalogoLugaresService lugares,
            EliminarViajeService eliminacion
    ) {
        this.generaciones = generaciones;
        this.viajes = viajes;
        this.consultas = consultas;
        this.lugares = lugares;
        this.eliminacion = eliminacion;
    }

    @DeleteMapping("/{viajeId}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID viajeId,
            @Valid @RequestBody EliminarViajeDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        eliminacion.eliminar(usuario.getId(),viajeId,entrada);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    public ResponseEntity<List<ResumenViajeDto>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        var resultado = consultas.listarPropios(usuario.getId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(resultado);
    }

    @GetMapping("/{viajeId}")
    public ResponseEntity<ViajeGuardadoDto> recuperar(
            @PathVariable("viajeId") UUID viajeId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            HttpSession sesion
    ) {
        var resultado = consultas.recuperar(
                usuario.getId(),
                viajeId
        );
        lugares.restaurarSeleccionGuardada(viajeId, resultado.resumen().revision(),
                resultado.configuracion(), sesion);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(resultado);
    }

    @PostMapping("/desde-generacion/{generacionId}")
    public ResponseEntity<ViajeCreadoDto> guardar(
            @PathVariable("generacionId") UUID generacionId,
            @Valid @RequestBody GuardarViajeDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            HttpSession sesion
    ) {
        var resultado = generaciones.obtenerParaGuardar(
                generacionId,
                sesion
        );

        UUID viajeId = viajes.guardarNuevo(
                usuario.getId(),
                entrada,
                resultado.configuracion(),
                resultado.itinerario()
        );

        return ResponseEntity
                .created(URI.create("/api/viajes/" + viajeId))
                .cacheControl(CacheControl.noStore())
                .body(new ViajeCreadoDto(viajeId));
    }

    @PostMapping("/{viajeId}/desde-generacion/{generacionId}")
    public ResponseEntity<ViajeActualizadoDto> actualizarDesdeGeneracion(
            @PathVariable("viajeId") UUID viajeId,
            @PathVariable("generacionId") UUID generacionId,
            @Valid @RequestBody ActualizarViajeDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        var resultado = generaciones.obtenerParaGuardar(generacionId, sesion);
        var actualizado = viajes.actualizar(usuario.getId(), viajeId, entrada,
                resultado.configuracion(), resultado.itinerario());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actualizado);
    }

    @PutMapping("/{viajeId}")
    public ResponseEntity<ViajeActualizadoDto> actualizarTitulo(
            @PathVariable("viajeId") UUID viajeId,
            @Valid @RequestBody ActualizarViajeDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var guardado = consultas.recuperar(usuario.getId(), viajeId);
        var actualizado = viajes.actualizar(usuario.getId(), viajeId, entrada,
                guardado.configuracion(), guardado.itinerario());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actualizado);
    }
}
