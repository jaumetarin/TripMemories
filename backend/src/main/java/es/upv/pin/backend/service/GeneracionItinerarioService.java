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
import es.upv.pin.backend.dto.OpcionComidaDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.CostLevel;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.model.EstadoHorarioVisita;
import es.upv.pin.backend.dto.ResultadoValidacionPlan;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final EvaluadorHorarioService evaluadorHorario;
    private final ValidadorPlanService validadorPlan;
    private final RecomendacionComidaService recomendaciones;

    public GeneracionItinerarioService(
            ConfiguracionItinerarioService configuraciones,
            EvaluacionCandidatosService evaluacion,
            DesplazamientoService desplazamientos) {
        this(configuraciones, evaluacion, desplazamientos, new EvaluadorHorarioService(), new ValidadorPlanService(), null);
    }

    public GeneracionItinerarioService(ConfiguracionItinerarioService configuraciones,
            EvaluacionCandidatosService evaluacion, DesplazamientoService desplazamientos,
            EvaluadorHorarioService evaluadorHorario, ValidadorPlanService validadorPlan) {
        this(configuraciones, evaluacion, desplazamientos, evaluadorHorario, validadorPlan, null);
    }

    @Autowired
    public GeneracionItinerarioService(ConfiguracionItinerarioService configuraciones,
            EvaluacionCandidatosService evaluacion, DesplazamientoService desplazamientos,
            EvaluadorHorarioService evaluadorHorario, ValidadorPlanService validadorPlan,
            RecomendacionComidaService recomendaciones) {
        this.configuraciones = configuraciones;
        this.evaluacion = evaluacion;
        this.desplazamientos = desplazamientos;
        this.evaluadorHorario = evaluadorHorario;
        this.validadorPlan = validadorPlan;
        this.recomendaciones = recomendaciones;
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
        List<OpcionComidaDto> restaurantes = List.of();
        if (recomendaciones != null && config.preferencias().intereses().contains(CategoriaTuristica.GASTRONOMIA)
                && ventanas.stream().anyMatch(v -> v.disponible()
                    && !v.fecha().atTime(config.ritmo().comidaInicio()).isBefore(v.inicio())
                    && !v.fecha().atTime(config.ritmo().comidaFin()).isAfter(v.fin()))) {
            var comidas = recomendaciones.buscar(config.destino().id(), config.preferencias(), Set.of());
            restaurantes = comidas.opciones();
            advertencias.addAll(comidas.advertencias());
        }
        Set<String> idsRestaurantes = new HashSet<>();
        restaurantes.forEach(r -> idsRestaurantes.add(r.id()));
        List<CandidatoEvaluadoDto> seleccionados = seleccionar(candidatos).stream()
                .filter(c -> !idsRestaurantes.contains(c.lugar().id()))
                .toList();
        List<CandidatoEvaluadoDto> pendientes = seleccionados.stream()
                .limit(MAXIMO_PUNTOS - restaurantes.size()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<PuntoRutaDto> puntos = new ArrayList<>(pendientes.stream()
                .map(c -> new PuntoRutaDto(c.lugar().id(), c.lugar().latitud(), c.lugar().longitud())).toList());
        restaurantes.forEach(r -> puntos.add(new PuntoRutaDto(r.id(), r.latitud(), r.longitud())));
        Set<String> conflictosHorario = new HashSet<>();

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
        if (conCoordenadas > MAXIMO_PUNTOS || seleccionados.size() > pendientes.size()) {
            advertencias.add("La matriz de rutas admite 50 puntos entre visitas y restaurantes; otros candidatos quedaron fuera.");
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
        boolean hayComidaGenerica = false;

        for (Ventana ventana : ventanas) {
            if (!ventana.disponible()) {
                dias.add(new DiaItinerarioDto(ventana.fecha(), null, null, List.of()));
                advertencias.add(avisoSinVentana(ventana.fecha()));
                parcial = true;
                continue;
            }

            List<BloqueItinerarioDto> bloques = new ArrayList<>();
            boolean visitasDia = false;
            Map<CategoriaTuristica, Integer> categoriasDelDia =
                    new EnumMap<>(CategoriaTuristica.class);
            LocalDateTime cursor = ventana.inicio();
            String anterior = null;
            LocalDateTime comidaInicio = ventana.fecha().atTime(config.ritmo().comidaInicio());
            LocalDateTime comidaFin = ventana.fecha().atTime(config.ritmo().comidaFin());
            boolean comidaCompleta = !comidaInicio.isBefore(ventana.inicio()) && !comidaFin.isAfter(ventana.fin());
            boolean comidaPendiente = comidaFin.isAfter(ventana.inicio()) && comidaInicio.isBefore(ventana.fin());
            if (comidaPendiente) {
                if (comidaInicio.isBefore(ventana.inicio())) comidaInicio = ventana.inicio();
                if (comidaFin.isAfter(ventana.fin())) comidaFin = ventana.fin();
            }
            int actividad = 0;

            while (true) {
                LocalDateTime limite = comidaPendiente ? comidaInicio : ventana.fin();
                if (actividad >= config.ritmo().descansoCadaMinutos()
                        && !cursor.plusMinutes(config.ritmo().descansoDuracionMinutos()).isAfter(limite)) {
                    LocalDateTime finDescanso = cursor.plusMinutes(config.ritmo().descansoDuracionMinutos());
                    bloques.add(bloqueSimple(TipoBloqueItinerario.DESCANSO, cursor, finDescanso, "Descanso"));
                    cursor = finDescanso;
                    actividad = 0;
                    continue;
                }
                List<Opcion> factibles = opcionesFactibles(
                        pendientes, anterior, rutas, cursor, limite, config.ritmo().margenMinutos(), conflictosHorario);
                if (comidaPendiente && comidaCompleta && !restaurantes.isEmpty()) {
                    List<Opcion> conLlegada = new ArrayList<>();
                    for (Opcion opcion : factibles) {
                        for (OpcionComidaDto restaurante : restaurantesAlcanzables(restaurantes,
                                opcion.candidato().lugar().id(), rutas,
                                cursorFinOpcion(cursor, opcion, config.ritmo().margenMinutos()), comidaInicio,
                                config.ritmo().margenMinutos())) {
                            if (estadoHorarioRestaurante(restaurante, comidaInicio, comidaFin)
                                    != EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE) {
                                conLlegada.add(opcion);
                                break;
                            }
                        }
                    }
                    if (!conLlegada.isEmpty()) factibles = conLlegada;
                }
                if (factibles.isEmpty()) {
                    LocalDateTime siguiente = siguienteInicioConVisita(
                            pendientes, anterior, rutas, cursor, limite,
                            config.ritmo().margenMinutos(), conflictosHorario);
                    if (siguiente != null) {
                        bloques.add(bloqueSimple(TipoBloqueItinerario.TIEMPO_LIBRE,
                                cursor, siguiente, "Espera hasta el horario de una visita"));
                        parcial |= Duration.between(cursor, siguiente).toMinutes() >= HUECO_PARCIAL_MINUTOS;
                        cursor = siguiente;
                        continue;
                    }
                    if (comidaPendiente) {
                        List<OpcionComidaDto> alcanzables = new ArrayList<>();
                        if (comidaCompleta) for (OpcionComidaDto restaurante :
                                restaurantesAlcanzables(restaurantes, anterior, rutas, cursor, comidaInicio,
                                        config.ritmo().margenMinutos())) {
                            if (estadoHorarioRestaurante(restaurante, comidaInicio, comidaFin)
                                    != EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE) alcanzables.add(restaurante);
                        }
                        if (!alcanzables.isEmpty()) {
                            OpcionComidaDto principal = alcanzables.getFirst();
                            DesplazamientoDto tramo = anterior == null ? null : rutas.get(new Par(anterior, principal.id()));
                            if (tramo != null && tramo.duracionSegundos() > 0) {
                                LocalDateTime llegada = cursor.plusMinutes((long) Math.ceil(tramo.duracionSegundos() / 60.0));
                                bloques.add(new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO, cursor, llegada, null,
                                        tramo.origenId(), tramo.destinoId(), tramo.distanciaMetros(), tramo.duracionSegundos(),
                                        tramo.fuente(), false, "Desplazamiento"));
                                cursor = llegada;
                            }
                            if (tramo != null && config.ritmo().margenMinutos() > 0) {
                                LocalDateTime finMargen = cursor.plusMinutes(config.ritmo().margenMinutos());
                                bloques.add(bloqueSimple(TipoBloqueItinerario.MARGEN, cursor, finMargen, "Margen"));
                                cursor = finMargen;
                            }
                            if (cursor.isBefore(comidaInicio)) {
                                bloques.add(bloqueSimple(TipoBloqueItinerario.TIEMPO_LIBRE, cursor, comidaInicio, "Tiempo libre"));
                                parcial |= Duration.between(cursor, comidaInicio).toMinutes() >= HUECO_PARCIAL_MINUTOS;
                            }
                            // Geoapify aporta un horario orientativo, nunca una apertura confirmada.
                            bloques.add(new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA, comidaInicio, comidaFin, null,
                                    null, null, null, null, null, true,
                                    "Restaurante sugerido sin reserva; horario pendiente de confirmar",
                                    principal, alcanzables.stream().skip(1).limit(3).toList()));
                            anterior = principal.id();
                            hayCondicionadas = true;
                        } else {
                            hayComidaGenerica = true;
                            if (!restaurantes.isEmpty()) advertencias.add("No hay restaurante alcanzable a tiempo; se propone una comida genérica.");
                            if (cursor.isBefore(comidaInicio)) {
                                bloques.add(bloqueSimple(TipoBloqueItinerario.TIEMPO_LIBRE, cursor, comidaInicio, "Tiempo libre"));
                                parcial |= Duration.between(cursor, comidaInicio).toMinutes() >= HUECO_PARCIAL_MINUTOS;
                            }
                            bloques.add(new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA,
                                    comidaInicio, comidaFin, null, null, null, null, null, null, true,
                                    comidaCompleta
                                            ? "Comida sin ubicación concreta; desvío no calculado"
                                            : "Franja parcial de comida sin ubicación concreta; desvío no calculado"));
                        }
                        cursor = comidaFin;
                        comidaPendiente = false;
                        actividad = 0;
                        continue;
                    }
                    break;
                }

                int mejorAfinidad = factibles.stream()
                        .mapToInt(o -> o.candidato().afinidad())
                        .max()
                        .orElseThrow();
                String lugarAnterior = anterior;
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
                    actividad += (int) elegido.minutosTraslado();
                }
                if (elegido.tramo() != null && config.ritmo().margenMinutos() > 0) {
                    LocalDateTime finMargen = cursor.plusMinutes(config.ritmo().margenMinutos());
                    bloques.add(bloqueSimple(TipoBloqueItinerario.MARGEN, cursor, finMargen, "Margen"));
                    cursor = finMargen;
                }

                CandidatoEvaluadoDto candidato = elegido.candidato();
                LocalDateTime finVisita = cursor.plusMinutes(candidato.duracionMinutos());
                boolean condicionada = candidato.estadoHorario() != ScheduleStatus.VERIFIED
                        || evaluadorHorario.evaluar(candidato, cursor, finVisita) == EstadoHorarioVisita.INCIERTO;
                bloques.add(new BloqueItinerarioDto(
                        TipoBloqueItinerario.VISITA, cursor, finVisita, candidato,
                        null, null, null, null, null, condicionada,
                        condicionada ? "Horario pendiente de confirmar" : "Visita"));
                hayCondicionadas |= condicionada;
                hayVisitas = true;
                visitasDia = true;
                cursor = finVisita;
                actividad += candidato.duracionMinutos();
                pendientes.remove(candidato);
                anterior = candidato.lugar().id();
                conflictosHorario.remove(anterior);
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
            parcial |= !visitasDia;
            dias.add(new DiaItinerarioDto(
                    ventana.fecha(), ventana.inicio(), ventana.fin(), List.copyOf(bloques)));
        }

        for (CandidatoEvaluadoDto candidato : pendientes) {
            if (conflictosHorario.contains(candidato.lugar().id()))
                advertencias.add("No se ha programado " + candidato.lugar().nombre() + " (" + candidato.lugar().id()
                        + "): conflicto con el horario facilitado por la fuente.");
        }
        if (!pendientes.isEmpty()) {
            advertencias.add("Quedan " + pendientes.size()
                    + " lugares sin programar por falta de tiempo, rutas o compatibilidad de horarios.");
            parcial = true;
        }
        if (hayCondicionadas) {
            advertencias.add("Los horarios no verificados requieren confirmacion.");
        }
        if (hayVisitas) {
            advertencias.add("No se han calculado trayectos desde o hacia el alojamiento.");
        }
        if (hayComidaGenerica) {
            advertencias.add("La comida no tiene una ubicacion concreta; el posible desvio para comer no esta incluido en los trayectos a pie.");
        }
        ResultadoValidacionPlan validacion = validadorPlan.validar(dias, config.preferencias());
        advertencias.addAll(validacion.incidencias());
        if (!validacion.valido()) throw new IllegalStateException("El itinerario generado no cumple sus invariantes: "
                + String.join("; ", validacion.incidencias()));
        return new ItinerarioGeneradoDto(
                List.copyOf(dias), List.copyOf(advertencias), parcial || validacion.parcial(),
                candidatos.size(), puntos.size());
    }

    private static BloqueItinerarioDto bloqueSimple(TipoBloqueItinerario tipo,
            LocalDateTime inicio, LocalDateTime fin, String etiqueta) {
        return new BloqueItinerarioDto(tipo, inicio, fin, null,
                null, null, null, null, null, false, etiqueta);
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

    private List<Opcion> opcionesFactibles(
            List<CandidatoEvaluadoDto> pendientes,
            String anterior,
            Map<Par, DesplazamientoDto> rutas,
            LocalDateTime cursor,
            LocalDateTime finDia,
            int margenMinutos, Set<String> conflictosHorario) {
        List<Opcion> opciones = new ArrayList<>();
        long segundosRestantes = Duration.between(cursor, finDia).getSeconds();
        for (CandidatoEvaluadoDto candidato : pendientes) {
            DesplazamientoDto tramo = anterior == null
                    ? null
                    : rutas.get(new Par(anterior, candidato.lugar().id()));
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
                            .plusMinutes(tramo == null ? 0 : margenMinutos)
                            .plusMinutes(candidato.duracionMinutos())
                            .isAfter(finDia)) {
                LocalDateTime inicioVisita = cursor.plusMinutes(minutosTraslado)
                        .plusMinutes(tramo == null ? 0 : margenMinutos);
                if (evaluadorHorario.evaluar(candidato, inicioVisita,
                        inicioVisita.plusMinutes(candidato.duracionMinutos())) != EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE) {
                    opciones.add(new Opcion(candidato, tramo, minutosTraslado));
                } else conflictosHorario.add(candidato.lugar().id());
            }
        }
        return opciones;
    }

    private LocalDateTime siguienteInicioConVisita(List<CandidatoEvaluadoDto> pendientes,
            String anterior, Map<Par, DesplazamientoDto> rutas, LocalDateTime cursor,
            LocalDateTime limite, int margenMinutos, Set<String> conflictosHorario) {
        if (pendientes.stream().noneMatch(c -> c.horario() != null
                && c.horarioFuente() != null && c.horarioFuente().startsWith("Geoapify"))) {
            return null;
        }
        for (LocalDateTime intento = cursor.plusMinutes(1); intento.isBefore(limite);
                intento = intento.plusMinutes(1)) {
            if (!opcionesFactibles(pendientes, anterior, rutas, intento,
                    limite, margenMinutos, conflictosHorario).isEmpty()) {
                return intento;
            }
        }
        return null;
    }

    private EstadoHorarioVisita estadoHorarioRestaurante(OpcionComidaDto restaurante,
            LocalDateTime inicio, LocalDateTime fin) {
        var lugar = new es.upv.pin.backend.dto.LugarDto(restaurante.id(), restaurante.nombre(), restaurante.direccion(),
                restaurante.latitud(), restaurante.longitud(), List.of(CategoriaTuristica.GASTRONOMIA), List.of(), restaurante.horario());
        var candidato = new CandidatoEvaluadoDto(lugar, 0, 1, null, null, null, null, List.of(),
                restaurante.horario(), restaurante.horarioFuente(), null, null, null, null, null, null);
        return evaluadorHorario.evaluar(candidato, inicio, fin);
    }

    private static LocalDateTime cursorFinOpcion(LocalDateTime cursor, Opcion opcion, int margen) {
        return cursor.plusMinutes(opcion.minutosTraslado()).plusMinutes(opcion.tramo() == null ? 0 : margen)
                .plusMinutes(opcion.candidato().duracionMinutos());
    }

    private static List<OpcionComidaDto> restaurantesAlcanzables(List<OpcionComidaDto> restaurantes,
            String anterior, Map<Par, DesplazamientoDto> rutas, LocalDateTime cursor,
            LocalDateTime limite, int margenMinutos) {
        return restaurantes.stream().filter(r -> {
            if (anterior == null) return !cursor.isAfter(limite);
            DesplazamientoDto tramo = rutas.get(new Par(anterior, r.id()));
            return rutaValida(tramo) && !cursor.plusMinutes((long) Math.ceil(tramo.duracionSegundos() / 60.0))
                    .plusMinutes(margenMinutos).isAfter(limite);
        }).sorted(Comparator.comparingDouble(r -> anterior == null ? 0
                : rutas.get(new Par(anterior, r.id())).duracionSegundos())).toList();
    }

    private static Comparator<Opcion> ordenOpciones(
            String anterior,
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
