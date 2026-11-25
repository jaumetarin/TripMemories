package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Codigo;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Nivel;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import es.upv.pin.backend.service.FranjasEdicionService.Tipo;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReconstruccionDiaService {
    private final FranjasEdicionService franjas;
    private final ColocacionVisitaService colocacion;

    public ReconstruccionDiaService(FranjasEdicionService franjas,
            ColocacionVisitaService colocacion) {
        this.franjas = franjas;
        this.colocacion = colocacion;
    }

    public Resultado reconstruir(DiaItinerarioDto original,
            List<Visita> visitas, RitmoDiarioDto ritmo,
            List<DesplazamientoDto> desplazamientos) {
        var ventanas = franjas.obtener(original, ritmo);
        Map<Par, DesplazamientoDto> rutas = new HashMap<>();
        for (var ruta : desplazamientos) {
            rutas.put(new Par(ruta.origenId(), ruta.destinoId()), ruta);
        }
        var bloques = new ArrayList<BloqueItinerarioDto>();
        var incidencias = new ArrayList<IncidenciaEdicionDto>();
        var comidaOriginal = original.bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA)
                .findFirst().orElse(null);
        var restaurante = comidaOriginal == null ? null : comidaOriginal.restaurante();
        String anterior = "referencia:inicio";
        int siguiente = 0;
        int actividad = 0;
        LocalDateTime cursor = original.inicioDisponible();
        boolean interrumpido = false;

        for (int indice = 0; indice < ventanas.size(); indice++) {
            var ventana = ventanas.get(indice);
            if (ventana.tipo() == Tipo.COMIDA) {
                // Si el día empieza durante la comida, no inventamos un traslado previo.
                if (restaurante != null && !restaurante.id().equals(anterior)) {
                    var coincidencia = buscar(rutas, anterior, restaurante.id());
                    if (!valida(coincidencia) || !"misma ubicación".equals(coincidencia.fuente())
                            || coincidencia.distanciaMetros() != 0 || coincidencia.duracionSegundos() != 0) {
                        incidencias.add(new IncidenciaEdicionDto(Nivel.CONFLICTO,
                                Codigo.DISPONIBILIDAD_EXCEDIDA, original.fecha(), null,
                                restaurante.id(), "No hay tiempo disponible antes de la comida "
                                        + "para llegar al restaurante guardado."));
                        interrumpido = true;
                        break;
                    }
                    anterior = restaurante.id();
                }
                OpcionComidaDto principal = restaurante == null ? null
                        : sinTrayectoAntiguo(restaurante);
                var alternativas = comidaOriginal == null ? List.<OpcionComidaDto>of()
                        : comidaOriginal.alternativasComida().stream()
                                .map(ReconstruccionDiaService::sinTrayectoAntiguo).toList();
                bloques.add(new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA,
                        ventana.inicio(), ventana.fin(), null,
                        null, null, null, null, null, principal != null,
                        principal == null ? "Pausa para comer" : "Restaurante guardado; confirma su horario",
                        principal, alternativas));
                cursor = ventana.fin();
                actividad = 0;
                continue;
            }

            boolean antesDeComida = indice + 1 < ventanas.size()
                    && ventanas.get(indice + 1).tipo() == Tipo.COMIDA;
            boolean hayFranjaPosterior = ventanas.subList(indice + 1, ventanas.size())
                    .stream().anyMatch(f -> f.tipo() == Tipo.VISITAS);

            while (siguiente < visitas.size()) {
                var visita = visitas.get(siguiente);
                var limite = ventana.fin();
                if (antesDeComida && restaurante != null) {
                    var hastaComida = buscar(rutas, visita.candidato().lugar().id(), restaurante.id());
                    if (!valida(hastaComida)) break;
                    double reserva = Math.ceil(hastaComida.duracionSegundos() / 60.0)
                            + ritmo.margenMinutos();
                    if (reserva > Duration.between(cursor, limite).toMinutes()) break;
                    limite = limite.minusMinutes((long) reserva);
                }
                var resultado = colocacion.colocar(visita, anterior,
                        buscar(rutas, anterior, visita.candidato().lugar().id()),
                        cursor, limite, actividad, ritmo);
                if (resultado.bloques().isEmpty()) {
                    if (!hayFranjaPosterior) incidencias.addAll(resultado.incidencias());
                    break;
                }
                boolean conflictoHorario = resultado.incidencias().stream()
                        .anyMatch(i -> i.codigo() == Codigo.HORARIO_INCOMPATIBLE);
                if (conflictoHorario && hayFranjaPosterior) break;

                bloques.addAll(resultado.bloques());
                incidencias.addAll(resultado.incidencias());
                cursor = resultado.fin();
                actividad = resultado.minutosActividad();
                anterior = visita.candidato().lugar().id();
                siguiente++;
            }

            if (antesDeComida && restaurante != null) {
                var ruta = buscar(rutas, anterior, restaurante.id());
                if (!valida(ruta)) {
                    incidencias.add(new IncidenciaEdicionDto(Nivel.CONFLICTO,
                            Codigo.RUTA_NO_DISPONIBLE, original.fecha(), null,
                            restaurante.id(), "No se puede confirmar la ruta hasta el restaurante guardado."));
                    interrumpido = true;
                    break;
                }
                double minutos = Math.ceil(ruta.duracionSegundos() / 60.0);
                long disponibles = Duration.between(cursor, ventana.fin()).toMinutes();
                if (minutos + ritmo.margenMinutos() > disponibles) {
                    incidencias.add(new IncidenciaEdicionDto(Nivel.CONFLICTO,
                            Codigo.DISPONIBILIDAD_EXCEDIDA, original.fecha(), null,
                            restaurante.id(), "No se llega al restaurante dentro de la franja de comida."));
                    interrumpido = true;
                    break;
                }
                if (minutos > 0) {
                    var llegada = cursor.plusMinutes((long) minutos);
                    bloques.add(new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO,
                            cursor, llegada, null, ruta.origenId(), ruta.destinoId(),
                            ruta.distanciaMetros(), ruta.duracionSegundos(), ruta.fuente(),
                            false, "Desplazamiento al restaurante"));
                    cursor = llegada;
                }
                if (ritmo.margenMinutos() > 0) {
                    var finMargen = cursor.plusMinutes(ritmo.margenMinutos());
                    bloques.add(simple(TipoBloqueItinerario.MARGEN, cursor, finMargen, "Margen"));
                    cursor = finMargen;
                }
                anterior = restaurante.id();
            }
            if (cursor.isBefore(ventana.fin())) {
                bloques.add(simple(TipoBloqueItinerario.TIEMPO_LIBRE,
                        cursor, ventana.fin(), "Tiempo libre"));
            }
            cursor = ventana.fin();
        }

        // Ninguna visita pendiente desaparece sin un conflicto que impida guardar.
        for (int i = siguiente; i < visitas.size(); i++) {
            var pendiente = visitas.get(i);
            boolean identificada = incidencias.stream()
                    .anyMatch(aviso -> pendiente.actividadId().equals(aviso.actividadId()));
            if (!identificada) {
                incidencias.add(new IncidenciaEdicionDto(Nivel.CONFLICTO,
                        Codigo.DISPONIBILIDAD_EXCEDIDA, original.fecha(), pendiente.actividadId(),
                        pendiente.candidato().lugar().id(), interrumpido
                                ? "No se pudo completar la reconstrucción de esta visita."
                                : "Esta visita no cabe en el día manteniendo el orden elegido."));
            }
        }
        return new Resultado(new DiaItinerarioDto(original.fecha(), original.inicioDisponible(),
                original.finDisponible(), List.copyOf(bloques)), List.copyOf(incidencias));
    }

    private static DesplazamientoDto buscar(Map<Par, DesplazamientoDto> rutas,
            String origen, String destino) {
        if (origen.equals(destino)) {
            return new DesplazamientoDto(origen, destino, 0.0, 0.0,
                    EstadoDesplazamiento.DISPONIBLE, "misma ubicación");
        }
        return rutas.get(new Par(origen, destino));
    }

    private static boolean valida(DesplazamientoDto ruta) {
        return ruta != null && ruta.estado() == EstadoDesplazamiento.DISPONIBLE
                && ruta.fuente() != null && !ruta.fuente().isBlank()
                && ruta.distanciaMetros() != null && Double.isFinite(ruta.distanciaMetros())
                && ruta.distanciaMetros() >= 0
                && ruta.duracionSegundos() != null && Double.isFinite(ruta.duracionSegundos())
                && ruta.duracionSegundos() >= 0;
    }

    private static OpcionComidaDto sinTrayectoAntiguo(OpcionComidaDto opcion) {
        return new OpcionComidaDto(opcion.id(), opcion.nombre(), opcion.latitud(), opcion.longitud(),
                opcion.direccion(), opcion.horario(), opcion.horarioFuente(), null, null);
    }

    private static BloqueItinerarioDto simple(TipoBloqueItinerario tipo,
            LocalDateTime inicio, LocalDateTime fin, String descripcion) {
        return new BloqueItinerarioDto(tipo, inicio, fin, null,
                null, null, null, null, null, false, descripcion);
    }

    private record Par(String origen, String destino) {}

    public record Resultado(DiaItinerarioDto dia, List<IncidenciaEdicionDto> incidencias) {}
}
