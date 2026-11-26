package es.upv.pin.backend.service;

import es.upv.pin.backend.PostgresTest;
import es.upv.pin.backend.exception.ControlGeneracionException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CuotaGeneracionIntegrationTests extends PostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired PlanService planes;
    private UUID usuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario(id,nombre,correo,password_hash,correo_verificado) VALUES (?, 'Prueba', ?, 'hash', true)", id, id + "@example.test");
        return id;
    }
    private CuotaGeneracionService servicio(String fecha) {
        return new CuotaGeneracionService(jdbc, tx, planes, Clock.fixed(Instant.parse(fecha), ZoneOffset.UTC));
    }
    @Test void reservasConcurrentesCompartenCuenta() throws Exception {
        UUID id = usuario(); var cuota = servicio("2026-11-20T12:00:00Z");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tareas = new ArrayList<Callable<Boolean>>();
            for (int i=0; i<8; i++) tareas.add(() -> { try { cuota.reservar(id); return true; } catch (ControlGeneracionException e) { return false; } });
            int aceptadas = 0; for (var f : pool.invokeAll(tareas)) if (f.get()) aceptadas++;
            assertEquals(3, aceptadas); assertEquals(0, cuota.consultar(id).restantes());
            assertEquals(2, cuota.consultar(id).restantesMensuales());
        }
    }
    @Test void reiniciosHoraYMesMadrid() {
        UUID id = usuario(); var primero = servicio("2026-11-30T21:00:00Z");
        primero.reservar(id); primero.reservar(id); primero.reservar(id);
        var despues = servicio("2026-11-30T22:00:00Z");
        assertEquals(3, despues.consultar(id).restantes());
        despues.reservar(id); despues.reservar(id);
        assertThrows(ControlGeneracionException.class, () -> despues.reservar(id));
        var diciembre = servicio("2026-11-30T23:00:00Z").consultar(id);
        assertEquals(5, diciembre.restantesMensuales());
        assertEquals(Instant.parse("2026-12-31T23:00:00Z"), diciembre.reinicioMensual());
    }
}
