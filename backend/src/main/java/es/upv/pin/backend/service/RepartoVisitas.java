package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.CandidatoEvaluadoDto;
import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.model.EstadoHorarioVisita;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Reparto orientativo y local: las rutas y horas exactas las decide el Planner. */
final class RepartoVisitas {
    record Jornada(LocalDate fecha, LocalDateTime inicio, LocalDateTime fin) {}

    private final List<Jornada> jornadas;
    private final ConfiguracionItinerarioDto config;
    private final EvaluadorHorarioService horarios;
    private final Map<String, Set<LocalDate>> fechas = new HashMap<>();
    private final Map<String, LocalDate> reservas = new HashMap<>();

    RepartoVisitas(List<Jornada> jornadas, List<CandidatoEvaluadoDto> candidatos,
            ConfiguracionItinerarioDto config, EvaluadorHorarioService horarios,
            List<DesplazamientoDto> rutas, String origen) {
        this.jornadas = jornadas;
        this.config = config;
        this.horarios = horarios;
        // Un candidato sin ningún camino desde el origen no debe inflar las reservas.
        Map<String, Double> segundosDesdeOrigen = new HashMap<>();
        segundosDesdeOrigen.put(origen, 0.0);
        boolean cambio;
        do {
            cambio = false;
            for (var ruta : rutas) {
                if (ruta.estado() == EstadoDesplazamiento.DISPONIBLE
                        && ruta.duracionSegundos() != null && Double.isFinite(ruta.duracionSegundos())
                        && ruta.duracionSegundos() >= 0 && ruta.distanciaMetros() != null
                        && Double.isFinite(ruta.distanciaMetros()) && ruta.distanciaMetros() >= 0
                        && segundosDesdeOrigen.containsKey(ruta.origenId())) {
                    double llegada = segundosDesdeOrigen.get(ruta.origenId()) + ruta.duracionSegundos();
                    if (llegada < segundosDesdeOrigen.getOrDefault(ruta.destinoId(), Double.POSITIVE_INFINITY)) {
                        segundosDesdeOrigen.put(ruta.destinoId(), llegada);
                        cambio = true;
                    }
                }
            }
        } while (cambio);
        for (var candidato : candidatos) {
            Set<LocalDate> compatibles = new HashSet<>();
            var segundos = segundosDesdeOrigen.get(candidato.lugar().id());
            if (segundos != null) {
                for (var jornada : jornadas) {
                    if (cabe(candidato, jornada.inicio(), jornada.fin(), (long) Math.ceil(segundos / 60.0)))
                        compatibles.add(jornada.fecha());
                }
            }
            fechas.put(candidato.lugar().id(), Set.copyOf(compatibles));
        }
    }

    boolean aplazable(CandidatoEvaluadoDto candidato, LocalDate fecha) {
        return fechas.getOrDefault(candidato.lugar().id(), Set.of()).stream().anyMatch(d -> d.isAfter(fecha));
    }

    boolean reservadoHoy(CandidatoEvaluadoDto candidato, LocalDate fecha) {
        return fecha.equals(reservas.get(candidato.lugar().id()));
    }

    boolean reservadoOtroDia(CandidatoEvaluadoDto candidato, LocalDate fecha) {
        var reserva = reservas.get(candidato.lugar().id());
        return reserva != null && reserva.isAfter(fecha);
    }

    boolean cabeHoy(CandidatoEvaluadoDto candidato, LocalDateTime desde, long minutosTraslado) {
        return jornadas.stream().filter(j -> j.fecha().equals(desde.toLocalDate())).findFirst()
                .map(j -> cabe(candidato, desde, j.fin(), minutosTraslado)).orElse(false);
    }

