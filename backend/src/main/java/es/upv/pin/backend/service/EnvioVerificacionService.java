package es.upv.pin.backend.service;

import es.upv.pin.backend.client.BrevoClient;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EnvioVerificacionService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EnvioVerificacionService.class);
    private final BrevoClient brevo;
    private final Executor ejecutor;
    public EnvioVerificacionService(BrevoClient brevo, @Qualifier("correosExecutor") Executor ejecutor) {
        this.brevo = brevo;
        this.ejecutor = ejecutor;
    }
    @TransactionalEventListener
    public void enviarTrasConfirmar(CorreoVerificacion correo) {
        // El tiempo de Brevo no permite averiguar si la dirección ya estaba registrada.
        try {
            ejecutor.execute(() -> {
                try { brevo.enviarVerificacion(correo.correo(), correo.nombre(), correo.enlace()); }
                catch (RuntimeException error) { LOG.warn("No se pudo entregar un correo de verificación; puede solicitarse un nuevo enlace."); }
            });
        } catch (RuntimeException error) {
            LOG.warn("No se pudo encolar un correo de verificación; puede solicitarse un nuevo enlace.");
        }
    }
}
