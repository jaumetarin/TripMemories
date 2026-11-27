package es.upv.pin.backend.service;

import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.TrazadosDiaDto.TramoDto;
import es.upv.pin.backend.service.TramosPlan.Tramo;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

/** Los fallos de geometría se aíslan por tramo y nunca impiden consultar la lista. */
@Service
public class TrazadosService {
    private static final String ATRIBUTO = TrazadosService.class.getName();
    private final OpenRouteServiceClient cliente;
    private final Semaphore llamadas = new Semaphore(3);
    public TrazadosService(OpenRouteServiceClient cliente) { this.cliente = cliente; }

    public TrazadosDiaDto obtener(ConfiguracionItinerarioDto config, ItinerarioGeneradoDto plan,
            LocalDate fecha, HttpSession sesion) {
        var tramos = TramosPlan.delDia(config, plan, fecha);
        var cache = cache(sesion);
        List<TramoDto> resultado = new ArrayList<>();
        Map<String, Future<GeometriaTramoDto>> pendientes = new HashMap<>();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        var ejecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (var tramo : tramos) {
                if (utilizable(tramo) && !mismoPunto(tramo) && !plan.trazados().containsKey(tramo.clave())) {
                    pendientes.computeIfAbsent(tramo.clave(), clave -> ejecutor.submit(new org.springframework.security.concurrent.DelegatingSecurityContextCallable<>(() -> recuperar(tramo, cache))));
                }
            }
            for (var tramo : tramos) {
                var geometria = plan.trazados().get(tramo.clave());
                String estado = "DISPONIBLE";
                if (!utilizable(tramo)) estado = "COORDENADAS_AUSENTES";
                else if (mismoPunto(tramo)) estado = "MISMO_PUNTO";
                else if (geometria == null) {
                    try { geometria = pendientes.get(tramo.clave()).get(Math.max(1, limite - System.nanoTime()), TimeUnit.NANOSECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); estado = "NO_DISPONIBLE"; }
                    catch (ExecutionException | TimeoutException e) { estado = "NO_DISPONIBLE"; }
                }
                resultado.add(new TramoDto(tramo.clave(), tramo.origenId(), tramo.destinoId(), estado, geometria));
            }
        } finally {
            pendientes.values().forEach(f -> f.cancel(true));
            ejecutor.shutdownNow();
        }
        return new TrazadosDiaDto(fecha, List.copyOf(resultado));
    }

    public static Map<String, GeometriaTramoDto> disponibles(TrazadosDiaDto datos) {
        Map<String, GeometriaTramoDto> resultado = new LinkedHashMap<>();
        datos.tramos().stream().filter(t -> "DISPONIBLE".equals(t.estado()) && t.geometria() != null)
                .forEach(t -> resultado.put(t.clave(), t.geometria()));
        return Map.copyOf(resultado);
    }

    private GeometriaTramoDto recuperar(Tramo tramo, Cache cache) throws InterruptedException {
        CompletableFuture<GeometriaTramoDto> enCurso;
        boolean propia;
        synchronized (cache) {
            var guardada = cache.datos.get(tramo.clave());
            if (guardada != null) return guardada;
            enCurso = cache.enCurso.get(tramo.clave());
            propia = enCurso == null;
            if (propia) { enCurso = new CompletableFuture<>(); cache.enCurso.put(tramo.clave(), enCurso); }
        }
        if (!propia) return esperar(enCurso);
        try {
            llamadas.acquire();
            GeometriaTramoDto geometria;
            try { geometria = Objects.requireNonNull(cliente.calcularGeometria(tramo.origen(), tramo.destino())); }
            finally { llamadas.release(); }
            synchronized (cache) {
                cache.datos.put(tramo.clave(), geometria);
                while (cache.datos.size() > 256) cache.datos.pollFirstEntry();
            }
            enCurso.complete(geometria);
            return geometria;
        } catch (RuntimeException | InterruptedException e) {
            enCurso.completeExceptionally(e);
            throw e;
        } finally { synchronized (cache) { cache.enCurso.remove(tramo.clave()); } }
    }

    private static GeometriaTramoDto esperar(CompletableFuture<GeometriaTramoDto> futuro) throws InterruptedException {
        try { return futuro.get(15, TimeUnit.SECONDS); }
        catch (ExecutionException | TimeoutException e) { throw new IllegalStateException("Trazado no disponible."); }
    }
    private static boolean utilizable(Tramo t) { return TramosPlan.localizado(t.origen()) && TramosPlan.localizado(t.destino()); }
    private static boolean mismoPunto(Tramo t) {
        return t.origen().latitud().doubleValue() == t.destino().latitud().doubleValue()
                && t.origen().longitud().doubleValue() == t.destino().longitud().doubleValue();
    }
    private synchronized Cache cache(HttpSession sesion) {
        var cache = (Cache) sesion.getAttribute(ATRIBUTO);
        if (cache == null) { cache = new Cache(); sesion.setAttribute(ATRIBUTO, cache); }
        return cache;
    }
    private static class Cache {
        final LinkedHashMap<String, GeometriaTramoDto> datos = new LinkedHashMap<>();
        final Map<String, CompletableFuture<GeometriaTramoDto>> enCurso = new HashMap<>();
    }
}