    /** Se recalcula con lo que queda, para recuperar las reservas que otro día no pudo usar. */
    int objetivo(LocalDate fecha, List<CandidatoEvaluadoDto> pendientes) {
        var futuras = jornadas.stream().filter(j -> !j.fecha().isBefore(fecha) && capacidad(j) > 0).toList();
        Map<LocalDate, Integer> minutos = new HashMap<>();
        Map<LocalDate, Integer> visitas = new HashMap<>();
        var ordenados = pendientes.stream().sorted(Comparator
                .comparingLong((CandidatoEvaluadoDto c) -> futuras.stream().filter(j -> compatible(c, j)).count())
                .thenComparingInt(c -> ReglasSeleccionLugares.orden(c.lugar(), config))
                .thenComparing(Comparator.comparingInt(CandidatoEvaluadoDto::duracionMinutos).reversed())
                .thenComparing(c -> c.lugar().id())).toList();
        // Antes de añadir segundas visitas, buscar una reserva distinta para cada día.
        // Si una asignación estorba a otra, se recoloca: no basta un reparto voraz.
        Map<String, Jornada> destinos = new HashMap<>();
        for (var jornada : futuras) reservar(jornada, ordenados, new HashSet<>(), destinos);
        reservas.clear();
        destinos.forEach((id, jornada) -> reservas.put(id, jornada.fecha()));
        for (var candidato : ordenados) {
            var jornada = destinos.get(candidato.lugar().id());
            if (jornada != null) {
                minutos.merge(jornada.fecha(), candidato.duracionMinutos() + config.ritmo().margenMinutos(), Integer::sum);
                visitas.merge(jornada.fecha(), 1, Integer::sum);
            }
        }
        for (var candidato : ordenados) {
            if (destinos.containsKey(candidato.lugar().id())) continue;
            int coste = candidato.duracionMinutos() + config.ritmo().margenMinutos();
            var elegida = futuras.stream().filter(j -> compatible(candidato, j)
                            && minutos.getOrDefault(j.fecha(), 0) + coste <= capacidad(j))
                    .min(Comparator.comparingDouble((Jornada j) ->
                                    (double) minutos.getOrDefault(j.fecha(), 0) / capacidad(j))
                            .thenComparing(Jornada::fecha));
            elegida.ifPresent(j -> {
                minutos.merge(j.fecha(), coste, Integer::sum);
                visitas.merge(j.fecha(), 1, Integer::sum);
            });
        }
        return visitas.getOrDefault(fecha, 0);
    }

    private boolean reservar(Jornada jornada, List<CandidatoEvaluadoDto> candidatos,
            Set<String> probados, Map<String, Jornada> destinos) {
        for (var candidato : candidatos) {
            String id = candidato.lugar().id();
            if (!compatible(candidato, jornada)
                    || candidato.duracionMinutos() + config.ritmo().margenMinutos() > capacidad(jornada)
                    || !probados.add(id)) continue;
            var anterior = destinos.get(id);
            if (anterior == null || reservar(anterior, candidatos, probados, destinos)) {
                destinos.put(id, jornada);
                return true;
            }
        }
        return false;
    }

    private boolean compatible(CandidatoEvaluadoDto c, Jornada j) {
        return fechas.getOrDefault(c.lugar().id(), Set.of()).contains(j.fecha());
    }

    private long capacidad(Jornada jornada) {
        var inicioComida = jornada.fecha().atTime(config.ritmo().comidaInicio());
        var finComida = jornada.fecha().atTime(config.ritmo().comidaFin());
        var desde = inicioComida.isAfter(jornada.inicio()) ? inicioComida : jornada.inicio();
        var hasta = finComida.isBefore(jornada.fin()) ? finComida : jornada.fin();
        return Math.max(0, Duration.between(jornada.inicio(), jornada.fin()).toMinutes()
                - Math.max(0, Duration.between(desde, hasta).toMinutes()));
    }

    private boolean cabe(CandidatoEvaluadoDto c, LocalDateTime desde, LocalDateTime hasta, long traslado) {
        var comidaInicio = desde.toLocalDate().atTime(config.ritmo().comidaInicio());
        var comidaFin = desde.toLocalDate().atTime(config.ritmo().comidaFin());
        var primerInicio = desde.plusMinutes(traslado + config.ritmo().margenMinutos());
        for (var inicio = primerInicio; !inicio.plusMinutes(c.duracionMinutos()).isAfter(hasta);
                inicio = inicio.plusMinutes(1)) {
            var fin = inicio.plusMinutes(c.duracionMinutos());
            if (inicio.isBefore(comidaFin) && fin.isAfter(comidaInicio)) continue;
            if (horarios.evaluar(c, inicio, fin) != EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE) return true;
        }
        return false;
    }
}
