package es.upv.pin.backend.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.service.DestinoService;

@RestController
@RequestMapping("/api/destinos")
public class DestinoController {
    private final DestinoService destinoService;

    public DestinoController(DestinoService destinoService) {
        this.destinoService = destinoService;
    }

    @GetMapping
    public ResponseEntity<List<DestinoDto>> buscar(@RequestParam(defaultValue = "") String texto) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(destinoService.buscar(texto));
    }
}
