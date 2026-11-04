package es.upv.pin.backend.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.CandidatoDto;
import es.upv.pin.backend.service.CandidatoService;

@RestController
@RequestMapping("/api/candidatos")
public class CandidatoController {

    private final CandidatoService candidatoService;

    public CandidatoController(CandidatoService candidatoService) {
        this.candidatoService = candidatoService;
    }

    @GetMapping("/turisticos")
    public ResponseEntity<List<CandidatoDto>> buscarTuristicos(
            @RequestParam("destinoId") String destinoId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(candidatoService.buscarTuristicos(destinoId));
    }

    @GetMapping("/gastronomicos")
    public ResponseEntity<List<CandidatoDto>> buscarGastronomicos(
            @RequestParam("destinoId") String destinoId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(candidatoService.buscarGastronomicos(destinoId));
    }
}