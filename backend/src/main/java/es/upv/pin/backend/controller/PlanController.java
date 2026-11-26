package es.upv.pin.backend.controller;

import es.upv.pin.backend.model.Plan;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.service.PlanService;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planes")
public class PlanController {
    private final PlanService servicio;
    public PlanController(PlanService servicio) { this.servicio=servicio; }
    public record Oferta(Plan plan, int generacionesMes, int generacionesHora, long cuotaBytes) {
        static Oferta de(Plan p) { return new Oferta(p, p.generacionesMes(), p.generacionesHora(), p.cuotaBytes()); }
    }
    public record Actual(Plan plan, int generacionesMes, int generacionesHora, long cuotaBytes, boolean demoHabilitada) {}
    public record Cambio(Plan plan) {}
    private Actual dto(Plan p) { return new Actual(p, p.generacionesMes(), p.generacionesHora(), p.cuotaBytes(), servicio.demoHabilitada()); }
    @GetMapping("/oferta")
    public List<Oferta> oferta() { return Arrays.stream(Plan.values()).map(Oferta::de).toList(); }
    @GetMapping("/actual")
    public ResponseEntity<Actual> actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dto(servicio.actual(usuario.getId())));
    }
    @PostMapping("/demo/cambiar")
    public ResponseEntity<Actual> cambiar(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestBody Cambio cambio) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dto(servicio.cambiarDemo(usuario.getId(), cambio.plan())));
    }
}
