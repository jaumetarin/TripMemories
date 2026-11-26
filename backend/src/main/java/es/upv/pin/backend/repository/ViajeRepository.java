package es.upv.pin.backend.repository;

import es.upv.pin.backend.dto.BloqueItinerarioDto;
import es.upv.pin.backend.dto.ResumenViajeDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ViajeRepository {

    private final JdbcClient jdbc;

    public ViajeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ResumenViajeDto> listarPropios(UUID usuarioId) {
        return jdbc.sql("""
                SELECT id, titulo, destino,
                       fecha_inicio, fecha_fin,
                       actualizado_en, revision, eliminando_recuerdos
                FROM viaje
                WHERE usuario_id = :usuarioId AND eliminado_en IS NULL
                ORDER BY actualizado_en DESC, id
                """)
                .param("usuarioId", usuarioId)
                .query((rs, numeroFila) -> leerResumen(rs))
                .list();
    }

    public Optional<CabeceraGuardada> buscarPropio(
            UUID usuarioId,
            UUID viajeId
    ) {
        return jdbc.sql("""
                SELECT v.id, v.titulo, v.destino,
                       v.fecha_inicio, v.fecha_fin,
                       v.actualizado_en, v.revision, v.eliminando_recuerdos,
                       i.id AS itinerario_id,
                       i.formato,
                       i.configuracion,
                       i.datos_generacion
                FROM viaje v
                LEFT JOIN itinerario i
                    ON i.viaje_id = v.id
                    AND i.es_actual = true
                WHERE v.id = :viajeId
                    AND v.usuario_id = :usuarioId
                    AND v.eliminado_en IS NULL AND NOT v.eliminando_recuerdos
                """)
                .param("viajeId", viajeId)
                .param("usuarioId", usuarioId)
                .query((rs, numeroFila) -> new CabeceraGuardada(
                        leerResumen(rs),
                        rs.getObject("itinerario_id", UUID.class),
                        rs.getObject("formato", Integer.class),
                        rs.getString("configuracion"),
                        rs.getString("datos_generacion")
                ))
                .optional();
    }

    public List<ActividadGuardada> listarActividadesPropias(
            UUID usuarioId,
            UUID itinerarioId
    ) {
        return jdbc.sql("""
                SELECT a.id, a.dia, a.orden,
                       a.hora_aproximada,
                       a.fin, a.tipo, a.detalle
                FROM actividad a
                JOIN itinerario i
                    ON i.id = a.itinerario_id
                JOIN viaje v
                    ON v.id = i.viaje_id
                WHERE i.id = :itinerarioId
                    AND i.es_actual = true
                    AND v.usuario_id = :usuarioId
                    AND v.eliminado_en IS NULL AND NOT v.eliminando_recuerdos
                ORDER BY a.dia, a.orden
                """)
                .param("itinerarioId", itinerarioId)
                .param("usuarioId", usuarioId)
                .query((rs, numeroFila) -> new ActividadGuardada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("dia", LocalDate.class),
                        rs.getInt("orden"),
                        rs.getObject(
                                "hora_aproximada",
                                LocalTime.class
                        ),
                        rs.getObject("fin", LocalDateTime.class),
                        rs.getString("tipo"),
                        rs.getString("detalle")
                ))
                .list();
    }

    public UUID insertarViaje(
            UUID usuarioId,
            String titulo,
            String destino,
            LocalDate fechaInicio,
            LocalDate fechaFin
    ) {
        return jdbc.sql("""
                INSERT INTO viaje (
                    usuario_id,
                    titulo,
                    destino,
                    tipo,
                    fecha_inicio,
                    fecha_fin
                )
                VALUES (
                    :usuarioId,
                    :titulo,
                    :destino,
                    'planificado',
                    :fechaInicio,
                    :fechaFin
                )
                RETURNING id
                """)
                .param("usuarioId", usuarioId)
                .param("titulo", titulo)
                .param("destino", destino)
                .param("fechaInicio", fechaInicio)
                .param("fechaFin", fechaFin)
                .query(UUID.class)
                .single();
    }

    public UUID insertarItinerario(
            UUID viajeId,
            String configuracionJson,
            String datosGeneracionJson
    ) {
        return jdbc.sql("""
                INSERT INTO itinerario (
                    viaje_id,
                    version,
                    es_actual,
                    configuracion,
                    datos_generacion,
                    formato
                )
                VALUES (
                    :viajeId,
                    (SELECT COALESCE(MAX(version), 0) + 1
                     FROM itinerario WHERE viaje_id = :viajeId),
                    true,
                    CAST(:configuracion AS jsonb),
                    CAST(:datosGeneracion AS jsonb),
                    1
                )
                RETURNING id
                """)
                .param("viajeId", viajeId)
                .param("configuracion", configuracionJson)
                .param("datosGeneracion", datosGeneracionJson)
                .query(UUID.class)
                .single();
    }

    public void crearAlbumInicial(UUID viajeId) {
        jdbc.sql("""
                INSERT INTO album(viaje_id,titulo)
                SELECT id, left('Recuerdos de ' || COALESCE(destino,titulo),160)
                FROM viaje WHERE id=:id ON CONFLICT(viaje_id) DO NOTHING
                """).param("id",viajeId).update();
    }

    public UUID insertarActividad(
            UUID itinerarioId,
            LocalDate dia,
            int orden,
            BloqueItinerarioDto bloque,
            String detalleJson
    ) {
        var lugar = bloque.candidato() == null
                ? null
                : bloque.candidato().lugar();

        var restaurante = bloque.restaurante();

        String lugarId = lugar != null
                ? lugar.id()
                : restaurante != null ? restaurante.id() : null;

        String lugarNombre = lugar != null
                ? lugar.nombre()
                : restaurante != null ? restaurante.nombre() : null;

        long minutos = Duration.between(
                bloque.inicio(),
                bloque.fin()
        ).toMinutes();

        Integer duracionMinutos = minutos > 0
                ? Math.toIntExact(minutos)
                : null;

        return jdbc.sql("""
                INSERT INTO actividad (
                    itinerario_id,
                    dia,
                    orden,
                    hora_aproximada,
                    duracion_min,
                    tipo,
                    lugar_id,
                    lugar_nombre,
                    fin,
                    detalle
                )
                VALUES (
                    :itinerarioId,
                    :dia,
                    :orden,
                    :inicio,
                    :duracion,
                    :tipo,
                    :lugarId,
                    :lugarNombre,
                    :fin,
                    CAST(:detalle AS jsonb)
                )
                RETURNING id
                """)
                .param("itinerarioId", itinerarioId)
                .param("dia", dia)
                .param("orden", orden)
                .param("inicio", bloque.inicio().toLocalTime())
                .param("duracion", duracionMinutos, Types.INTEGER)
                .param("tipo", bloque.tipo().name())
                .param("lugarId", lugarId, Types.VARCHAR)
                .param("lugarNombre", lugarNombre, Types.VARCHAR)
                .param("fin", bloque.fin())
                .param("detalle", detalleJson)
                .query(UUID.class)
                .single();
    }

    public boolean actualizarViaje(UUID usuarioId, UUID viajeId, long revision,
            String titulo, String destino, LocalDate fechaInicio, LocalDate fechaFin) {
        return jdbc.sql("""
                UPDATE viaje
                SET titulo = :titulo, destino = :destino, tipo = 'planificado',
                    fecha_inicio = :fechaInicio, fecha_fin = :fechaFin,
                    actualizado_en = now(), revision = revision + 1
                WHERE id = :viajeId AND usuario_id = :usuarioId AND revision = :revision
                    AND eliminado_en IS NULL AND NOT eliminando_recuerdos
                """)
                .param("viajeId", viajeId).param("usuarioId", usuarioId).param("revision", revision)
                .param("titulo", titulo).param("destino", destino)
                .param("fechaInicio", fechaInicio).param("fechaFin", fechaFin)
                .update() == 1;
    }

    public void marcarItinerariosAnteriores(UUID usuarioId, UUID viajeId) {
        jdbc.sql("""
                UPDATE itinerario i SET es_actual = false
                FROM viaje v
                WHERE i.viaje_id = v.id AND v.id = :viajeId
                    AND v.usuario_id = :usuarioId AND i.es_actual = true
                """)
                .param("viajeId", viajeId).param("usuarioId", usuarioId).update();
    }
    /** Enriquecimiento derivado: no cambia las actividades ni incrementa su revisión. */
    public boolean guardarTrazados(UUID usuario, UUID viaje, long revision, String trazadosJson) {
        return jdbc.sql("""
                WITH propio AS (
                    SELECT id FROM viaje WHERE id = :viaje AND usuario_id = :usuario
                        AND eliminado_en IS NULL AND NOT eliminando_recuerdos
                        AND revision = :revision FOR UPDATE
                )
                UPDATE itinerario i SET datos_generacion = jsonb_set(i.datos_generacion, '{trazados}',
                    (CASE WHEN jsonb_typeof(i.datos_generacion->'trazados') = 'object'
                        THEN i.datos_generacion->'trazados' ELSE '{}'::jsonb END) || CAST(:trazados AS jsonb), true)
                FROM propio WHERE i.viaje_id = propio.id AND i.es_actual = true AND i.formato = 1
                """).param("viaje", viaje).param("usuario", usuario).param("revision", revision)
                .param("trazados", trazadosJson).update() == 1;
    }
    /** La eliminación tiene que poder reintentarse aunque los recuerdos estén bloqueados. */
    public Optional<EstadoEliminacion> bloquearParaEliminar(UUID usuario, UUID viaje) {
        return jdbc.sql("""
                SELECT revision, eliminado_en IS NOT NULL AS eliminado, eliminando_recuerdos
                FROM viaje WHERE id=:viaje AND usuario_id=:usuario FOR UPDATE
                """).param("viaje",viaje).param("usuario",usuario)
                .query((r,n) -> new EstadoEliminacion(r.getLong("revision"),r.getBoolean("eliminado"),r.getBoolean("eliminando_recuerdos"))).optional();
    }

    public void bloquearRecuerdos(UUID usuario, UUID viaje) {
        jdbc.sql("UPDATE viaje SET eliminando_recuerdos=true WHERE id=:viaje AND usuario_id=:usuario")
                .param("viaje",viaje).param("usuario",usuario).update();
    }

    public void marcarEliminado(UUID usuario, UUID viaje) {
        jdbc.sql("""
                UPDATE viaje SET eliminado_en=now(), eliminando_recuerdos=false,
                    actualizado_en=now(), revision=revision+1
                WHERE id=:viaje AND usuario_id=:usuario AND eliminado_en IS NULL
                """).param("viaje",viaje).param("usuario",usuario).update();
    }

    public record EstadoEliminacion(long revision, boolean eliminado, boolean eliminandoRecuerdos) {}

    private ResumenViajeDto leerResumen(
            ResultSet rs
    ) throws SQLException {
        return new ResumenViajeDto(
                rs.getObject("id", UUID.class),
                rs.getString("titulo"),
                rs.getString("destino"),
                rs.getObject("fecha_inicio", LocalDate.class),
                rs.getObject("fecha_fin", LocalDate.class),
                rs.getObject(
                        "actualizado_en",
                        OffsetDateTime.class
                ).toInstant(),
                rs.getLong("revision"), rs.getBoolean("eliminando_recuerdos")
        );
    }

    public record CabeceraGuardada(
            ResumenViajeDto resumen,
            UUID itinerarioId,
            Integer formato,
            String configuracionJson,
            String datosGeneracionJson
    ) {}

    public record ActividadGuardada(
            UUID id,
            LocalDate dia,
            int orden,
            LocalTime inicio,
            LocalDateTime fin,
            String tipo,
            String detalleJson
    ) {}
}
