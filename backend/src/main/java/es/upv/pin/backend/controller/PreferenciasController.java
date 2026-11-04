package es.upv.pin.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.CategoriaDto;
import es.upv.pin.backend.dto.OpcionesGastoDistanciaDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.service.PreferenciasService;

@RestController
@RequestMapping("/api/itinerarios")
public class PreferenciasController {

    private final PreferenciasService preferenciasService;

    public PreferenciasController(PreferenciasService preferenciasService) {
        this.preferenciasService = preferenciasService;
    }

    @GetMapping("/catalogo-preferencias")
    public List<CategoriaDto> obtenerCatalogo() {
        return preferenciasService.obtenerCatalogo();
    }

    @PostMapping("/validar-preferencias")
    public PreferenciasDto validar(@RequestBody PreferenciasDto preferencias) {
        return preferenciasService.validar(preferencias);
    }

    @GetMapping("/opciones-gasto-distancia")
    public OpcionesGastoDistanciaDto obtenerOpcionesGastoDistancia() {
        return preferenciasService.obtenerOpcionesGastoDistancia();
    }
}
