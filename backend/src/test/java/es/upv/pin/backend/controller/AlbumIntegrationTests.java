package es.upv.pin.backend.controller;

import es.upv.pin.backend.PostgresTest;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.ContenidoAlbumDto.LimitesDto;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.repository.AlbumRepository;
import es.upv.pin.backend.service.*;
import java.awt.Color;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AlbumIntegrationTests extends PostgresTest {
    private static final Path CARPETA = carpetaTemporal();
    @DynamicPropertySource static void fotos(DynamicPropertyRegistry propiedades) {
        propiedades.add("fotos.directorio", CARPETA::toString);
    }
    private static Path carpetaTemporal() {
        try { return Files.createTempDirectory("rute-album-test-"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired ViajeService viajes;
    @Autowired AlbumRepository albumes;
    @Autowired javax.sql.DataSource baseDeDatos;
    @MockitoSpyBean AlmacenamientoFotosLocal archivos;
    @MockitoSpyBean ValidadorFotos validador;
    private UsuarioAutenticado propietario, ajeno;
    private UUID album, viaje;
    private byte[] imagen;

    @BeforeEach void preparar() throws Exception {
        propietario = usuario(); ajeno = usuario();
        viaje = viaje(propietario.getId());
        album = albumes.listar(propietario.getId()).getFirst().id();
        imagen = ValidadorFotosTests.imagen(Color.RED);
    }
    private UsuarioAutenticado usuario() {
        UUID id = UUID.randomUUID();
        String correo = id + "@example.test";
        jdbc.sql("INSERT INTO usuario(id,nombre,correo,password_hash,correo_verificado) VALUES(:id,'Prueba',:correo,'hash-de-prueba',true)")
                .param("id",id).param("correo",correo).update();
        return new UsuarioAutenticado(new Usuario(id,"Prueba",correo,"hash-de-prueba",true));
    }
    private UUID viaje(UUID usuario) {
        var inicio = LocalDate.now().plusDays(30).atTime(10,0);
        var config = new ConfiguracionItinerarioDto(new DestinoDto("valencia","Valencia","Valencia","España",39.47,-0.37),
                new FechaHoraDto(inicio,inicio.plusHours(8),1),"",
                new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS),List.of(),List.of(),NivelGasto.MODERADO,3.0),
                new RitmoDiarioDto(LocalTime.of(13,0),LocalTime.of(15,0),120,15,10));
        var plan = new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(inicio.toLocalDate(),inicio,inicio.plusHours(8),List.of())),
                List.of(),false,0,0,List.of(),false);
        return viajes.guardarNuevo(usuario,new GuardarViajeDto("Vacaciones en Valencia"),config,plan);
    }
    private ResultActions subir(UUID destino, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/albumes/"+destino+"/fotos")
                .file(new MockMultipartFile("foto","foto.png","image/png",bytes)).with(user(propietario)).with(csrf()));
    }
    private FotoDto subir() throws Exception {
        return json.readValue(subir(album,imagen).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray(),FotoDto.class);
    }

    private ResultActions eliminarViaje(boolean recuerdos) throws Exception {
        return mvc.perform(delete("/api/viajes/"+viaje).with(user(propietario)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(new EliminarViajeDto(1L,recuerdos))));
    }

    @Test void eliminarViajeConservaFotosAlbumYDestinoSinPermitirReabrirElItinerario() throws Exception {
        var foto = subir();
        long cuota = albumes.bytesUsados(propietario.getId());
        eliminarViaje(false).andExpect(status().isNoContent());
        mvc.perform(get("/api/viajes").with(user(propietario))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/viajes/"+viaje).with(user(propietario))).andExpect(status().isNotFound());
        mvc.perform(put("/api/viajes/"+viaje).with(user(propietario)).with(csrf()).contentType("application/json")
                .content("{\"titulo\":\"No resucitar\",\"revision\":1}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/albumes/"+album).with(user(propietario))).andExpect(status().isOk())
                .andExpect(jsonPath("$.album.viajeEliminado").value(true))
                .andExpect(jsonPath("$.album.viajeTitulo").value("Vacaciones en Valencia"))
                .andExpect(jsonPath("$.album.destino").value("Valencia"));
        mvc.perform(get("/api/fotos/"+foto.id()+"/contenido").with(user(propietario)))
                .andExpect(status().isOk()).andExpect(content().bytes(imagen));
        assertEquals(cuota,albumes.bytesUsados(propietario.getId()));
        eliminarViaje(false).andExpect(status().isNoContent());
        assertTrue(albumes.fotoPropia(propietario.getId(),foto.id()).isPresent());
        // El álbum conservado permite añadir nuevos recuerdos al viaje ya retirado.
        subir(album,imagen).andExpect(status().isCreated());
    }

    @Test
    void rechazaBorrarRecuerdosDesdeUnaVistaAntiguaDelViaje() throws Exception {
        var foto = subir();
        long cuotaAnterior = albumes.bytesUsados(propietario.getId());

        eliminarViaje(false).andExpect(status().isNoContent());

        clearInvocations(archivos);

        eliminarViaje(true).andExpect(status().isConflict());

        assertTrue(albumes.propio(propietario.getId(), album).isPresent());
        assertTrue(albumes.fotoPropia(propietario.getId(), foto.id()).isPresent());
        assertEquals(cuotaAnterior, albumes.bytesUsados(propietario.getId()));

        verify(archivos, never()).borrar(anyString());

        mvc.perform(get("/api/fotos/" + foto.id() + "/contenido")
                .with(user(propietario)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(imagen));

        eliminarViaje(false).andExpect(status().isNoContent());
    }

    @Test void eliminaAutomaticamenteElAlbumVacio() throws Exception {
        eliminarViaje(false).andExpect(status().isNoContent());
        assertTrue(albumes.listar(propietario.getId()).isEmpty());
        eliminarViaje(false).andExpect(status().isNoContent());
    }

    @Test void eliminarTambienRecuerdosBorraArchivosYCuotaSinAfectarOtroViaje() throws Exception {
        var foto = subir();
        UUID otroViaje = viaje(propietario.getId());
        UUID otroAlbum = albumes.listar(propietario.getId()).stream().filter(a -> a.viajeId().equals(otroViaje)).findFirst().orElseThrow().id();
        subir(otroAlbum,imagen).andExpect(status().isCreated());
        eliminarViaje(true).andExpect(status().isNoContent());
        assertFalse(Files.exists(CARPETA.resolve(foto.id().toString())));
        assertEquals(foto.bytesOriginal()+foto.bytesMiniatura(),albumes.bytesUsados(propietario.getId()));
        assertTrue(albumes.propio(propietario.getId(),album).isEmpty());
        assertTrue(albumes.propio(propietario.getId(),otroAlbum).isPresent());
        for (String ruta : List.of("contenido","miniatura","descargar"))
            mvc.perform(get("/api/fotos/"+foto.id()+"/"+ruta).with(user(propietario))).andExpect(status().isNotFound());
        eliminarViaje(true).andExpect(status().isNoContent());
    }

    @Test void unFalloAlBorrarRecuerdosBloqueaArchivosYPermiteCompletarLaEliminacion() throws Exception {
        var foto = subir();
        doThrow(new IllegalStateException("fallo preparado")).when(archivos).borrar(endsWith("/miniatura"));
        eliminarViaje(true).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/viajes").with(user(propietario))).andExpect(jsonPath("$[0].eliminandoRecuerdos").value(true));
        mvc.perform(get("/api/albumes/"+album).with(user(propietario))).andExpect(status().isNotFound());
        mvc.perform(get("/api/fotos/"+foto.id()+"/contenido").with(user(propietario))).andExpect(status().isNotFound());
        assertTrue(albumes.bytesUsados(propietario.getId())>0);
        eliminarViaje(false).andExpect(status().isConflict());
        doCallRealMethod().when(archivos).borrar(anyString());
        eliminarViaje(true).andExpect(status().isNoContent());
        assertEquals(0,albumes.bytesUsados(propietario.getId()));
        assertFalse(Files.exists(CARPETA.resolve(foto.id().toString())));
    }

    @Test void eliminarViajeCompruebaSesionPropiedadCsrfRevisionYEleccionExplicita() throws Exception {
        String cuerpo = "{\"revision\":1,\"eliminarRecuerdos\":true}";
        mvc.perform(delete("/api/viajes/"+viaje).with(csrf()).contentType("application/json").content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/viajes/"+viaje).with(user(propietario)).contentType("application/json").content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/viajes/"+viaje).with(user(ajeno)).with(csrf()).contentType("application/json").content(cuerpo))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/viajes/"+viaje).with(user(propietario)).with(csrf()).contentType("application/json")
                .content("{\"revision\":2,\"eliminarRecuerdos\":true}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/viajes/"+viaje).with(user(propietario)).with(csrf()).contentType("application/json")
                .content("{\"revision\":1}")).andExpect(status().isBadRequest());
        assertFalse(albumes.propio(propietario.getId(),album).orElseThrow().viajeEliminado());
    }

    @Test void noBorraLosArchivosDeUnaSubidaActiva() throws Exception {
        var preparada = validador.preparar(new MockMultipartFile("foto","foto.png","image/png",imagen));
        albumes.reservar(UUID.randomUUID(),album,preparada);
        eliminarViaje(true).andExpect(status().isConflict());
        assertTrue(albumes.propio(propietario.getId(),album).isPresent());
        assertFalse(albumes.propio(propietario.getId(),album).orElseThrow().viajeEliminado());
    }

    @Test void eliminarUnAlbumRetiraTodasSusFotosPeroConservaElViajeYOtrosAlbumes() throws Exception {
        var foto = subir();
        var segunda = subir();
        UUID otroViaje = viaje(propietario.getId());
        UUID otroAlbum = albumes.listar(propietario.getId()).stream().filter(a -> a.viajeId().equals(otroViaje)).findFirst().orElseThrow().id();
        subir(otroAlbum,imagen).andExpect(status().isCreated());
        mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf())).andExpect(status().isNoContent());
        assertTrue(albumes.propio(propietario.getId(),album).isEmpty());
        assertTrue(albumes.propio(propietario.getId(),otroAlbum).isPresent());
        assertEquals(foto.bytesOriginal()+foto.bytesMiniatura(),albumes.bytesUsados(propietario.getId()));
        for (var eliminada : List.of(foto,segunda)) {
            assertFalse(Files.exists(CARPETA.resolve(eliminada.id().toString())));
            for (String ruta : List.of("contenido","miniatura","descargar"))
                mvc.perform(get("/api/fotos/"+eliminada.id()+"/"+ruta).with(user(propietario))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/viajes/"+viaje).with(user(propietario))).andExpect(status().isOk());
        mvc.perform(put("/api/viajes/"+viaje).with(user(propietario)).with(csrf()).contentType("application/json")
                .content("{\"titulo\":\"Viaje sin álbum\",\"revision\":1}")).andExpect(status().isOk());
        assertTrue(albumes.propio(propietario.getId(),album).isEmpty());
    }

    @Test void borrarElAlbumVacioODeUnViajeRetiradoTambienEsPosible() throws Exception {
        mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/viajes/"+viaje).with(user(propietario))).andExpect(status().isOk());
        UUID nuevoViaje = viaje(propietario.getId());
        UUID nuevoAlbum = albumes.listar(propietario.getId()).getFirst().id();
        subir(nuevoAlbum,imagen).andExpect(status().isCreated());
        mvc.perform(delete("/api/viajes/"+nuevoViaje).with(user(propietario)).with(csrf()).contentType("application/json")
                .content("{\"revision\":1,\"eliminarRecuerdos\":false}")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/albumes/"+nuevoAlbum).with(user(propietario)).with(csrf())).andExpect(status().isNoContent());
        assertTrue(albumes.listar(propietario.getId()).isEmpty());
    }

    @Test void borrarAlbumExigeSesionPropiedadYCsrfAntesDeTocarElDisco() throws Exception {
        var foto = subir();
        clearInvocations(archivos);
        mvc.perform(delete("/api/albumes/"+album).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/albumes/"+album).with(user(propietario))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/albumes/"+album).with(user(ajeno)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/albumes/"+UUID.randomUUID()).with(user(propietario)).with(csrf())).andExpect(status().isNotFound());
        verify(archivos,never()).borrar(anyString());
        assertTrue(albumes.fotoPropia(propietario.getId(),foto.id()).isPresent());
    }

    @Test void borrarAlbumConSubidaActivaSeRechazaSinBloquearlo() throws Exception {
        albumes.reservar(UUID.randomUUID(),album,validador.preparar(new MockMultipartFile("foto","foto.png","image/png",imagen)));
        mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf())).andExpect(status().isConflict());
        assertFalse(albumes.propio(propietario.getId(),album).orElseThrow().eliminando());
    }

    @Test void unBorradoParcialDelAlbumNoMuestraFotosDisponiblesYSePuedeReintentar() throws Exception {
        var foto = subir();
        doThrow(new IllegalStateException("fallo preparado")).when(archivos).borrar(endsWith("/miniatura"));
        mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf())).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/albumes/"+album).with(user(propietario))).andExpect(status().isOk())
                .andExpect(jsonPath("$.album.eliminando").value(true)).andExpect(jsonPath("$.fotos.length()").value(0))
                .andExpect(jsonPath("$.album.portadaId").isEmpty());
        assertTrue(albumes.bytesUsados(propietario.getId())>0);
        subir(album,imagen).andExpect(status().isConflict());
        for (String ruta : List.of("contenido","miniatura","descargar"))
            mvc.perform(get("/api/fotos/"+foto.id()+"/"+ruta).with(user(propietario))).andExpect(status().isNotFound());
        doCallRealMethod().when(archivos).borrar(anyString());
        mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0,albumes.bytesUsados(propietario.getId()));
        mvc.perform(get("/api/viajes/"+viaje).with(user(propietario))).andExpect(status().isOk());
    }

    @Test void unFalloDeConfirmacionEnLaBaseNoAnunciaExitoNiReabreArchivosBorrados() throws Exception {
        var foto = subir();
        String trigger = "fallo_album_"+UUID.randomUUID().toString().replace("-", "");
        jdbc.sql("CREATE FUNCTION "+trigger+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.id='"+album+"' THEN "
                +"RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='fallo privado'; END IF; RETURN OLD; END $$").update();
        jdbc.sql("CREATE TRIGGER "+trigger+" BEFORE DELETE ON album FOR EACH ROW EXECUTE FUNCTION "+trigger+"()").update();
        try {
            String respuesta = mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf()))
                    .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
            assertFalse(respuesta.contains("fallo privado"));
            assertTrue(albumes.propio(propietario.getId(),album).orElseThrow().eliminando());
            assertFalse(Files.exists(CARPETA.resolve(foto.id().toString())));
            assertTrue(albumes.bytesUsados(propietario.getId())>0);
            mvc.perform(get("/api/fotos/"+foto.id()+"/contenido").with(user(propietario))).andExpect(status().isNotFound());
        } finally {
            jdbc.sql("DROP TRIGGER "+trigger+" ON album").update();
            jdbc.sql("DROP FUNCTION "+trigger+"()").update();
        }
        mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0,albumes.bytesUsados(propietario.getId()));
    }

    @Test void creaUnAlbumPorViajeYReflejaSuTituloActual() throws Exception {
        assertEquals(1,albumes.listar(propietario.getId()).size());
        jdbc.sql("UPDATE viaje SET titulo='Mi viaje actualizado' WHERE id=:id").param("id",viaje).update();
        mvc.perform(get("/api/albumes").with(user(propietario))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viajeTitulo").value("Mi viaje actualizado"))
                .andExpect(jsonPath("$[0].destino").value("Valencia"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.sql("INSERT INTO album(viaje_id,titulo) VALUES(:id,'Duplicado')").param("id",viaje).update());
    }

    @Test void laMigracionCreaAlbumesParaViajesAntiguosSinDuplicarLosExistentes() {
        // Esquema aislado dentro de la base efímera, nunca la de desarrollo.
        String esquema = "album_migracion_" + UUID.randomUUID().toString().replace("-", "");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(baseDeDatos).schemas(esquema).defaultSchema(esquema).target("5").load().migrate();
            UUID usuario = jdbc.sql("INSERT INTO " + esquema + ".usuario(nombre,correo,password_hash) VALUES('Prueba','migracion@example.test','hash') RETURNING id")
                    .query(UUID.class).single();
            UUID antiguo = jdbc.sql("INSERT INTO " + esquema + ".viaje(usuario_id,titulo,destino,tipo) VALUES(:id,'Viaje antiguo','Roma','externo') RETURNING id")
                    .param("id",usuario).query(UUID.class).single();
            UUID existente = jdbc.sql("INSERT INTO " + esquema + ".viaje(usuario_id,titulo,destino,tipo) VALUES(:id,'Segundo viaje','Madrid','externo') RETURNING id")
                    .param("id",usuario).query(UUID.class).single();
            jdbc.sql("INSERT INTO " + esquema + ".album(viaje_id,titulo) VALUES(:id,'Álbum anterior')").param("id",existente).update();
            org.flywaydb.core.Flyway.configure().dataSource(baseDeDatos).schemas(esquema).defaultSchema(esquema).load().migrate();
            assertEquals(2,jdbc.sql("SELECT count(*) FROM " + esquema + ".album").query(Integer.class).single());
            assertEquals("Recuerdos de Roma",jdbc.sql("SELECT titulo FROM " + esquema + ".album WHERE viaje_id=:id").param("id",antiguo).query(String.class).single());
            assertEquals("Álbum anterior",jdbc.sql("SELECT titulo FROM " + esquema + ".album WHERE viaje_id=:id").param("id",existente).query(String.class).single());
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS " + esquema + " CASCADE").update();
        }
    }

    @Test void guardaReabreYDescargaElOriginalCorrespondienteACadaMiniatura() throws Exception {
        var roja = subir();
        byte[] azul = ValidadorFotosTests.imagen(Color.BLUE);
        var otra = json.readValue(subir(album,azul).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray(),FotoDto.class);
        assertNotEquals(roja.id(),otra.id());
        mvc.perform(get("/api/albumes/"+album).with(user(propietario))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos.length()").value(2)).andExpect(jsonPath("$.album.fotos").value(2));
        for (var foto : List.of(roja,otra)) {
            byte[] original = foto.id().equals(roja.id()) ? imagen : azul;
            mvc.perform(get("/api/fotos/"+foto.id()+"/contenido").with(user(propietario))).andExpect(status().isOk())
                    .andExpect(content().bytes(original)).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
            mvc.perform(get("/api/fotos/"+foto.id()+"/descargar").with(user(propietario))).andExpect(status().isOk())
                    .andExpect(content().bytes(original)).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
            byte[] mini = mvc.perform(get("/api/fotos/"+foto.id()+"/miniatura").with(user(propietario))).andExpect(status().isOk())
                    .andExpect(content().contentType("image/jpeg")).andReturn().getResponse().getContentAsByteArray();
            var color = new Color(ImageIO.read(new java.io.ByteArrayInputStream(mini)).getRGB(20,20));
            assertTrue(foto.id().equals(roja.id()) ? color.getRed()>240 : color.getBlue()>240);
        }
        var privada = albumes.fotoPropia(propietario.getId(),roja.id()).orElseThrow();
        assertTrue(jdbc.sql("SELECT actividad_id IS NULL AND lugar_id_libre IS NULL AND lugar_nombre_libre IS NULL FROM archivo_multimedia WHERE id=:id")
                .param("id",roja.id()).query(Boolean.class).single());
        String respuesta = mvc.perform(get("/api/albumes/"+album).with(user(propietario))).andReturn().getResponse().getContentAsString();
        assertFalse(respuesta.contains(privada.original()));
        assertFalse(respuesta.contains(CARPETA.toString()));
    }

    @Test void rechazaAnonimosAccesosAjenosYMutacionesSinCsrf() throws Exception {
        var foto = subir();
        mvc.perform(get("/api/albumes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/albumes/"+album).with(user(ajeno))).andExpect(status().isNotFound());
        mvc.perform(get("/api/albumes").with(user(ajeno))).andExpect(jsonPath("$.length()").value(0));
        for (String ruta : List.of("miniatura","contenido","descargar")) {
            mvc.perform(get("/api/fotos/"+foto.id()+"/"+ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/fotos/"+foto.id()+"/"+ruta).with(user(ajeno))).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/fotos/"+foto.id()).with(user(ajeno)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/fotos/"+foto.id()).with(user(propietario))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/albumes/"+album+"/fotos").file(new MockMultipartFile("foto","foto.png","image/png",imagen))
                .with(user(propietario))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/albumes/"+album+"/fotos").file(new MockMultipartFile("foto","foto.png","image/png",imagen))
                .with(user(ajeno)).with(csrf())).andExpect(status().isNotFound());
    }

    @Test void rechazaImagenInvalidaYSoloPermiteSubidasIndividuales() throws Exception {
        subir(album,"corrupta".getBytes()).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/albumes/"+album+"/fotos").file(new MockMultipartFile("foto","foto.svg","image/svg+xml","<svg/>".getBytes()))
                .with(user(propietario)).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/albumes/"+album+"/fotos").file(new MockMultipartFile("foto","una.png","image/png",imagen))
                .file(new MockMultipartFile("foto","otra.png","image/png",imagen)).with(user(propietario)).with(csrf()))
                .andExpect(status().isBadRequest());
        assertEquals(0,albumes.numeroFotos(album));
    }

    @Test void unFalloDeAlmacenamientoNoPublicaNiConsumeCuotaYPermiteReintentar() throws Exception {
        doThrow(new IllegalStateException("fallo preparado")).when(archivos).guardar(endsWith("/miniatura"),any(byte[].class));
        subir(album,imagen).andExpect(status().isServiceUnavailable());
        assertEquals(0,albumes.numeroFotos(album));
        assertEquals(0,albumes.bytesUsados(propietario.getId()));
        doCallRealMethod().when(archivos).guardar(anyString(),any(byte[].class));
        assertEquals("DISPONIBLE",subir().estado());
    }

    @Test void elFalloDeLimpiezaSeConservaSinDarUnaDisponibilidadFalsa() throws Exception {
        doThrow(new IllegalStateException("fallo preparado")).when(archivos).guardar(endsWith("/miniatura"),any(byte[].class));
        doThrow(new IllegalStateException("fallo preparado")).when(archivos).borrar(anyString());
        subir(album,imagen).andExpect(status().isServiceUnavailable());
        var pendiente = albumes.fotos(propietario.getId(),album).getFirst();
        assertEquals("ERROR",pendiente.estado());
        assertEquals(0,albumes.propio(propietario.getId(),album).orElseThrow().fotos());
        assertTrue(albumes.bytesUsados(propietario.getId())>0);
        mvc.perform(get("/api/fotos/"+pendiente.id()+"/contenido").with(user(propietario))).andExpect(status().isNotFound());
        doCallRealMethod().when(archivos).borrar(anyString());
        mvc.perform(delete("/api/fotos/"+pendiente.id()).with(user(propietario)).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0,albumes.bytesUsados(propietario.getId()));
    }

    @Test void borrarRetiraArchivosReferenciasYAccesoYUnFalloNoAnunciaExito() throws Exception {
        var foto = subir();
        doThrow(new IllegalStateException("fallo preparado")).when(archivos).borrar(endsWith("/miniatura"));
        mvc.perform(delete("/api/fotos/"+foto.id()).with(user(propietario)).with(csrf())).andExpect(status().isServiceUnavailable());
        assertEquals("ELIMINANDO",albumes.fotos(propietario.getId(),album).getFirst().estado());
        mvc.perform(get("/api/fotos/"+foto.id()+"/contenido").with(user(propietario))).andExpect(status().isNotFound());
        doCallRealMethod().when(archivos).borrar(anyString());
        mvc.perform(delete("/api/fotos/"+foto.id()).with(user(propietario)).with(csrf())).andExpect(status().isNoContent());
        assertFalse(Files.exists(CARPETA.resolve(foto.id().toString())));
        assertEquals(0,albumes.bytesUsados(propietario.getId()));
        assertTrue(albumes.fotoPropia(propietario.getId(),foto.id()).isEmpty());
        for (String ruta : List.of("miniatura","contenido","descargar"))
            mvc.perform(get("/api/fotos/"+foto.id()+"/"+ruta).with(user(propietario))).andExpect(status().isNotFound());
    }

    @Test void aplicaLimitePorAlbumYCuotaEntreTodosLosAlbumesDeLaCuenta() throws Exception {
        var normal = validador.limites();
        doReturn(new LimitesDto(normal.formatos(),normal.maxArchivoBytes(),1,normal.cuotaBytes(),normal.maxPixeles())).when(validador).limites();
        var foto = subir();
        subir(album,imagen).andExpect(status().isConflict());
        UUID segundoViaje = viaje(propietario.getId());
        UUID segundoAlbum = albumes.listar(propietario.getId()).stream().filter(a -> a.viajeId().equals(segundoViaje)).findFirst().orElseThrow().id();
        doReturn(new LimitesDto(normal.formatos(),normal.maxArchivoBytes(),100,foto.bytesOriginal()+foto.bytesMiniatura()+1,normal.maxPixeles())).when(validador).limites();
        subir(segundoAlbum,imagen).andExpect(status().isConflict());
        assertEquals(0,albumes.numeroFotos(segundoAlbum));
    }

    @Test void dosSubidasSimultaneasNoPuedenSuperarLaCuota() throws Exception {
        var normal = validador.limites();
        long bytes = validador.preparar(new MockMultipartFile("foto","foto.png","image/png",imagen)).bytesTotales();
        doReturn(new LimitesDto(normal.formatos(),normal.maxArchivoBytes(),100,bytes+bytes/2,normal.maxPixeles())).when(validador).limites();
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            var resultados = hilos.invokeAll(List.of(
                    () -> subir(album,imagen).andReturn().getResponse().getStatus(),
                    () -> subir(album,imagen).andReturn().getResponse().getStatus()));
            var estados = new ArrayList<Integer>();
            for (var resultado : resultados) estados.add((Integer) resultado.get());
            estados.sort(Integer::compareTo);
            assertEquals(List.of(201,409),estados);
        }
        assertEquals(1,albumes.numeroFotos(album));
        assertEquals(bytes,albumes.bytesUsados(propietario.getId()));
    }

    @Test void borrarAlbumDuranteSubidaAntiguaNoDejaOriginalHuerfano() throws Exception {
        comprobarBorradoDuranteSubidaAntigua(false,"/original");
    }

    @Test void borrarAlbumDuranteSubidaAntiguaNoDejaMiniaturaHuerfana() throws Exception {
        comprobarBorradoDuranteSubidaAntigua(false,"/miniatura");
    }

    @Test void borrarViajeConRecuerdosDuranteSubidaAntiguaNoDejaArchivosHuerfanos() throws Exception {
        comprobarBorradoDuranteSubidaAntigua(true,"/original");
    }

    @Test void borrarAlbumEnCursoNoConfirmaNiConservaUnaSubidaAntigua() throws Exception {
        comprobarBorradoEnCursoDuranteSubidaAntigua("album");
    }

    @Test void borrarViajeEnCursoNoConfirmaNiConservaUnaSubidaAntigua() throws Exception {
        comprobarBorradoEnCursoDuranteSubidaAntigua("viaje");
    }

    @Test void borrarFotoEnCursoNoConservaUnaSubidaAntigua() throws Exception {
        comprobarBorradoEnCursoDuranteSubidaAntigua("foto");
    }

    private void comprobarBorradoDuranteSubidaAntigua(boolean borrarViaje, String pausaEn) throws Exception {
        var escrituraPausada = new CountDownLatch(1);
        var continuarEscritura = new CountDownLatch(1);
        doAnswer(invocacion -> {
            if (((String) invocacion.getArgument(0)).endsWith(pausaEn)) {
                escrituraPausada.countDown();
                if (!continuarEscritura.await(30, TimeUnit.SECONDS))
                    throw new IllegalStateException("La subida tardó demasiado en reanudarse.");
            }
            return invocacion.callRealMethod();
        }).when(archivos).guardar(anyString(),any(byte[].class));

        UUID fotoId = null;
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            var subida = hilos.submit(() -> subir(album,imagen).andReturn().getResponse().getStatus());
            try {
                assertTrue(escrituraPausada.await(20,TimeUnit.SECONDS));
                fotoId = jdbc.sql("SELECT id FROM archivo_multimedia WHERE album_id=:album AND estado='PENDIENTE'")
                        .param("album",album).query(UUID.class).single();
                // Simula una escritura aún activa que superó el plazo de recuperación de diez minutos.
                jdbc.sql("UPDATE archivo_multimedia SET subido_en=now()-interval '11 minutes' WHERE id=:id")
                        .param("id",fotoId).update();
                if (borrarViaje) eliminarViaje(true).andExpect(status().isNoContent());
                else mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf()))
                        .andExpect(status().isNoContent());
            } finally {
                continuarEscritura.countDown();
            }
            assertEquals(503,subida.get(20,TimeUnit.SECONDS));
            assertTrue(albumes.fotoPropia(propietario.getId(),fotoId).isEmpty());
            assertEquals(0,albumes.bytesUsados(propietario.getId()));
            assertFalse(Files.exists(CARPETA.resolve(fotoId.toString())),
                    "La subida debe limpiar los archivos que escribió tras eliminarse su registro.");
        } finally {
            if (fotoId != null) {
                var carpetaFoto = CARPETA.resolve(fotoId.toString());
                Files.deleteIfExists(carpetaFoto.resolve("original"));
                Files.deleteIfExists(carpetaFoto.resolve("miniatura"));
                Files.deleteIfExists(carpetaFoto);
            }
        }
    }

    private void comprobarBorradoEnCursoDuranteSubidaAntigua(String tipoBorrado) throws Exception {
        var escrituraPausada = new CountDownLatch(1);
        var continuarEscritura = new CountDownLatch(1);
        var borradoPausado = new CountDownLatch(1);
        var continuarBorrado = new CountDownLatch(1);
        var primeraLimpieza = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocacion -> {
            escrituraPausada.countDown();
            if (!continuarEscritura.await(30,TimeUnit.SECONDS))
                throw new IllegalStateException("La subida tardó demasiado en reanudarse.");
            return invocacion.callRealMethod();
        }).when(archivos).guardar(endsWith("/original"),any(byte[].class));
        doAnswer(invocacion -> {
            var resultado = invocacion.callRealMethod();
            if (((String) invocacion.getArgument(0)).endsWith("/original")
                    && primeraLimpieza.compareAndSet(false,true)) {
                borradoPausado.countDown();
                if (!continuarBorrado.await(30,TimeUnit.SECONDS))
                    throw new IllegalStateException("El borrado tardó demasiado en reanudarse.");
            }
            return resultado;
        }).when(archivos).borrar(anyString());

        UUID fotoId = null;
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            var subida = hilos.submit(() -> subir(album,imagen).andReturn().getResponse().getStatus());
            try {
                assertTrue(escrituraPausada.await(20,TimeUnit.SECONDS));
                fotoId = jdbc.sql("SELECT id FROM archivo_multimedia WHERE album_id=:album AND estado='PENDIENTE'")
                        .param("album",album).query(UUID.class).single();
                jdbc.sql("UPDATE archivo_multimedia SET subido_en=now()-interval '11 minutes' WHERE id=:id")
                        .param("id",fotoId).update();
                UUID idFoto = fotoId;
                var borrado = hilos.submit(() -> {
                    if (tipoBorrado.equals("viaje")) return eliminarViaje(true).andReturn().getResponse().getStatus();
                    if (tipoBorrado.equals("foto")) return mvc.perform(delete("/api/fotos/"+idFoto)
                            .with(user(propietario)).with(csrf())).andReturn().getResponse().getStatus();
                    return mvc.perform(delete("/api/albumes/"+album).with(user(propietario)).with(csrf()))
                            .andReturn().getResponse().getStatus();
                });
                try {
                    assertTrue(borradoPausado.await(20,TimeUnit.SECONDS));
                    continuarEscritura.countDown();
                    assertEquals(503,subida.get(20,TimeUnit.SECONDS));
                } finally {
                    continuarEscritura.countDown();
                    continuarBorrado.countDown();
                }
                assertEquals(204,borrado.get(20,TimeUnit.SECONDS));
                assertTrue(albumes.fotoPropia(propietario.getId(),fotoId).isEmpty());
                assertEquals(0,albumes.bytesUsados(propietario.getId()));
                assertFalse(Files.exists(CARPETA.resolve(fotoId.toString())));
            } finally {
                continuarEscritura.countDown();
                continuarBorrado.countDown();
            }
        } finally {
            if (fotoId != null) {
                var carpetaFoto = CARPETA.resolve(fotoId.toString());
                Files.deleteIfExists(carpetaFoto.resolve("original"));
                Files.deleteIfExists(carpetaFoto.resolve("miniatura"));
                Files.deleteIfExists(carpetaFoto);
            }
        }
    }
}
