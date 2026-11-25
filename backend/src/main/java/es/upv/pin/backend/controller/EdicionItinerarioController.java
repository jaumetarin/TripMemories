package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.EdicionItinerarioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/viajes/{viajeId}/edicion")
public class EdicionItinerarioController {
    private final EdicionItinerarioService servicio;
    public EdicionItinerarioController(EdicionItinerarioService servicio) { this.servicio = servicio; }

    @PostMapping("/iniciar")
    public ResponseEntity<EstadoEdicionDto> iniciar(@PathVariable UUID viajeId,
            @Valid @RequestBody RevisionEdicionDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        return respuesta(servicio.iniciar(usuario.getId(), viajeId, entrada.revision(), sesion));
    }

    @PostMapping("/{propuestaId}/visitas/{actividadId}/eliminar")
    public ResponseEntity<EstadoEdicionDto> eliminar(@PathVariable UUID viajeId, @PathVariable UUID propuestaId,
            @PathVariable UUID actividadId, @Valid @RequestBody EliminarActividadDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        return respuesta(servicio.eliminar(usuario.getId(), viajeId, propuestaId, actividadId, entrada.revision(), sesion));
    }

    @PostMapping("/{propuestaId}/reordenar")
    public ResponseEntity<EstadoEdicionDto> reordenar(@PathVariable UUID viajeId, @PathVariable UUID propuestaId,
            @Valid @RequestBody ReordenarActividadesDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        return respuesta(servicio.reordenar(usuario.getId(), viajeId, propuestaId, entrada, sesion));
    }

    @PostMapping("/{propuestaId}/visitas/{actividadId}/alternativas")
    public ResponseEntity<AlternativasEdicionDto> alternativas(@PathVariable UUID viajeId, @PathVariable UUID propuestaId,
            @PathVariable UUID actividadId, @Valid @RequestBody RevisionEdicionDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        return respuesta(servicio.alternativas(usuario.getId(), viajeId, propuestaId, actividadId, entrada.revision(), sesion));
    }

    @PostMapping("/{propuestaId}/visitas/{actividadId}/sustituir")
    public ResponseEntity<EstadoEdicionDto> sustituir(@PathVariable UUID viajeId, @PathVariable UUID propuestaId,
            @PathVariable UUID actividadId, @Valid @RequestBody SustituirActividadDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        return respuesta(servicio.sustituir(usuario.getId(), viajeId, propuestaId, actividadId, entrada, sesion));
    }

    @PostMapping("/{propuestaId}/recalcular")
    public ResponseEntity<EstadoEdicionDto> recalcular(@PathVariable UUID viajeId, @PathVariable UUID propuestaId,
            @Valid @RequestBody RevisionEdicionDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        return respuesta(servicio.recalcular(usuario.getId(), viajeId, propuestaId, entrada.revision(), sesion));
    }

    @PostMapping("/{propuestaId}/guardar")
    public ResponseEntity<ViajeActualizadoDto> guardar(@PathVariable UUID viajeId, @PathVariable UUID propuestaId,
            @Valid @RequestBody ConfirmarEdicionDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        return respuesta(servicio.guardar(usuario.getId(), viajeId, propuestaId, entrada, sesion));
    }

    private static <T> ResponseEntity<T> respuesta(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
