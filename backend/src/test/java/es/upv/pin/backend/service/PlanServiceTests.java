package es.upv.pin.backend.service;

import es.upv.pin.backend.model.Plan;
import es.upv.pin.backend.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanServiceTests {
    @Test void demoDeshabilitadaRechazaCambiosSinEscribir() {
        var usuarios=mock(UsuarioRepository.class);
        var servicio=new PlanService(usuarios,false);
        var error=assertThrows(ResponseStatusException.class, () -> servicio.cambiarDemo(UUID.randomUUID(),Plan.PREMIUM));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
        verifyNoInteractions(usuarios);
    }
    @Test void limitesDeAmbosPlanesSonLosPublicados() {
        assertEquals(5,Plan.FREE.generacionesMes()); assertEquals(3,Plan.FREE.generacionesHora());
        assertEquals(524288000L,Plan.FREE.cuotaBytes());
        assertEquals(20,Plan.PREMIUM.generacionesMes()); assertEquals(5,Plan.PREMIUM.generacionesHora());
        assertEquals(2147483648L,Plan.PREMIUM.cuotaBytes());
    }
}
