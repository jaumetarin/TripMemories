package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.ItinerarioGeneradoDto;
import es.upv.pin.backend.service.GeneracionItinerarioService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itinerarios")
public class GeneracionItinerarioController {
    private final GeneracionItinerarioService servicio;

    public GeneracionItinerarioController(GeneracionItinerarioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/generar")
    public ResponseEntity<ItinerarioGeneradoDto> generar(@RequestBody ConfiguracionItinerarioDto configuracion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.generar(configuracion));
    }
}
