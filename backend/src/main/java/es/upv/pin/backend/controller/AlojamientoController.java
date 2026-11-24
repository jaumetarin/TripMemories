package es.upv.pin.backend.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.AlojamientoDto;
import es.upv.pin.backend.service.AlojamientoService;

@RestController
@RequestMapping("/api/alojamientos")
public class AlojamientoController {

    private final AlojamientoService servicio;

    public AlojamientoController(AlojamientoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<AlojamientoDto>> buscar(
            @RequestParam(name = "texto", defaultValue = "") String texto,
            @RequestParam(name = "destinoId", defaultValue = "") String destinoId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.buscar(texto, destinoId));
    }
}
