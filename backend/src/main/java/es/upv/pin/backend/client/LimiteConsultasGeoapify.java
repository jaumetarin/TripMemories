package es.upv.pin.backend.client;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Un único límite para todos los usuarios del backend, no uno por formulario. */
@Component
public class LimiteConsultasGeoapify {
    private final Semaphore simultaneas;
    private final long intervaloNanos;
    private long siguienteInicio;
    private boolean programado;

    public LimiteConsultasGeoapify() { this(3, 350); }

    LimiteConsultasGeoapify(int maximo, long intervaloMilisegundos) {
        simultaneas = new Semaphore(maximo, true);
        intervaloNanos = TimeUnit.MILLISECONDS.toNanos(intervaloMilisegundos);
    }

    public <T> T ejecutar(Supplier<T> consulta) {
        boolean permiso = false;
        try {
            simultaneas.acquire();
            permiso = true;
            esperarTurno();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            return consulta.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se ha cancelado la búsqueda de lugares.");
        } finally { if (permiso) simultaneas.release(); }
    }

    private synchronized void esperarTurno() throws InterruptedException {
        long espera = programado ? siguienteInicio - System.nanoTime() : 0;
        if (espera > 0) TimeUnit.NANOSECONDS.sleep(espera);
        siguienteInicio = System.nanoTime() + intervaloNanos;
        programado = true;
    }
}
