package es.upv.pin.backend.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.service.DesplazamientoService;

@RestController
@RequestMapping("/api/desplazamientos")
public class DesplazamientoController {

    private final DesplazamientoService servicio;

    public DesplazamientoController(DesplazamientoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/calcular")
    public ResponseEntity<List<DesplazamientoDto>> calcular(
            @RequestBody List<PuntoRutaDto> puntos) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.calcular(puntos));
    }
}