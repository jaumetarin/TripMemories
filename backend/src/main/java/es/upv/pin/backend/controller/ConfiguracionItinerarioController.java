package es.upv.pin.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.service.ConfiguracionItinerarioService;
import es.upv.pin.backend.service.CatalogoLugaresService;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/itinerarios")
public class ConfiguracionItinerarioController {
    private final ConfiguracionItinerarioService servicio;
    private final CatalogoLugaresService lugares;

    public ConfiguracionItinerarioController(ConfiguracionItinerarioService servicio,
            CatalogoLugaresService lugares) {
        this.servicio = servicio;
        this.lugares = lugares;
    }

    @PostMapping("/confirmar-configuracion")
    public ResponseEntity<ConfiguracionItinerarioDto> confirmar(@RequestBody ConfiguracionItinerarioDto configuracion,
            HttpSession sesion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.validar(
                configuracion == null || configuracion.selecciones() == null || configuracion.selecciones().isEmpty()
                        ? configuracion : lugares.resolver(configuracion, sesion)));
    }
}
