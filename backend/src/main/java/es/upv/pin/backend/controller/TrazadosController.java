package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.exception.RevisionViajeException;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TrazadosController {
    private final TrazadosService trazados;
    private final ControlGeneracionService generaciones;
    private final ConsultaViajeService consultas;
    private final EdicionItinerarioService edicion;
    private final ViajeService escritura;
    public TrazadosController(TrazadosService trazados, ControlGeneracionService generaciones,
            ConsultaViajeService consultas, EdicionItinerarioService edicion, ViajeService escritura) {
        this.trazados = trazados; this.generaciones = generaciones; this.consultas = consultas;
        this.edicion = edicion; this.escritura = escritura;
    }

    @PostMapping("/itinerarios/{generacionId}/trazados")
    public ResponseEntity<TrazadosDiaDto> generado(@PathVariable UUID generacionId,
            @Valid @RequestBody SolicitudTrazadosDto entrada, HttpSession sesion) {
        var plan = generaciones.obtenerParaGuardar(generacionId, sesion);
        var resultado = trazados.obtener(plan.configuracion(), plan.itinerario(), entrada.dia(), sesion);
        generaciones.conservarTrazados(generacionId, TrazadosService.disponibles(resultado), sesion);
        return respuesta(resultado);
    }

    @PostMapping("/viajes/{viajeId}/trazados")
    public ResponseEntity<TrazadosDiaDto> guardado(@PathVariable UUID viajeId,
            @Valid @RequestBody SolicitudTrazadosDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        var plan = consultas.recuperar(usuario.getId(), viajeId);
        long revision = revision(entrada);
        if (plan.resumen().revision() != revision) throw new RevisionViajeException();
        var resultado = trazados.obtener(plan.configuracion(), plan.itinerario(), entrada.dia(), sesion);
        escritura.conservarTrazados(usuario.getId(), viajeId, revision, TrazadosService.disponibles(resultado));
        return respuesta(resultado);
    }

    @PostMapping("/viajes/{viajeId}/edicion/{propuestaId}/trazados")
    public ResponseEntity<TrazadosDiaDto> borrador(@PathVariable UUID viajeId, @PathVariable UUID propuestaId,
            @Valid @RequestBody SolicitudTrazadosDto entrada,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpSession sesion) {
        long revision = revision(entrada);
        var plan = edicion.paraTrazados(usuario.getId(), viajeId, propuestaId, revision, sesion);
        var resultado = trazados.obtener(plan.configuracion(), plan.itinerario(), entrada.dia(), sesion);
        edicion.conservarTrazados(usuario.getId(), viajeId, propuestaId, revision, TrazadosService.disponibles(resultado), sesion);
        return respuesta(resultado);
    }

    private static long revision(SolicitudTrazadosDto entrada) {
        if (entrada.revision() == null) throw new IllegalArgumentException("Indica la revisión del viaje.");
        return entrada.revision();
    }
    private static ResponseEntity<TrazadosDiaDto> respuesta(TrazadosDiaDto datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(datos);
    }
}
