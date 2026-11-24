package es.upv.pin.backend.client;

import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Guarda solo respuestas públicas del proveedor; nunca preferencias ni selecciones personales. */
public class CacheGeoapify {
    public record Consulta(String tipo, String destino, String categorias, int limite, int offset, String nombre, String sesgo) {
        public Consulta(String tipo, String destino, String categorias, int limite, int offset, String nombre) {
            this(tipo, destino, categorias, limite, offset, nombre, "");
        }
    }
    private record Entrada(RespuestaLugaresGeoapifyDto respuesta, Instant caducidad) {}
    private final Map<Consulta, Entrada> entradas = new LinkedHashMap<>(16, .75f, true);
    private final Object[] cerrojos = new Object[64];
    private final Clock reloj;
    private final Duration vigencia;
    private final int maximo;

    public CacheGeoapify() { this(Clock.systemUTC(), Duration.ofMinutes(15), 128); }

    CacheGeoapify(Clock reloj, Duration vigencia, int maximo) {
        this.reloj = reloj;
        this.vigencia = vigencia;
        this.maximo = maximo;
        for (int i = 0; i < cerrojos.length; i++) cerrojos[i] = new Object();
    }

    public RespuestaLugaresGeoapifyDto obtener(Consulta clave, Supplier<RespuestaLugaresGeoapifyDto> consulta) {
        var guardada = vigente(clave);
        if (guardada != null) return guardada;
        // La misma consulta simultánea espera su resultado, en vez de consumir otra petición.
        synchronized (cerrojos[Math.floorMod(clave.hashCode(), cerrojos.length)]) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Se ha cancelado la búsqueda de lugares.");
            guardada = vigente(clave);
            if (guardada != null) return guardada;
            var respuesta = consulta.get();
            if (respuesta == null || respuesta.features() == null || respuesta.features().stream()
                    .anyMatch(f -> f == null || f.properties() == null))
                throw new IllegalStateException("El servicio de lugares ha devuelto una respuesta no válida.");
            var copia = new RespuestaLugaresGeoapifyDto(java.util.List.copyOf(respuesta.features()));
            guardar(clave, copia);
            return copia;
        }
    }

    private synchronized RespuestaLugaresGeoapifyDto vigente(Consulta clave) {
        var entrada = entradas.get(clave);
        if (entrada == null) return null;
        if (!entrada.caducidad().isAfter(reloj.instant())) { entradas.remove(clave); return null; }
        return entrada.respuesta();
    }

    private synchronized void guardar(Consulta clave, RespuestaLugaresGeoapifyDto respuesta) {
        Instant ahora = reloj.instant();
        entradas.values().removeIf(e -> !e.caducidad().isAfter(ahora));
        entradas.put(clave, new Entrada(respuesta, ahora.plus(vigencia)));
        while (entradas.size() > maximo) entradas.remove(entradas.keySet().iterator().next());
    }
}
