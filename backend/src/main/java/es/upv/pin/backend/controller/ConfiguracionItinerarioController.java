package es.upv.pin.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.service.ConfiguracionItinerarioService;

@RestController
@RequestMapping("/api/itinerarios")
public class ConfiguracionItinerarioController {
    private final ConfiguracionItinerarioService servicio;

    public ConfiguracionItinerarioController(ConfiguracionItinerarioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/confirmar-configuracion")
    public ResponseEntity<ConfiguracionItinerarioDto> confirmar(@RequestBody ConfiguracionItinerarioDto configuracion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.validar(configuracion));
    }
}
