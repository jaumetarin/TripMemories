package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Codigo;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Nivel;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.model.EstadoHorarioVisita;
import es.upv.pin.backend.model.ScheduleStatus;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ColocacionVisitaService {
    private final EvaluadorHorarioService horarios;

    public ColocacionVisitaService(EvaluadorHorarioService horarios) {
        this.horarios = horarios;
    }

    public Resultado colocar(
            Visita visita,
            String origenId,
            DesplazamientoDto ruta,
            LocalDateTime cursor,
            LocalDateTime limite,
            int minutosActividad,
            RitmoDiarioDto ritmo
    ) {
        var candidato = visita.candidato();

        if (candidato.lugar() == null || candidato.duracionMinutos() <= 0) {
            throw new IllegalArgumentException(
                    "La visita no contiene un lugar y una duración válidos.");
        }

        if (!rutaValida(ruta, origenId, candidato.lugar().id())) {
            return fallo(visita, cursor, minutosActividad,
                    Codigo.RUTA_NO_DISPONIBLE,
                    "No se puede confirmar el trayecto hasta «"
                            + candidato.lugar().nombre() + "».");
        }

        var bloques = new ArrayList<BloqueItinerarioDto>();
        var posicion = cursor;
        int actividad = minutosActividad;

        if (actividad >= ritmo.descansoCadaMinutos()) {
            var finDescanso = posicion.plusMinutes(
                    ritmo.descansoDuracionMinutos());
            bloques.add(simple(TipoBloqueItinerario.DESCANSO,
                    posicion, finDescanso, "Descanso"));
            posicion = finDescanso;
            actividad = 0;
        }

        double minutosRuta = Math.ceil(ruta.duracionSegundos() / 60.0);

        if (minutosRuta > Math.max(
                0, Duration.between(posicion, limite).toMinutes())) {
            return sinEspacio(visita, cursor, minutosActividad);
        }

        int traslado = (int) minutosRuta;

        if (traslado > 0) {
            var llegada = posicion.plusMinutes(traslado);
            bloques.add(new BloqueItinerarioDto(
                    TipoBloqueItinerario.TRASLADO,
                    posicion, llegada, null,
                    ruta.origenId(), ruta.destinoId(),
                    ruta.distanciaMetros(), ruta.duracionSegundos(),
                    ruta.fuente(), false, "Desplazamiento"));
            posicion = llegada;
        }

        if (ritmo.margenMinutos() > 0) {
            var finMargen = posicion.plusMinutes(ritmo.margenMinutos());
            bloques.add(simple(TipoBloqueItinerario.MARGEN,
                    posicion, finMargen, "Margen"));
            posicion = finMargen;
        }

        var finVisita = posicion.plusMinutes(candidato.duracionMinutos());

        if (finVisita.isAfter(limite)) {
            return sinEspacio(visita, cursor, minutosActividad);
        }

        var inicioInicial = posicion;
        var estado = horarios.evaluar(candidato, posicion, finVisita);

        // Busca una apertura posterior sin cambiar el orden de las visitas.
        while (estado == EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE
                && !finVisita.plusMinutes(1).isAfter(limite)) {
            posicion = posicion.plusMinutes(1);
            finVisita = finVisita.plusMinutes(1);
            estado = horarios.evaluar(candidato, posicion, finVisita);
        }

        var incidencias = new ArrayList<IncidenciaEdicionDto>();

        if (estado == EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE) {
            // Mostrar el intento inicial facilita entender el conflicto.
            posicion = inicioInicial;
            finVisita = posicion.plusMinutes(candidato.duracionMinutos());
            incidencias.add(incidencia(visita, posicion,
                    Nivel.CONFLICTO, Codigo.HORARIO_INCOMPATIBLE,
                    "«" + candidato.lugar().nombre()
                            + "» no encaja con su horario publicado "
                            + "dentro de esta franja."));
        } else if (posicion.isAfter(inicioInicial)) {
            bloques.add(simple(TipoBloqueItinerario.TIEMPO_LIBRE,
                    inicioInicial, posicion,
                    "Espera hasta el horario de la visita"));
        }

        boolean condicionada =
                estado != EstadoHorarioVisita.COMPATIBLE_SEGUN_FUENTE
                        || candidato.estadoHorario() != ScheduleStatus.VERIFIED;

        if (condicionada
                && estado != EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE) {
            incidencias.add(incidencia(visita, posicion,
                    Nivel.AVISO, Codigo.HORARIO_NO_CONFIRMADO,
                    "Confirma el horario de «"
                            + candidato.lugar().nombre() + "» antes de ir."));
        }

        bloques.add(new BloqueItinerarioDto(
                TipoBloqueItinerario.VISITA, posicion, finVisita,
                candidato, null, null, null, null, null,
                condicionada,
                condicionada ? "Horario pendiente de confirmar" : "Visita"));

        return new Resultado(List.copyOf(bloques),
                List.copyOf(incidencias), finVisita,
                actividad + traslado + candidato.duracionMinutos());
    }

    private static boolean rutaValida(
            DesplazamientoDto ruta, String origen, String destino) {
        return ruta != null
                && ruta.estado() == EstadoDesplazamiento.DISPONIBLE
                && origen != null && origen.equals(ruta.origenId())
                && destino != null && destino.equals(ruta.destinoId())
                && ruta.fuente() != null && !ruta.fuente().isBlank()
                && ruta.distanciaMetros() != null
                && Double.isFinite(ruta.distanciaMetros())
                && ruta.distanciaMetros() >= 0
                && ruta.duracionSegundos() != null
                && Double.isFinite(ruta.duracionSegundos())
                && ruta.duracionSegundos() >= 0;
    }

    private static Resultado sinEspacio(
            Visita visita, LocalDateTime cursor, int actividad) {
        return fallo(visita, cursor, actividad,
                Codigo.DISPONIBILIDAD_EXCEDIDA,
                "No hay tiempo para llegar y completar la visita a «"
                        + visita.candidato().lugar().nombre()
                        + "» dentro de esta franja.");
    }

    private static Resultado fallo(
            Visita visita, LocalDateTime cursor, int actividad,
            Codigo codigo, String mensaje) {
        return new Resultado(List.of(),
                List.of(incidencia(visita, cursor,
                        Nivel.CONFLICTO, codigo, mensaje)),
                cursor, actividad);
    }

    private static IncidenciaEdicionDto incidencia(
            Visita visita, LocalDateTime inicio,
            Nivel nivel, Codigo codigo, String mensaje) {
        return new IncidenciaEdicionDto(nivel, codigo,
                inicio.toLocalDate(), visita.actividadId(),
                visita.candidato().lugar().id(), mensaje);
    }

    private static BloqueItinerarioDto simple(
            TipoBloqueItinerario tipo,
            LocalDateTime inicio, LocalDateTime fin, String descripcion) {
        return new BloqueItinerarioDto(tipo, inicio, fin, null,
                null, null, null, null, null, false, descripcion);
    }

    public record Resultado(
            List<BloqueItinerarioDto> bloques,
            List<IncidenciaEdicionDto> incidencias,
            LocalDateTime fin,
            int minutosActividad
    ) {}
}
