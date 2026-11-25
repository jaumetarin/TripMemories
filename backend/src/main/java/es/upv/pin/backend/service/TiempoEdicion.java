package es.upv.pin.backend.service;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Un único plazo monotónico por operación, compartido por todos sus candidatos y días. */
public final class TiempoEdicion {
    private static final ThreadLocal<TiempoEdicion> ACTUAL = new ThreadLocal<>();
    private static final long LIMITE = Duration.ofSeconds(120).toNanos();
    // Una consulta ORS puede consumir 5 s de conexión y 20 s de lectura.
    private static final long RESERVA_CONSULTA = Duration.ofSeconds(25).toNanos();
    private final LongSupplier reloj;
    private final long inicio;

    public TiempoEdicion() { this(System::nanoTime); }
    TiempoEdicion(LongSupplier reloj) { this.reloj = reloj; this.inicio = reloj.getAsLong(); }
    public static TiempoEdicion actual() {
        var actual = ACTUAL.get();
        return actual == null ? new TiempoEdicion() : actual;
    }
    public boolean permiteConsulta() { return reloj.getAsLong() - inicio < LIMITE - RESERVA_CONSULTA; }
    public void comprobar() {
        if (reloj.getAsLong() - inicio >= LIMITE) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Se agotó el tiempo para comprobar los cambios. El viaje guardado se conserva; recupera el borrador y vuelve a intentarlo.");
        }
    }
    public static <T> T ejecutar(Supplier<T> operacion) { return ejecutar(actual(), operacion); }
    static <T> T ejecutar(TiempoEdicion tiempo, Supplier<T> operacion) {
        var anterior = ACTUAL.get();
        ACTUAL.set(anterior == null ? tiempo : anterior);
        try { ACTUAL.get().comprobar(); return operacion.get(); }
        finally { if (anterior == null) ACTUAL.remove(); else ACTUAL.set(anterior); }
    }
}
