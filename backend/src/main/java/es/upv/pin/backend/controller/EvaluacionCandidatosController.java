package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.EvaluacionCandidatosDto;
import es.upv.pin.backend.service.EvaluacionCandidatosService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidatos")
public class EvaluacionCandidatosController {
    private final EvaluacionCandidatosService servicio;

    public EvaluacionCandidatosController(EvaluacionCandidatosService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/evaluar")
    public ResponseEntity<EvaluacionCandidatosDto> evaluar(
            @RequestBody ConfiguracionItinerarioDto configuracion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.evaluar(configuracion));
    }
}
