package es.upv.pin.backend.repository;

import es.upv.pin.backend.model.Usuario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {
    private final JdbcClient jdbc;
    public UsuarioRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Usuario> buscarPorCorreo(String correo) {
        return jdbc.sql("SELECT * FROM usuario WHERE lower(correo) = :correo").param("correo", correo)
                .query((rs, n) -> leer(rs)).optional();
    }
    public Optional<Usuario> bloquearPorCorreo(String correo) {
        return jdbc.sql("SELECT * FROM usuario WHERE lower(correo) = :correo FOR UPDATE").param("correo", correo)
                .query((rs, n) -> leer(rs)).optional();
    }
    public void crearSiNoExiste(UUID id, String nombre, String correo, String hash) {
        jdbc.sql("INSERT INTO usuario (id, nombre, correo, password_hash) VALUES (:id, :nombre, :correo, :hash) ON CONFLICT DO NOTHING")
                .param("id", id).param("nombre", nombre).param("correo", correo).param("hash", hash).update();
    }
    public void actualizarPendiente(UUID id, String nombre, String hash) {
        jdbc.sql("UPDATE usuario SET nombre = :nombre, password_hash = :hash WHERE id = :id AND correo_verificado = false")
                .param("id", id).param("nombre", nombre).param("hash", hash).update();
    }
    public void actualizarHash(String correo, String hash) {
        jdbc.sql("UPDATE usuario SET password_hash = :hash WHERE lower(correo) = :correo")
                .param("correo", correo).param("hash", hash).update();
    }
    public boolean puedeReenviar(UUID id, Instant ahora) {
        var ultimo = jdbc.sql("SELECT ultimo_envio_en FROM verificacion_correo WHERE usuario_id = :id").param("id", id)
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant()).optional();
        return ultimo.isEmpty() || !ahora.isBefore(ultimo.get().plusSeconds(60));
    }
    public void guardarVerificacion(UUID id, String hash, Instant ahora) {
        jdbc.sql("""
                INSERT INTO verificacion_correo (usuario_id, token_hash, expira_en, ultimo_envio_en)
                VALUES (:id, :hash, :expira, :ahora)
                ON CONFLICT (usuario_id) DO UPDATE SET token_hash = EXCLUDED.token_hash,
                    expira_en = EXCLUDED.expira_en, ultimo_envio_en = EXCLUDED.ultimo_envio_en
                """).param("id", id).param("hash", hash).param("expira", OffsetDateTime.ofInstant(ahora.plusSeconds(1800), java.time.ZoneOffset.UTC))
                .param("ahora", OffsetDateTime.ofInstant(ahora, java.time.ZoneOffset.UTC)).update();
    }
    public Optional<UUID> usuarioDelToken(String hash, Instant ahora) {
        return jdbc.sql("SELECT usuario_id FROM verificacion_correo WHERE token_hash = :hash AND expira_en > :ahora")
                .param("hash", hash).param("ahora", OffsetDateTime.ofInstant(ahora, java.time.ZoneOffset.UTC))
                .query(UUID.class).optional();
    }
    public Optional<Usuario> bloquearPorId(UUID id) {
        return jdbc.sql("SELECT * FROM usuario WHERE id = :id FOR UPDATE").param("id", id).query((rs, n) -> leer(rs)).optional();
    }
    public void verificar(UUID id) {
        jdbc.sql("UPDATE usuario SET correo_verificado = true WHERE id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM verificacion_correo WHERE usuario_id = :id").param("id", id).update();
    }
    private Usuario leer(ResultSet rs) throws SQLException {
        return new Usuario(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("correo"),
                rs.getString("password_hash"), rs.getBoolean("correo_verificado"));
    }
}
