package es.upv.pin.backend.service;

import es.upv.pin.backend.model.Plan;
import es.upv.pin.backend.repository.UsuarioRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanService {
    private final UsuarioRepository usuarios;
    private final boolean demoHabilitada;
    public PlanService(UsuarioRepository usuarios, @Value("${planes.demo-habilitada:true}") boolean demoHabilitada) {
        this.usuarios=usuarios; this.demoHabilitada=demoHabilitada;
    }
    public Plan actual(UUID usuario) {
        return usuarios.buscarPlan(usuario).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta ya no existe."));
    }
    public boolean demoHabilitada() { return demoHabilitada; }
    @Transactional
    public Plan cambiarDemo(UUID usuario, Plan plan) {
        if (!demoHabilitada) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El cambio de plan de demostracion esta deshabilitado.");
        if (plan == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un plan valido.");
        if (!usuarios.cambiarPlan(usuario, plan)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta ya no existe.");
        return plan;
    }
}
