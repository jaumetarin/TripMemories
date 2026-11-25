package es.upv.pin.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Límites por operación e IP real de la conexión, no por la existencia de una cuenta. */
@Service
public class LimiteAuthService {
    private final Map<String, Ventana> ventanas = new HashMap<>();
    private final Clock reloj;
    public LimiteAuthService() { this(Clock.systemUTC()); }
    LimiteAuthService(Clock reloj) { this.reloj = reloj; }

    public synchronized void comprobar(String operacion, String ip, int limite) {
        Instant ahora = reloj.instant();
        ventanas.values().removeIf(v -> !ahora.isBefore(v.hasta));
        String clave = operacion + "|" + ip;
        Ventana ventana = ventanas.get(clave);
        if (ventana == null) {
            if (ventanas.size() >= 5000) throw limite();
            ventana = new Ventana(ahora.plusSeconds(900));
            ventanas.put(clave, ventana);
        }
        if (ventana.intentos >= limite) throw limite();
        ventana.intentos++;
    }
    private static ResponseStatusException limite() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera unos minutos antes de volver a intentarlo.");
    }
    private static class Ventana {
        final Instant hasta;
        int intentos;
        Ventana(Instant hasta) { this.hasta = hasta; }
    }
}
