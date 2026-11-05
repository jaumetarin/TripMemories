package es.upv.pin.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.service.PreparacionCandidatosService;

@RestController
@RequestMapping("/api/candidatos")
public class PreparacionCandidatosController {
    private final PreparacionCandidatosService servicio;

    public PreparacionCandidatosController(PreparacionCandidatosService servicio) { this.servicio = servicio; }

    @PostMapping("/preparar")
    public ResponseEntity<List<LugarDto>> preparar(@RequestBody ConfiguracionItinerarioDto configuracion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.preparar(configuracion));
    }
}
