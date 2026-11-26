package es.upv.pin.backend.repository;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.FotoPreparada;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AlbumRepository {
    private final JdbcClient jdbc;
    public AlbumRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    private static final String ALBUMES = """
            SELECT a.id,a.viaje_id,a.titulo,v.titulo AS viaje_titulo,v.destino,v.fecha_inicio,v.fecha_fin,
                v.eliminado_en IS NOT NULL AS viaje_eliminado, a.eliminando,
                (SELECT count(*) FROM archivo_multimedia f WHERE f.album_id=a.id AND f.estado='DISPONIBLE' AND NOT a.eliminando) AS fotos,
                (SELECT f.id FROM archivo_multimedia f WHERE f.album_id=a.id AND f.estado='DISPONIBLE' AND NOT a.eliminando
                    ORDER BY f.subido_en DESC,f.id DESC LIMIT 1) AS portada
            FROM album a JOIN viaje v ON v.id=a.viaje_id WHERE v.usuario_id=:usuario AND NOT v.eliminando_recuerdos
            """;
    public List<AlbumDto> listar(UUID usuario) {
        return jdbc.sql(ALBUMES + " ORDER BY v.fecha_inicio DESC NULLS LAST,a.id")
                .param("usuario",usuario).query((r,n) -> album(r)).list();
    }
    public Optional<AlbumDto> propio(UUID usuario, UUID id) {
        return jdbc.sql(ALBUMES + " AND a.id=:id").param("usuario",usuario).param("id",id)
                .query((r,n) -> album(r)).optional();
    }
    public List<FotoDto> fotos(UUID usuario, UUID album) {
        return jdbc.sql("""
                SELECT f.* FROM archivo_multimedia f JOIN album a ON a.id=f.album_id JOIN viaje v ON v.id=a.viaje_id
                WHERE v.usuario_id=:usuario AND NOT v.eliminando_recuerdos AND NOT a.eliminando AND a.id=:album AND f.tipo='foto' AND f.estado<>'LEGADO'
                ORDER BY f.subido_en DESC,f.id DESC
                """).param("usuario",usuario).param("album",album).query((r,n) -> foto(r)).list();
    }
    public Optional<FotoPrivada> fotoPropia(UUID usuario, UUID id) {
        return jdbc.sql("""
                SELECT f.* FROM archivo_multimedia f JOIN album a ON a.id=f.album_id JOIN viaje v ON v.id=a.viaje_id
                WHERE v.usuario_id=:usuario AND NOT v.eliminando_recuerdos AND NOT a.eliminando AND f.id=:id AND f.tipo='foto' AND f.estado<>'LEGADO'
                """).param("usuario",usuario).param("id",id)
                .query((r,n) -> new FotoPrivada(foto(r),r.getObject("album_id",UUID.class),r.getString("ruta_privada"),r.getString("ruta_miniatura"))).optional();
    }
    public void bloquearCuenta(UUID usuario) {
        jdbc.sql("SELECT id FROM usuario WHERE id=:id FOR UPDATE").param("id",usuario).query(UUID.class).single();
    }
    public long bytesUsados(UUID usuario) {
        return jdbc.sql("""
                SELECT COALESCE(sum(f.bytes_original+f.bytes_miniatura),0) FROM archivo_multimedia f
                JOIN album a ON a.id=f.album_id JOIN viaje v ON v.id=a.viaje_id
                WHERE v.usuario_id=:id AND f.estado<>'LEGADO'
                """).param("id",usuario).query(Long.class).single();
    }
    public long numeroFotos(UUID album) {
        return jdbc.sql("SELECT count(*) FROM archivo_multimedia WHERE album_id=:id AND estado<>'LEGADO'")
                .param("id",album).query(Long.class).single();
    }
    public void reservar(UUID id, UUID album, FotoPreparada foto) {
        jdbc.sql("""
                INSERT INTO archivo_multimedia(id,album_id,tipo,ruta_privada,ruta_miniatura,nombre_original,
                    mime_type,bytes_original,bytes_miniatura,ancho,alto,estado)
                VALUES(:id,:album,'foto',:original,:miniatura,:nombre,:mime,:bytes,:bytesMini,:ancho,:alto,'PENDIENTE')
                """).param("id",id).param("album",album).param("original",id+"/original").param("miniatura",id+"/miniatura")
                .param("nombre",foto.nombre()).param("mime",foto.mime()).param("bytes",foto.original().length)
                .param("bytesMini",foto.miniatura().length).param("ancho",foto.ancho()).param("alto",foto.alto()).update();
    }
    public boolean estado(UUID usuario, UUID id, String nuevo, String anterior) {
        return jdbc.sql("""
                UPDATE archivo_multimedia f SET estado=:nuevo FROM album a,viaje v
                WHERE f.album_id=a.id AND a.viaje_id=v.id AND v.usuario_id=:usuario
                    AND f.id=:id AND f.estado=:anterior
                """).param("usuario",usuario).param("id",id).param("nuevo",nuevo).param("anterior",anterior).update()==1;
    }
    public boolean eliminar(UUID usuario, UUID id, String estado) {
        return jdbc.sql("""
                DELETE FROM archivo_multimedia f USING album a,viaje v
                WHERE f.album_id=a.id AND a.viaje_id=v.id AND v.usuario_id=:usuario AND f.id=:id AND f.estado=:estado
                """).param("usuario",usuario).param("id",id).param("estado",estado).update()==1;
    }
    private static AlbumDto album(ResultSet r) throws SQLException {
        return new AlbumDto(r.getObject("id",UUID.class),r.getObject("viaje_id",UUID.class),r.getString("titulo"),
                r.getString("viaje_titulo"),r.getString("destino"),r.getObject("fecha_inicio",LocalDate.class),
                r.getObject("fecha_fin",LocalDate.class),r.getLong("fotos"),r.getObject("portada",UUID.class),r.getBoolean("viaje_eliminado"),r.getBoolean("eliminando"));
    }
    private static FotoDto foto(ResultSet r) throws SQLException {
        return new FotoDto(r.getObject("id",UUID.class),r.getString("nombre_original"),r.getString("mime_type"),
                r.getLong("bytes_original"),r.getLong("bytes_miniatura"),r.getObject("ancho",Integer.class),
                r.getObject("alto",Integer.class),r.getString("estado"),r.getObject("subido_en",OffsetDateTime.class).toInstant());
    }
    public List<ArchivoDelViaje> archivosDelViaje(UUID usuario, UUID viaje) {
        return jdbc.sql("""
                SELECT f.id,f.ruta_privada,f.ruta_miniatura FROM archivo_multimedia f
                JOIN album a ON a.id=f.album_id JOIN viaje v ON v.id=a.viaje_id
                WHERE v.usuario_id=:usuario AND v.id=:viaje
                """).param("usuario",usuario).param("viaje",viaje)
                .query((r,n) -> new ArchivoDelViaje(r.getObject("id",UUID.class),r.getString("ruta_privada"),r.getString("ruta_miniatura"))).list();
    }

    public boolean haySubidasEnCurso(UUID usuario, UUID viaje) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM archivo_multimedia f JOIN album a ON a.id=f.album_id
                JOIN viaje v ON v.id=a.viaje_id WHERE v.usuario_id=:usuario AND v.id=:viaje
                    AND f.estado='PENDIENTE' AND f.subido_en>now()-interval '10 minutes')
                """).param("usuario",usuario).param("viaje",viaje).query(Boolean.class).single();
    }

    public void retirarAlbumVacio(UUID usuario, UUID viaje) {
        jdbc.sql("""
                DELETE FROM album a USING viaje v WHERE a.viaje_id=v.id AND v.id=:viaje AND v.usuario_id=:usuario
                    AND NOT EXISTS(SELECT 1 FROM archivo_multimedia f WHERE f.album_id=a.id)
                """).param("usuario",usuario).param("viaje",viaje).update();
    }

    public void retirarReferenciasDelViaje(UUID usuario, UUID viaje) {
        jdbc.sql("""
                DELETE FROM archivo_multimedia f USING album a,viaje v
                WHERE f.album_id=a.id AND a.viaje_id=v.id AND v.id=:viaje AND v.usuario_id=:usuario
                """).param("usuario",usuario).param("viaje",viaje).update();
        retirarAlbumVacio(usuario,viaje);
    }

    public void marcarAlbumEliminando(UUID usuario, UUID album) {
        jdbc.sql("""
                UPDATE album a SET eliminando=true FROM viaje v
                WHERE a.viaje_id=v.id AND a.id=:album AND v.usuario_id=:usuario
                """).param("usuario",usuario).param("album",album).update();
    }

    public void retirarReferenciasDelAlbum(UUID usuario, UUID album) {
        jdbc.sql("""
                DELETE FROM archivo_multimedia f USING album a,viaje v
                WHERE f.album_id=a.id AND a.viaje_id=v.id AND a.id=:album AND v.usuario_id=:usuario
                """).param("usuario",usuario).param("album",album).update();
        jdbc.sql("DELETE FROM album a USING viaje v WHERE a.viaje_id=v.id AND a.id=:album AND v.usuario_id=:usuario")
                .param("usuario",usuario).param("album",album).update();
    }

    public record ArchivoDelViaje(UUID id, String original, String miniatura) {}
    public record FotoPrivada(FotoDto datos, UUID albumId, String original, String miniatura) {}
}
