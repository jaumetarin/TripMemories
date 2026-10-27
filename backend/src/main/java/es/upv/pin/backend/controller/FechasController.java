package es.upv.pin.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.FechasDto;
import es.upv.pin.backend.service.FechasService;

@RestController
@RequestMapping("/api/itinerarios")
public class FechasController {

    private final FechasService fechasService;

    public FechasController(FechasService fechasService) {
        this.fechasService = fechasService;
    }

    @PostMapping("/validar-fechas")
    public FechasDto validar(
            @RequestBody FechasDto fechas) {

        return fechasService.validar(fechas);
    }
}