package es.upv.pin.backend.service;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallesComercialesServiceTests {
    private final GeoapifyClient cliente = mock(GeoapifyClient.class);
    private final CallesComercialesService servicio = new CallesComercialesService(cliente);
    private final DestinoDto destino = new DestinoDto("madrid", "Madrid", null, "España", 40.42, -3.7);

    private LugarGeoapifyDto comercio(String id, String calle, String portal, double longitud) {
        return new LugarGeoapifyDto(id, "Tienda " + id, 40.42, longitud, List.of("commercial.clothing"),
                calle + ", " + portal, "Mo-Fr 10:00-20:00", null, calle, portal, null);
    }

    private List<LugarGeoapifyDto> comercios(String calle) {
        return List.of(comercio(calle + "1", calle, "1", -3.706), comercio(calle + "2", calle, "3", -3.704),
                comercio(calle + "3", calle, "9", -3.702));
    }

    private LugarGeoapifyDto calle(String nombre) {
        return new LugarGeoapifyDto("calle-" + nombre, nombre, 40.42, -3.704, List.of("highway.primary"),
                nombre + ", Madrid", null, null, nombre, null, "street");
    }

    @Test
    void proponeUnaCalleConComerciosRealesYLaLocalizaSinCopiarHorariosDeLasTiendas() {
        when(cliente.buscarCalle("Gran Vía", destino)).thenReturn(calle("Gran Vía"));
        var resultado = servicio.buscar(destino, comercios("Gran Vía"));
        assertEquals(1, resultado.size());
        assertEquals("calle-Gran Vía", resultado.getFirst().place_id());
        assertNull(resultado.getFirst().opening_hours());
        verify(cliente).buscarCalle("Gran Vía", destino);
    }

    @Test
    void noInventaUnaCallePorUnaTiendaOUnCentroComercialConUnSoloPortal() {
        List<LugarGeoapifyDto> tiendas = new ArrayList<>();
        tiendas.add(comercio("a", "Calle Residencial", "1", -3.706));
        tiendas.add(comercio("b", "Calle del Centro", "8", -3.706));
        tiendas.add(comercio("c", "Calle del Centro", "8", -3.704));
        tiendas.add(comercio("d", "Calle del Centro", "8", -3.702));
        assertTrue(servicio.buscar(destino, tiendas).isEmpty());
        verifyNoInteractions(cliente);
    }

    @Test
    void limitaLasLocalizacionesATresYNoCuentaCopiasDeUnaTiendaComoComerciosDistintos() {
        List<LugarGeoapifyDto> tiendas = new ArrayList<>();
        for (int i = 0; i < 10; i++) tiendas.addAll(comercios("Calle " + i));
        tiendas.addAll(comercios("Calle 0"));
        when(cliente.buscarCalle(anyString(), eq(destino))).thenAnswer(inv -> calle(inv.getArgument(0)));
        assertEquals(3, servicio.buscar(destino, tiendas).size());
        verify(cliente, times(3)).buscarCalle(anyString(), eq(destino));
    }

    @Test
    void descartaResultadosDeOtraCalleYConservaElFlujoSiFallaLaLocalizacion() {
        when(cliente.buscarCalle("Gran Vía", destino)).thenReturn(calle("Otra calle"));
        assertTrue(servicio.buscar(destino, comercios("Gran Vía")).isEmpty());
        when(cliente.buscarCalle("Gran Vía", destino)).thenThrow(new IllegalStateException("No disponible"));
        assertTrue(servicio.buscar(destino, comercios("Gran Vía")).isEmpty());
    }
}
