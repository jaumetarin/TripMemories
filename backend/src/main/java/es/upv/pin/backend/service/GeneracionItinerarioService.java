package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.BloqueItinerarioDto;
import es.upv.pin.backend.dto.CandidatoEvaluadoDto;
import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.dto.DiaItinerarioDto;
import es.upv.pin.backend.dto.EvaluacionCandidatosDto;
import es.upv.pin.backend.dto.FechaHoraDto;
import es.upv.pin.backend.dto.ItinerarioGeneradoDto;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.CostLevel;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.model.NivelGasto;
import es.upv.pin.backend.model.ScheduleStatus;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GeneracionItinerarioService {
    private static final int MAXIMO_PUNTOS = 50;
    private static final int BANDA_AFINIDAD = 5;
    private static final int HUECO_PARCIAL_MINUTOS = 15;

    private final ConfiguracionItinerarioService configuraciones;
    private final EvaluacionCandidatosService evaluacion;
    private final DesplazamientoService desplazamientos;

    public GeneracionItinerarioService(
            ConfiguracionItinerarioService configuraciones,
            EvaluacionCandidatosService evaluacion,
            DesplazamientoService desplazamientos) {
        this.configuraciones = configuraciones;
        this.evaluacion = evaluacion;
        this.desplazamientos = desplazamientos;
    }

    public ItinerarioGeneradoDto generar(ConfiguracionItinerarioDto entrada) {
        ConfiguracionItinerarioDto config = configuraciones.validar(entrada);
        List<Ventana> ventanas = ventanas(config.disponibilidad());
        List<String> advertencias = new ArrayList<>();

        // No consumimos proveedores si el viaje no tiene tiempo dentro de 10:00-18:00.
        if (ventanas.stream().noneMatch(Ventana::disponible)) {
            for (Ventana ventana : ventanas) {
                advertencias.add(avisoSinVentana(ventana.fecha()));
            }
            return new ItinerarioGeneradoDto(
                    diasSinActividad(ventanas), List.copyOf(advertencias), true, 0, 0);
        }

        EvaluacionCandidatosDto resultado = evaluacion.evaluar(config);
        List<CandidatoEvaluadoDto> candidatos = resultado.candidatos();
        List<CandidatoEvaluadoDto> pendientes = seleccionar(candidatos);
        List<PuntoRutaDto> puntos = pendientes.stream()
                .map(c -> new PuntoRutaDto(
                        c.lugar().id(), c.lugar().latitud(), c.lugar().longitud()))
                .toList();

        List<DesplazamientoDto> tramos = puntos.size() < 2
                ? List.of()
                : desplazamientos.calcular(puntos);
        Map<Par, DesplazamientoDto> rutas = new HashMap<>();
        for (DesplazamientoDto tramo : tramos) {
            rutas.put(new Par(tramo.origenId(), tramo.destinoId()), tramo);
        }

        advertencias.addAll(resultado.advertencias());
        long conCoordenadas = candidatos.stream().filter(GeneracionItinerarioService::tieneCoordenadas).count();
        if (conCoordenadas < candidatos.size()) {
            advertencias.add("Algunos lugares no tienen coordenadas validas y no se han programado.");
        }
        if (conCoordenadas > MAXIMO_PUNTOS) {
            advertencias.add("Se han considerado 50 lugares para los recorridos; otros candidatos quedaron fuera.");
        }
        if (tramos.size() < puntos.size() * (puntos.size() - 1)
                || tramos.stream().anyMatch(t -> !rutaValida(t))) {
            advertencias.add(
                    "Algunas rutas a pie no estan disponibles; esas parejas de lugares se han omitido.");
        }

        List<DiaItinerarioDto> dias = new ArrayList<>();
        boolean parcial = false;
        boolean hayCondicionadas = false;
        boolean hayVisitas = false;

        for (Ventana ventana : ventanas) {
            if (!ventana.disponible()) {
                dias.add(new DiaItinerarioDto(ventana.fecha(), null, null, List.of()));
                advertencias.add(avisoSinVentana(ventana.fecha()));
                parcial = true;
                continue;
            }

            List<BloqueItinerarioDto> bloques = new ArrayList<>();
            Map<CategoriaTuristica, Integer> categoriasDelDia =
                    new EnumMap<>(CategoriaTuristica.class);
            LocalDateTime cursor = ventana.inicio();
            CandidatoEvaluadoDto anterior = null;

            while (true) {
                List<Opcion> factibles = opcionesFactibles(
                        pendientes, anterior, rutas, cursor, ventana.fin());
                if (factibles.isEmpty()) {
                    break;
                }

                int mejorAfinidad = factibles.stream()
                        .mapToInt(o -> o.candidato().afinidad())
                        .max()
                        .orElseThrow();
                CandidatoEvaluadoDto lugarAnterior = anterior;
                Opcion elegido = factibles.stream()
                        .filter(o -> o.candidato().afinidad() >= mejorAfinidad - BANDA_AFINIDAD)
                        .min(ordenOpciones(
                                lugarAnterior, config.preferencias().distanciaKm(),
                                config.preferencias().nivelGasto(), categoriasDelDia))
                        .orElseThrow();

                if (elegido.tramo() != null && elegido.minutosTraslado() > 0) {
                    LocalDateTime llegada = cursor.plusMinutes(elegido.minutosTraslado());
                    DesplazamientoDto tramo = elegido.tramo();
                    bloques.add(new BloqueItinerarioDto(
                            TipoBloqueItinerario.TRASLADO, cursor, llegada, null,
                            tramo.origenId(), tramo.destinoId(), tramo.distanciaMetros(),
                            tramo.duracionSegundos(), tramo.fuente(), false, "Desplazamiento"));
                    cursor = llegada;
                }

                CandidatoEvaluadoDto candidato = elegido.candidato();
                LocalDateTime finVisita = cursor.plusMinutes(candidato.duracionMinutos());
                boolean condicionada = candidato.estadoHorario() != ScheduleStatus.VERIFIED;
                bloques.add(new BloqueItinerarioDto(
                        TipoBloqueItinerario.VISITA, cursor, finVisita, candidato,
                        null, null, null, null, null, condicionada,
                        condicionada ? "Horario pendiente de confirmar" : "Visita"));
                hayCondicionadas |= condicionada;
                hayVisitas = true;
                cursor = finVisita;
                pendientes.remove(candidato);
                anterior = candidato;
                if (candidato.lugar().categorias() != null) {
                    candidato.lugar().categorias()
                            .forEach(c -> categoriasDelDia.merge(c, 1, Integer::sum));
                }
            }

            if (cursor.isBefore(ventana.fin())) {
                bloques.add(new BloqueItinerarioDto(
                        TipoBloqueItinerario.TIEMPO_LIBRE, cursor, ventana.fin(), null,
                        null, null, null, null, null, false, "Tiempo libre"));
                parcial |= Duration.between(cursor, ventana.fin()).toMinutes()
                        >= HUECO_PARCIAL_MINUTOS;
            }
            parcial |= anterior == null;
            dias.add(new DiaItinerarioDto(
                    ventana.fecha(), ventana.inicio(), ventana.fin(), List.copyOf(bloques)));
        }

        if (hayCondicionadas) {
            advertencias.add("Los horarios no verificados requieren confirmacion.");
        }
        if (hayVisitas) {
            advertencias.add("No se han calculado trayectos desde o hacia el alojamiento.");
        }
        return new ItinerarioGeneradoDto(
                List.copyOf(dias), List.copyOf(advertencias), parcial,
                candidatos.size(), puntos.size());
    }

    private static List<Ventana> ventanas(FechaHoraDto fechas) {
        List<Ventana> resultado = new ArrayList<>();
        for (LocalDate fecha = fechas.llegada().toLocalDate();
                !fecha.isAfter(fechas.salida().toLocalDate());
                fecha = fecha.plusDays(1)) {
            LocalDateTime inicio = fecha.atTime(10, 0);
            LocalDateTime fin = fecha.atTime(18, 0);
            if (inicio.isBefore(fechas.llegada())) {
                inicio = fechas.llegada();
            }
            if (fin.isAfter(fechas.salida())) {
                fin = fechas.salida();
            }
            resultado.add(new Ventana(fecha, inicio, fin));
        }
        return resultado;
    }

    private static List<DiaItinerarioDto> diasSinActividad(List<Ventana> ventanas) {
        return ventanas.stream()
                .map(v -> new DiaItinerarioDto(v.fecha(), null, null, List.of()))
                .toList();
    }

    private static String avisoSinVentana(LocalDate fecha) {
        return "El dia " + fecha + " no tiene tiempo disponible entre las 10:00 y las 18:00.";
    }

    private static List<Opcion> opcionesFactibles(
            List<CandidatoEvaluadoDto> pendientes,
            CandidatoEvaluadoDto anterior,
            Map<Par, DesplazamientoDto> rutas,
            LocalDateTime cursor,
            LocalDateTime finDia) {
        List<Opcion> opciones = new ArrayList<>();
        long segundosRestantes = Duration.between(cursor, finDia).getSeconds();
        for (CandidatoEvaluadoDto candidato : pendientes) {
            DesplazamientoDto tramo = anterior == null
                    ? null
                    : rutas.get(new Par(anterior.lugar().id(), candidato.lugar().id()));
            if (anterior != null && !rutaValida(tramo)) {
                continue;
            }
            if (tramo != null && tramo.duracionSegundos() > segundosRestantes) {
                continue;
            }
            long minutosTraslado = tramo == null
                    ? 0
                    : (long) Math.ceil(tramo.duracionSegundos() / 60.0);
            if (candidato.duracionMinutos() > 0
                    && !cursor.plusMinutes(minutosTraslado)
                            .plusMinutes(candidato.duracionMinutos())
                            .isAfter(finDia)) {
                opciones.add(new Opcion(candidato, tramo, minutosTraslado));
            }
        }
        return opciones;
    }

    private static Comparator<Opcion> ordenOpciones(
            CandidatoEvaluadoDto anterior,
            double distanciaDeseadaKm,
            NivelGasto gasto,
            Map<CategoriaTuristica, Integer> categoriasDelDia) {
        return Comparator
                .comparingInt((Opcion o) ->
                        anterior == null
                                || o.tramo().distanciaMetros() <= distanciaDeseadaKm * 1000
                                ? 0 : 1)
                .thenComparingDouble(o -> o.tramo() == null
                        ? 0 : o.tramo().duracionSegundos())
                .thenComparingInt(o -> prioridadCoste(o.candidato().coste(), gasto))
                .thenComparingInt(o -> repeticionCategoria(
                        o.candidato(), categoriasDelDia))
                .thenComparing(Comparator.comparingInt(
                        (Opcion o) -> o.candidato().afinidad()).reversed())
                .thenComparing(o -> o.candidato().lugar().id());
    }

    private static List<CandidatoEvaluadoDto> seleccionar(
            List<CandidatoEvaluadoDto> candidatos) {
        List<CandidatoEvaluadoDto> restantes = new ArrayList<>(
                candidatos.stream()
                        .filter(GeneracionItinerarioService::tieneCoordenadas)
                        .sorted(Comparator.comparingInt(CandidatoEvaluadoDto::afinidad)
                                .reversed().thenComparing(c -> c.lugar().id()))
                        .toList());
        List<CandidatoEvaluadoDto> elegidos = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<CategoriaTuristica, Integer> categorias =
                new EnumMap<>(CategoriaTuristica.class);

        while (!restantes.isEmpty() && elegidos.size() < MAXIMO_PUNTOS) {
            int mejorAfinidad = restantes.get(0).afinidad();
            CandidatoEvaluadoDto elegido = restantes.stream()
                    .filter(c -> c.afinidad() >= mejorAfinidad - BANDA_AFINIDAD)
                    .min(Comparator.comparingInt(
                                    (CandidatoEvaluadoDto c) ->
                                            repeticionCategoria(c, categorias))
                            .thenComparing(Comparator.comparingInt(
                                    CandidatoEvaluadoDto::afinidad).reversed())
                            .thenComparing(c -> c.lugar().id()))
                    .orElseThrow();
            restantes.remove(elegido);
            if (!ids.add(elegido.lugar().id())) {
                continue;
            }
            elegidos.add(elegido);
            if (elegido.lugar().categorias() != null) {
                elegido.lugar().categorias()
                        .forEach(c -> categorias.merge(c, 1, Integer::sum));
            }
        }
        return elegidos;
    }

    private static boolean tieneCoordenadas(CandidatoEvaluadoDto candidato) {
        if (candidato == null || candidato.lugar() == null
                || candidato.lugar().id() == null
                || candidato.lugar().id().isBlank()) {
            return false;
        }
        return coordenadaValida(candidato.lugar().latitud(), 90)
                && coordenadaValida(candidato.lugar().longitud(), 180);
    }

    private static boolean coordenadaValida(Double valor, int limite) {
        return valor != null && Double.isFinite(valor) && Math.abs(valor) <= limite;
    }

    private static boolean rutaValida(DesplazamientoDto tramo) {
        return tramo != null
                && tramo.estado() == EstadoDesplazamiento.DISPONIBLE
                && tramo.duracionSegundos() != null
                && Double.isFinite(tramo.duracionSegundos())
                && tramo.duracionSegundos() >= 0
                && tramo.distanciaMetros() != null
                && Double.isFinite(tramo.distanciaMetros())
                && tramo.distanciaMetros() >= 0;
    }

    private static int repeticionCategoria(
            CandidatoEvaluadoDto candidato,
            Map<CategoriaTuristica, Integer> usadas) {
        if (candidato.lugar().categorias() == null
                || candidato.lugar().categorias().isEmpty()) {
            return 1;
        }
        return candidato.lugar().categorias().stream()
                .mapToInt(c -> usadas.getOrDefault(c, 0))
                .min().orElse(1);
    }

    private static int prioridadCoste(CostLevel coste, NivelGasto gasto) {
        if (gasto == NivelGasto.SIN_PREFERENCIA
                || gasto == NivelGasto.FLEXIBLE) {
            return 0;
        }
        if (coste == null || coste == CostLevel.UNKNOWN) {
            return 2;
        }
        if (gasto == NivelGasto.ECONOMICO) {
            return switch (coste) {
                case FREE -> 0;
                case LOW -> 1;
                case MEDIUM -> 3;
                case HIGH -> 4;
                case UNKNOWN -> 2;
            };
        }
        return switch (coste) {
            case FREE, LOW, MEDIUM -> 0;
            case UNKNOWN -> 2;
            case HIGH -> 3;
        };
    }

    private record Ventana(LocalDate fecha, LocalDateTime inicio, LocalDateTime fin) {
        boolean disponible() {
            return fin.isAfter(inicio);
        }
    }

    private record Par(String origenId, String destinoId) {}

    private record Opcion(
            CandidatoEvaluadoDto candidato,
            DesplazamientoDto tramo,
            long minutosTraslado) {}
}
