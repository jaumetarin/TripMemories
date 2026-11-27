package es.upv.pin.backend.service;

import es.upv.pin.backend.exception.ControlGeneracionException;
import es.upv.pin.backend.model.Plan;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CuotaGeneracionService {
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;
    private final PlanService planes;
    private final Clock reloj;
    @org.springframework.beans.factory.annotation.Autowired
    public CuotaGeneracionService(JdbcTemplate jdbc, TransactionTemplate transacciones, PlanService planes) {
        this(jdbc, transacciones, planes, Clock.systemUTC());
    }
    CuotaGeneracionService(JdbcTemplate jdbc, TransactionTemplate transacciones, PlanService planes, Clock reloj) {
        this.jdbc = jdbc; this.transacciones = transacciones; this.planes = planes; this.reloj = reloj;
    }
    public Estado consultar(UUID usuario) { return operar(usuario, false); }
    public Estado reservar(UUID usuario) { return operar(usuario, true); }
    private Estado operar(UUID usuario, boolean reservar) {
        return transacciones.execute(status -> {
            // La fila de usuario también serializa la primera creación del contador.
            jdbc.queryForObject("SELECT id FROM usuario WHERE id = ? FOR UPDATE", UUID.class, usuario);
            Plan plan = planes.actual(usuario);
            Instant ahora = reloj.instant();
            LocalDate mes = ahora.atZone(ZONA).toLocalDate().withDayOfMonth(1);
            Instant reinicioMes = mes.plusMonths(1).atStartOfDay(ZONA).toInstant();
            if (!reservar && Boolean.FALSE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM consumo_generacion WHERE usuario_id=?)", Boolean.class, usuario))) {
                return new Estado(plan.generacionesHora(), plan.generacionesHora(), ahora.plusSeconds(3600),
                        plan.generacionesMes(), plan.generacionesMes(), reinicioMes);
            }
            jdbc.update("INSERT INTO consumo_generacion(usuario_id, mes) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    usuario, mes);
            var estado = jdbc.queryForObject("SELECT mes, usadas_mes FROM consumo_generacion WHERE usuario_id = ? FOR UPDATE",
                    (rs, n) -> new Datos(rs.getObject(1, LocalDate.class), rs.getInt(2)), usuario);
            int mensual = estado.mes.equals(mes) ? estado.mensual : 0;
            // La cuenta bloqueada serializa reservas y cambios de plan.
            jdbc.update("DELETE FROM generacion_reciente WHERE usuario_id=? AND instante<=?",
                    usuario, java.sql.Timestamp.from(ahora.minusSeconds(3600)));
            var recientes = jdbc.query("SELECT instante FROM generacion_reciente WHERE usuario_id=? ORDER BY instante",
                    (rs, n) -> rs.getTimestamp(1).toInstant(), usuario);
            int hora = recientes.size();
            Instant reinicio = hora == 0 ? ahora.plusSeconds(3600)
                    : recientes.get(Math.max(0, hora - plan.generacionesHora())).plusSeconds(3600);
            if (reservar) {
                if (mensual >= plan.generacionesMes()) throw limite("mensual", ahora, reinicioMes);
                if (hora >= plan.generacionesHora()) throw limite("por hora", ahora, reinicio);
                jdbc.update("INSERT INTO generacion_reciente(usuario_id, instante) VALUES (?, ?)",
                        usuario, java.sql.Timestamp.from(ahora));
                if (hora == 0) reinicio = ahora.plusSeconds(3600);
                mensual++; hora++;
            }
            jdbc.update("UPDATE consumo_generacion SET mes=?, usadas_mes=? WHERE usuario_id=?", mes, mensual, usuario);
            return new Estado(plan.generacionesHora(), Math.max(0, plan.generacionesHora()-hora), reinicio,
                    plan.generacionesMes(), Math.max(0, plan.generacionesMes()-mensual), reinicioMes);
        });
    }
    private static ControlGeneracionException limite(String periodo, Instant ahora, Instant reinicio) {
        return new ControlGeneracionException(HttpStatus.TOO_MANY_REQUESTS,
                "Has alcanzado el límite " + periodo + " de generaciones de tu cuenta.",
                Math.max(1, Duration.between(ahora, reinicio).toSeconds() + 1));
    }
    private record Datos(LocalDate mes, int mensual) {}
    public record Estado(int limite, int restantes, Instant reinicio, int limiteMensual, int restantesMensuales, Instant reinicioMensual) {}
}
