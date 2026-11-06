package es.upv.pin.backend.service;

import es.upv.pin.backend.client.AiProvider;
import es.upv.pin.backend.client.ResearchProvider;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.CostConfidence;
import es.upv.pin.backend.model.CostLevel;
import es.upv.pin.backend.model.ScheduleStatus;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class EvaluacionCandidatosService {
    private static final int EVALUACION = 40,
            INVESTIGACION = 1,
            MAX_LLAMADAS = 20,
            MAX_BUSQUEDAS = 100;
    private final ConfiguracionItinerarioService configuraciones;
    private final PreparacionCandidatosService preparacion;
    private final AiProvider ia;
    private final ResearchProvider investigacion;
    private final long plazoNanos;

    @org.springframework.beans.factory.annotation.Autowired
    public EvaluacionCandidatosService(
            ConfiguracionItinerarioService configuraciones,
            PreparacionCandidatosService preparacion,
            AiProvider ia,
            ResearchProvider investigacion) {
        this(configuraciones, preparacion, ia, investigacion, TimeUnit.SECONDS.toNanos(100));
    }

    EvaluacionCandidatosService(
            ConfiguracionItinerarioService configuraciones,
            PreparacionCandidatosService preparacion,
            AiProvider ia,
            ResearchProvider investigacion,
            long plazoNanos) {
        this.configuraciones = configuraciones;
        this.preparacion = preparacion;
        this.ia = ia;
        this.investigacion = investigacion;
        this.plazoNanos = plazoNanos;
    }

    public EvaluacionCandidatosDto evaluar(ConfiguracionItinerarioDto entrada) {
        long limite = System.nanoTime() + plazoNanos;
        var config = configuraciones.validar(entrada);
        var lugares = dentroDePlazo(() -> preparacion.preparar(config), limite);
        int disponible = minutosDisponibles(config.disponibilidad());
        if (lugares.isEmpty())
            return new EvaluacionCandidatosDto(
                    List.of(),
                    false,
                    List.of("No se encontraron candidatos para esta configuración."),
                    disponible,
                    0);
        List<String> avisos = new ArrayList<>();
        Map<String, ValoracionGemini> valoraciones = new HashMap<>();
        boolean fallback = false;
        try {
            if ((lugares.size() + EVALUACION - 1) / EVALUACION > MAX_LLAMADAS)
                throw new IllegalStateException();
            for (int i = 0; i < lugares.size(); i += EVALUACION) {
                var lote = lugares.subList(i, Math.min(i + EVALUACION, lugares.size()));
                var respuesta =
                        dentroDePlazo(
                                () ->
                                        ia.evaluar(
                                                lote,
                                                config.preferencias().intereses(),
                                                config.destino().nombre()),
                                limite);
                if (respuesta == null || respuesta.size() != lote.size())
                    throw new IllegalStateException();
                for (var lugar : lote) {
                    var v = respuesta.get(lugar.id());
                    if (v == null
                            || v.afinidad() < 0
                            || v.afinidad() > 100
                            || v.duracionMinutos() < 15
                            || v.duracionMinutos() > 480
                            || v.etiqueta() == null
                            || v.etiqueta().isBlank()
                            || v.motivo() == null
                            || v.motivo().isBlank()) throw new IllegalStateException();
                    valoraciones.put(lugar.id(), v);
                }
            }
        } catch (RuntimeException e) {
            fallback = true;
            valoraciones.clear();
            for (var lugar : lugares)
                valoraciones.put(lugar.id(), local(lugar, config.preferencias().intereses()));
            avisos.add(
                    "Las recomendaciones se han preparado a partir de tus intereses y del tipo de lugar;"
                            + " algunas pueden ser menos precisas.");
        }
        var ordenados =
                lugares.stream()
                        .sorted(
                                Comparator.comparingInt(
                                                (LugarDto l) -> valoraciones.get(l.id()).afinidad())
                                        .reversed())
                        .toList();
        List<LugarDto> investigables = diversificar(ordenados, valoraciones);
        Map<String, HallazgoGemini> hallazgos = new HashMap<>();
        int investigados = 0, llamadas = 0;
        if (!investigacion.disponible()) {
            avisos.add(
                    "Confirma los horarios y precios antes de ir: no hemos podido comprobar toda la información.");
        }
        for (int i = 0;
                investigacion.disponible()
                        && i < investigables.size()
                        && investigados < disponible
                        && llamadas < MAX_BUSQUEDAS;
                i += INVESTIGACION) {
            var lote = investigables.subList(i, Math.min(i + INVESTIGACION, investigables.size()));
            try {
                var respuesta =
                        dentroDePlazo(
                                () -> investigacion.investigar(lote, config.destino().nombre()),
                                limite);
                if (respuesta != null) {
                    for (var lugar : lote) {
                        var h = respuesta.get(lugar.id());
                        if (h != null && !fuentesValidas(h.fuentes()).isEmpty())
                            hallazgos.put(lugar.id(), h);
                    }
                }
            } catch (RuntimeException e) {
                avisos.add(
                        "Confirma los horarios y precios antes de ir: no hemos podido comprobar toda la información.");
                break;
            }
            llamadas += lote.size();
            for (var lugar : lote) {
                var h = hallazgos.get(lugar.id());
                boolean economico =
                        config.preferencias().nivelGasto()
                                == es.upv.pin.backend.model.NivelGasto.ECONOMICO;
                // Una descripción por sí sola no completa la investigación de datos prácticos.
                boolean tieneDatosPracticos = h != null && (fuenteValida(h.fuentePrecio())
                        || fuenteValida(h.fuenteHorario()) || fuenteValida(h.fuenteDuracion()));
                if (tieneDatosPracticos
                        && (!economico || coste(h) == CostLevel.FREE || coste(h) == CostLevel.LOW))
                    investigados += valoraciones.get(lugar.id()).duracionMinutos();
            }
        }
        if (llamadas >= MAX_BUSQUEDAS && investigados < disponible && ordenados.size() > llamadas)
            avisos.add(
                    "Confirma los horarios y precios antes de ir: no hemos podido comprobar toda la información.");
        List<CandidatoEvaluadoDto> candidatos = new ArrayList<>();
        for (var lugar : ordenados) {
            var v = valoraciones.get(lugar.id());
            var h = hallazgos.get(lugar.id());
            var fuentes = h == null ? List.<String>of() : fuentesValidas(h.fuentes());
            String descripcion = h != null && fuenteValida(h.fuenteDescripcion())
                            && fuentes.contains(h.fuenteDescripcion())
                    ? texto(h.descripcion()) : null;
            String fuenteDescripcion = descripcion == null ? null : h.fuenteDescripcion();
            String horario =
                    h != null && fuenteValida(h.fuenteHorario()) && texto(h.horario()) != null
                            ? texto(h.horario())
                            : lugar.horario();
            String fuenteHorario =
                    h != null && fuenteValida(h.fuenteHorario()) && texto(h.horario()) != null
                            ? "Fuentes externas"
                            : horario == null ? null : "Geoapify (sin verificar)";
            int duracion =
                    h != null
                                    && fuenteValida(h.fuenteDuracion())
                                    && h.duracionMinutos() != null
                                    && h.duracionMinutos() >= 15
                                    && h.duracionMinutos() <= 480
                            ? h.duracionMinutos()
                            : v.duracionMinutos();
            candidatos.add(
                    new CandidatoEvaluadoDto(
                            lugar,
                            v.afinidad(),
                            duracion,
                            etiquetaSegura(lugar),
                            motivoSeguro(lugar, config.preferencias().intereses()),
                            coste(h),
                            h == null ? null : texto(h.informacion()),
                            fuentes,
                            horario,
                            fuenteHorario,
                            fallback ? "LOCAL" : "GEMINI",
                            coste(h) == CostLevel.UNKNOWN
                                    ? CostConfidence.UNKNOWN
                                    : CostConfidence.ESTIMATED,
                            horario == null || fuenteHorario.startsWith("Geoapify")
                                    ? ScheduleStatus.UNVERIFIED
                                    : ScheduleStatus.PARTIAL,
                            h == null || !fuenteValida(h.fuentePrecio()) ? null : h.precioMinimo(),
                            h == null || !fuenteValida(h.fuentePrecio()) ? null : h.precioMaximo(),
                            h == null || !fuenteValida(h.fuentePrecio()) ? null : h.moneda(),
                            descripcion,
                            fuenteDescripcion));
        }
        return new EvaluacionCandidatosDto(
                List.copyOf(candidatos), fallback, List.copyOf(avisos), disponible, investigados);
    }

    private static <T> T dentroDePlazo(Callable<T> tarea, long limite) {
        long restante = limite - System.nanoTime();
        if (restante <= 0)
            throw new IllegalStateException(
                    "Se agotó el tiempo disponible para buscar candidatos.");
        ExecutorService ejecutor = Executors.newVirtualThreadPerTaskExecutor();
        Future<T> futuro = ejecutor.submit(tarea);
        try {
            return futuro.get(restante, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            throw new IllegalStateException(
                    "Se agotó el tiempo disponible para buscar candidatos.", e);
        } catch (InterruptedException e) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La búsqueda fue interrumpida.", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException("No se pudo completar la búsqueda.", e.getCause());
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private List<LugarDto> diversificar(
            List<LugarDto> ordenados, Map<String, ValoracionGemini> puntuaciones) {
        List<LugarDto> pendientes = new ArrayList<>(ordenados), resultado = new ArrayList<>();
        Map<CategoriaTuristica, Integer> usadas = new EnumMap<>(CategoriaTuristica.class);
        while (!pendientes.isEmpty()) {
            int mejor = puntuaciones.get(pendientes.get(0).id()).afinidad();
            LugarDto elegido =
                    pendientes.stream()
                            .filter(l -> puntuaciones.get(l.id()).afinidad() >= mejor - 5)
                            .min(
                                    Comparator.comparingInt(
                                            l ->
                                                    l.categorias().stream()
                                                            .mapToInt(
                                                                    c -> usadas.getOrDefault(c, 0))
                                                            .min()
                                                            .orElse(0)))
                            .orElseThrow();
            resultado.add(elegido);
            pendientes.remove(elegido);
            elegido.categorias().forEach(c -> usadas.merge(c, 1, Integer::sum));
        }
        return resultado;
    }

    private ValoracionGemini local(LugarDto lugar, List<CategoriaTuristica> intereses) {
        long coincidencias = lugar.categorias().stream().filter(intereses::contains).count();
        int duracion =
                lugar.categorias().contains(CategoriaTuristica.ARTE_MUSEOS)
                        ? 90
                        : lugar.categorias().contains(CategoriaTuristica.NATURALEZA_PARQUES)
                                ? 90
                                : lugar.categorias().contains(CategoriaTuristica.GASTRONOMIA)
                                        ? 75
                                        : 60;
        String motivo =
                coincidencias > 0
                        ? "Coincide con: "
                                + lugar.categorias().stream()
                                        .filter(intereses::contains)
                                        .map(CategoriaTuristica::getNombre)
                                        .collect(java.util.stream.Collectors.joining(", "))
                        : "Valoración basada en las categorías disponibles.";
        return new ValoracionGemini(
                intereses.isEmpty() ? 50 : (int) Math.min(100, 40 + 20 * coincidencias),
                duracion,
                "Estimación local",
                motivo);
    }

    private static String etiquetaSegura(LugarDto lugar) {
        if (lugar.subcategorias() != null && !lugar.subcategorias().isEmpty())
            return lugar.subcategorias().get(0).getNombre();
        if (lugar.categorias() != null && !lugar.categorias().isEmpty())
            return lugar.categorias().get(0).getNombre();
        return "Sin categoría";
    }

    private static String motivoSeguro(LugarDto lugar, List<CategoriaTuristica> intereses) {
        if (lugar.categorias() == null || lugar.categorias().isEmpty())
            return "No hay categorías verificables para explicar la afinidad.";
        var coincidencias = lugar.categorias().stream()
                .filter(intereses::contains)
                .map(CategoriaTuristica::getNombre)
                .toList();
        if (!coincidencias.isEmpty())
            return "Coincide con tus intereses: " + String.join(", ", coincidencias) + ".";
        return "Clasificado como: "
                + lugar.categorias().stream()
                        .map(CategoriaTuristica::getNombre)
                        .collect(java.util.stream.Collectors.joining(", "))
                + ".";
    }

    static int minutosDisponibles(FechaHoraDto fechas) {
        int minutos = 0;
        for (LocalDate dia = fechas.llegada().toLocalDate();
                !dia.isAfter(fechas.salida().toLocalDate());
                dia = dia.plusDays(1)) {
            LocalDateTime inicio = dia.atTime(10, 0), fin = dia.atTime(18, 0);
            if (inicio.isBefore(fechas.llegada())) inicio = fechas.llegada();
            if (fin.isAfter(fechas.salida())) fin = fechas.salida();
            if (fin.isAfter(inicio)) minutos += (int) Duration.between(inicio, fin).toMinutes();
        }
        return minutos;
    }

    static CostLevel coste(HallazgoGemini h) {
        if (h == null
                || !fuenteValida(h.fuentePrecio())
                || h.moneda() == null
                || !h.moneda().strip().equalsIgnoreCase("EUR")) return CostLevel.UNKNOWN;
        Double min = h.precioMinimo(), max = h.precioMaximo();
        if (min == null
                || max == null
                || !Double.isFinite(min)
                || !Double.isFinite(max)
                || min < 0
                || max < min) return CostLevel.UNKNOWN;
        return banda(max);
    }

    private static CostLevel banda(double p) {
        return p == 0
                ? CostLevel.FREE
                : p <= 25 ? CostLevel.LOW : p <= 50 ? CostLevel.MEDIUM : CostLevel.HIGH;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static boolean fuenteValida(String fuente) {
        return fuente != null && !fuentesValidas(List.of(fuente)).isEmpty();
    }

    private static List<String> fuentesValidas(List<String> fuentes) {
        if (fuentes == null) return List.of();
        return fuentes.stream()
                .filter(
                        s -> {
                            if (s == null) return false;
                            try {
                                URI u = URI.create(s);
                                return ("https".equalsIgnoreCase(u.getScheme())
                                                || "http".equalsIgnoreCase(u.getScheme()))
                                        && u.getHost() != null;
                            } catch (IllegalArgumentException e) {
                                return false;
                            }
                        })
                .distinct()
                .toList();
    }
}
