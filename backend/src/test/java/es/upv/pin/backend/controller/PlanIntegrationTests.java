package es.upv.pin.backend.controller;

import es.upv.pin.backend.PostgresTest;
import es.upv.pin.backend.model.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlanIntegrationTests extends PostgresTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired DataSource base;
    @Test void ofertaPublicaYPlanPrivadoConCsrfYLecturaActual() throws Exception {
        UUID id=UUID.randomUUID();
        jdbc.sql("INSERT INTO usuario(id,nombre,correo,password_hash,correo_verificado) VALUES(:id,'Prueba',:correo,'hash',true)")
                .param("id",id).param("correo",id+"@example.test").update();
        var principal=new UsuarioAutenticado(new Usuario(id,"Prueba",id+"@example.test","hash",true));
        mvc.perform(get("/api/planes/oferta")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plan").value("FREE")).andExpect(jsonPath("$[0].generacionesMes").value(5))
                .andExpect(jsonPath("$[1].plan").value("PREMIUM")).andExpect(jsonPath("$[1].cuotaBytes").value(2147483648L));
        mvc.perform(get("/api/planes/actual")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/planes/demo/cambiar").with(user(principal)).contentType("application/json").content("{\"plan\":\"PREMIUM\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/planes/demo/cambiar").with(user(principal)).with(csrf()).contentType("application/json").content("{\"plan\":\"PREMIUM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.demoHabilitada").value(true));
        mvc.perform(get("/api/planes/actual").with(user(principal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"));
        mvc.perform(post("/api/planes/demo/cambiar").with(user(principal)).with(csrf()).contentType("application/json").content("{\"plan\":\"INVALIDO\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void migracionAsignaFreeYRechazaOtrosValores() {
        String esquema="plan_"+UUID.randomUUID().toString().replace("-","");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(base).schemas(esquema).defaultSchema(esquema).target("8").load().migrate();
            UUID id=jdbc.sql("INSERT INTO "+esquema+".usuario(nombre,correo,password_hash) VALUES('Prueba','antiguo@example.test','hash') RETURNING id").query(UUID.class).single();
            org.flywaydb.core.Flyway.configure().dataSource(base).schemas(esquema).defaultSchema(esquema).target("9").load().migrate();
            assertEquals("FREE",jdbc.sql("SELECT plan FROM "+esquema+".usuario WHERE id=:id").param("id",id).query(String.class).single());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.sql("UPDATE "+esquema+".usuario SET plan='OTRO' WHERE id=:id").param("id",id).update());
        } finally { jdbc.sql("DROP SCHEMA IF EXISTS "+esquema+" CASCADE").update(); }
    }
}
