package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.ItinerarioGeneradoDto;
import es.upv.pin.backend.service.ControlGeneracionService;
import es.upv.pin.backend.dto.ConsumoGeneracionDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itinerarios")
public class GeneracionItinerarioController {
    private final ControlGeneracionService servicio;

    public GeneracionItinerarioController(ControlGeneracionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/generar")
    public ResponseEntity<ItinerarioGeneradoDto> generar(@RequestBody ConfiguracionItinerarioDto configuracion,
            HttpSession sesion) {
        var resultado = servicio.generar(configuracion, sesion);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Itinerario-Reutilizado", Boolean.toString(resultado.reutilizado()))
                .header("X-Itinerario-Vigente-Hasta", resultado.vigenteHasta().toString())
                .body(resultado.itinerario());
    }

    @GetMapping("/consumo")
    public ResponseEntity<ConsumoGeneracionDto> consumo(HttpSession sesion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(sesion));
    }
}
