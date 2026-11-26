package es.upv.pin.backend.service;

import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LimiteProveedorService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public LimiteProveedorService(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }
    public long reservar(UUID usuario, String clase, int limite) {
        return tx.execute(status -> {
            Instant ahora = Instant.now();
            jdbc.update("INSERT INTO limite_proveedor(usuario_id, clase, inicio, usadas) VALUES (?, ?, ?, 0) ON CONFLICT DO NOTHING", usuario, clase, java.sql.Timestamp.from(ahora));
            var datos = jdbc.queryForObject("SELECT inicio, usadas FROM limite_proveedor WHERE usuario_id=? AND clase=? FOR UPDATE",
                (rs, n) -> new Datos(rs.getTimestamp(1).toInstant(), rs.getInt(2)), usuario, clase);
            Instant inicio = ahora.isBefore(datos.inicio.plusSeconds(3600)) ? datos.inicio : ahora;
            int usadas = inicio.equals(datos.inicio) ? datos.usadas : 0;
            if (usadas >= limite) return Math.max(1, Duration.between(ahora, inicio.plusSeconds(3600)).toSeconds()+1);
            jdbc.update("UPDATE limite_proveedor SET inicio=?, usadas=? WHERE usuario_id=? AND clase=?", java.sql.Timestamp.from(inicio), usadas+1, usuario, clase);
            return 0L;
        });
    }
    private record Datos(Instant inicio, int usadas) {}
}
