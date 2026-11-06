package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.CandidatoEvaluadoDto;
import es.upv.pin.backend.model.EstadoHorarioVisita;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Interpreta únicamente reglas semanales OSM simples, sin excepciones ni horarios nocturnos. */
@Service
public class EvaluadorHorarioService {
    private static final Pattern REGLA = Pattern.compile("(Mo|Tu|We|Th|Fr|Sa|Su)(?:-(Mo|Tu|We|Th|Fr|Sa|Su))? (off|[0-2][0-9]:[0-5][0-9]-[0-2][0-9]:[0-5][0-9])");
    private static final List<String> DIAS = List.of("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su");

    public EstadoHorarioVisita evaluar(CandidatoEvaluadoDto candidato, LocalDateTime inicio, LocalDateTime fin) {
        if (candidato == null || candidato.lugar() == null || inicio == null || fin == null || !fin.isAfter(inicio)
                || !inicio.toLocalDate().equals(fin.toLocalDate()) || candidato.horario() == null
                || candidato.horarioFuente() == null || !candidato.horarioFuente().startsWith("Geoapify")
                || !candidato.horario().equals(candidato.lugar().horario())) return EstadoHorarioVisita.INCIERTO;
        String horario = candidato.horario().trim();
        if (horario.equals("24/7")) return EstadoHorarioVisita.COMPATIBLE_SEGUN_FUENTE;
        Map<Integer, String> reglas = new HashMap<>();
        for (String parte : horario.split(";", -1)) {
            Matcher m = REGLA.matcher(parte.trim());
            if (!m.matches()) return EstadoHorarioVisita.INCIERTO;
            int desde = DIAS.indexOf(m.group(1)), hasta = m.group(2) == null ? desde : DIAS.indexOf(m.group(2));
            if (hasta < desde) return EstadoHorarioVisita.INCIERTO;
            String tramo = m.group(3);
            if (!tramo.equals("off")) {
                try {
                    LocalTime apertura = LocalTime.parse(tramo.substring(0, 5));
                    LocalTime cierre = LocalTime.parse(tramo.substring(6));
                    if (!cierre.isAfter(apertura)) return EstadoHorarioVisita.INCIERTO;
                } catch (DateTimeException e) { return EstadoHorarioVisita.INCIERTO; }
            }
            for (int dia = desde; dia <= hasta; dia++) if (reglas.putIfAbsent(dia, tramo) != null) return EstadoHorarioVisita.INCIERTO;
        }
        String tramo = reglas.get(inicio.getDayOfWeek().getValue() - 1);
        if (tramo == null) return EstadoHorarioVisita.INCIERTO;
        if (tramo.equals("off")) return EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE;
        LocalTime apertura = LocalTime.parse(tramo.substring(0, 5)), cierre = LocalTime.parse(tramo.substring(6));
        return !inicio.toLocalTime().isBefore(apertura) && !fin.toLocalTime().isAfter(cierre)
                ? EstadoHorarioVisita.COMPATIBLE_SEGUN_FUENTE : EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE;
    }
}
