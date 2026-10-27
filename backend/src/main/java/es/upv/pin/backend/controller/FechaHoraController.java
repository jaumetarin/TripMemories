package es.upv.pin.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.upv.pin.backend.dto.FechaHoraDto;
import es.upv.pin.backend.service.FechaHoraService;

@RestController
@RequestMapping("/api/itinerarios")
public class FechaHoraController {

    private final FechaHoraService fechaHoraService;

    public FechaHoraController(FechaHoraService fechaHoraService) {
        this.fechaHoraService = fechaHoraService;
    }

    @PostMapping("/validar-fecha-hora")
    public FechaHoraDto validar(
            @RequestBody FechaHoraDto disponibilidad) {

        return fechaHoraService.validar(disponibilidad);
    }
}
