package es.upv.pin.backend.controller;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.service.CatalogoLugaresService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lugares")
public class SeleccionLugaresController {
    private final CatalogoLugaresService servicio;
    public SeleccionLugaresController(CatalogoLugaresService servicio) { this.servicio = servicio; }

    @PostMapping("/buscar")
    public ResponseEntity<CatalogoLugaresDto> buscar(@RequestBody BusquedaLugaresDto consulta, HttpSession sesion) {
        return respuesta(servicio.buscar(consulta, sesion));
    }
    @PostMapping("/revisar")
    public ResponseEntity<CatalogoLugaresDto> revisar(@RequestBody ConfiguracionItinerarioDto configuracion, HttpSession sesion) {
        return respuesta(servicio.revisar(configuracion, sesion));
    }
    @PostMapping("/seleccion")
    public ResponseEntity<CatalogoLugaresDto> cambiar(@RequestBody CambioSeleccionLugarDto cambio, HttpSession sesion) {
        return respuesta(servicio.cambiar(cambio, sesion));
    }
    private ResponseEntity<CatalogoLugaresDto> respuesta(CatalogoLugaresDto datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(datos);
    }
}
