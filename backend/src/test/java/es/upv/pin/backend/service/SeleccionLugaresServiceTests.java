package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SeleccionLugaresServiceTests {
    private final PreparacionCandidatosService preparacion = mock(PreparacionCandidatosService.class);
    private final ConfiguracionItinerarioService configuraciones = new ConfiguracionItinerarioService(new FechaHoraService(), new PreferenciasService());
    private final CatalogoLugaresService servicio = new CatalogoLugaresService(preparacion, configuraciones, new MuestraLugaresService());
    private final MockHttpSession sesion = new MockHttpSession();
    private final LugarDto museo = lugar("museo", "Museo del Prado", CategoriaTuristica.ARTE_MUSEOS);
    private final LugarDto parque = lugar("parque", "Parque del Retiro", CategoriaTuristica.NATURALEZA_PARQUES);

    private LugarDto lugar(String id, String nombre, CategoriaTuristica categoria) {
        return new LugarDto(id, nombre, "Madrid", 40.4, -3.7, List.of(categoria), List.of(), null);
    }
    private ConfiguracionItinerarioDto viaje(List<SeleccionLugarDto> selecciones) {
        return new ConfiguracionItinerarioDto(new DestinoDto("madrid", "Madrid", null, "España", 40.4, -3.7),
                new FechaHoraDto(LocalDateTime.parse("2026-12-01T10:00"), LocalDateTime.parse("2026-12-01T18:00"), 1), "",
                new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS, CategoriaTuristica.NATURALEZA_PARQUES),
                        List.of(), List.of(), NivelGasto.MODERADO, 3.0), null, selecciones);
    }
    private void cargar() {
        when(preparacion.muestraInicial(any())).thenReturn(List.of(museo, parque));
        servicio.buscar(new BusquedaLugaresDto(viaje(List.of()), "", false), sesion);
    }

    @Test void reutilizarCatalogoNoVuelveAPrepararLugares() {
        cargar();
        servicio.buscar(new BusquedaLugaresDto(viaje(List.of()), "", false), sesion);
        verify(preparacion, times(1)).muestraInicial(any());
        verifyNoMoreInteractions(preparacion);
    }

    @Test void unEstadoVigenteSinDuplicarYCambioYRetirada() {
        cargar();
        var config = viaje(List.of());
        for (var estado : List.of(EstadoLugar.OBLIGATORIO, EstadoLugar.OBLIGATORIO, EstadoLugar.PREFERIDO, EstadoLugar.EVITADO)) {
            var respuesta = servicio.cambiar(new CambioSeleccionLugarDto(config, museo.id(), estado), sesion);
            assertEquals(List.of(new SeleccionLugarDto(museo, estado)), respuesta.selecciones());
            config = viaje(respuesta.selecciones());
            assertEquals(respuesta.selecciones(), servicio.resolver(config, sesion).selecciones());
        }
        assertTrue(servicio.cambiar(new CambioSeleccionLugarDto(config, museo.id(), null), sesion).selecciones().isEmpty());
        verify(preparacion).muestraInicial(any());
        verifyNoMoreInteractions(preparacion);
    }

    @Test void cambiarPreferenciasRetiraSoloLosIncompatiblesYLoExplica() {
        cargar();
        var config = viaje(List.of(new SeleccionLugarDto(museo, EstadoLugar.PREFERIDO), new SeleccionLugarDto(parque, EstadoLugar.OBLIGATORIO)));
        var nuevas = new PreferenciasDto(List.of(CategoriaTuristica.NATURALEZA_PARQUES), List.of(), List.of(), NivelGasto.MODERADO, 3.0);
        var cambiada = new ConfiguracionItinerarioDto(
                config.destino(),
                config.disponibilidad(),
                config.alojamiento(),
                nuevas,
                config.ritmo(),
                config.alojamientoLocalizado(),
                config.selecciones());
        var respuesta = servicio.revisar(cambiada, sesion);
        assertEquals(List.of(new SeleccionLugarDto(parque, EstadoLugar.OBLIGATORIO)), respuesta.selecciones());
        assertTrue(respuesta.avisos().getFirst().contains(museo.nombre()));
        assertThrows(IllegalArgumentException.class, () -> servicio.resolver(cambiada, sesion));
    }

    @Test void seleccionSoloUtilizaMetadatosConocidosPorElServidorEnSuSesion() {
        cargar();
        var falso = new LugarDto(museo.id(), "Nombre manipulado", "Otra dirección", 0.0, 0.0, museo.categorias(), List.of(), null);
        var config = viaje(List.of(new SeleccionLugarDto(falso, EstadoLugar.PREFERIDO)));
        assertEquals(museo, servicio.resolver(config, sesion).selecciones().getFirst().lugar());
        assertThrows(IllegalArgumentException.class, () -> servicio.resolver(config, new MockHttpSession()));
        assertThrows(IllegalArgumentException.class, () -> servicio.cambiar(new CambioSeleccionLugarDto(viaje(List.of()), "inventado", EstadoLugar.OBLIGATORIO), sesion));
    }

    @Test void escribirNoRepiteConsultasExternasYSePuedeBuscarFueraDelCatalogoInicial() {
        cargar();
        var config = viaje(List.of());
        assertEquals(1, servicio.buscar(new BusquedaLugaresDto(config, "Prado", false), sesion).lugares().size());
        servicio.buscar(new BusquedaLugaresDto(config, "Museo", false), sesion);
        var nuevo = lugar("reina", "Museo Reina Sofía", CategoriaTuristica.ARTE_MUSEOS);
        when(preparacion.buscar(any(), eq("Reina"))).thenReturn(List.of(nuevo));
        var respuesta = servicio.buscar(new BusquedaLugaresDto(config, "Reina", true), sesion);
        assertEquals(List.of(nuevo), respuesta.lugares());
        servicio.buscar(new BusquedaLugaresDto(config, "Reina", true), sesion);
        verify(preparacion).muestraInicial(any());
        verify(preparacion).buscar(any(), eq("Reina"));
        assertEquals(nuevo, servicio.cambiar(new CambioSeleccionLugarDto(config, nuevo.id(), EstadoLugar.OBLIGATORIO), sesion).selecciones().getFirst().lugar());
    }

    @Test void aliasNoCreanUnaSegundaSeleccion() {
        var alias = lugar("otro-id", museo.nombre(), CategoriaTuristica.ARTE_MUSEOS);
        when(preparacion.muestraInicial(any())).thenReturn(List.of(museo, alias));
        var resultado = servicio.buscar(new BusquedaLugaresDto(viaje(List.of()), "", false), sesion);
        assertEquals(1, resultado.lugares().size());
        var config = viaje(List.of(new SeleccionLugarDto(museo, EstadoLugar.OBLIGATORIO)));
        var respuesta = servicio.cambiar(new CambioSeleccionLugarDto(config, alias.id(), EstadoLugar.PREFERIDO), sesion);
        assertEquals(List.of(new SeleccionLugarDto(museo, EstadoLugar.PREFERIDO)), respuesta.selecciones());
    }

    @Test void validarBloqueaEstadosContradictoriosYRepetirNoDuplica() {
        var preferencias = viaje(List.of()).preferencias();
        var a = new SeleccionLugarDto(museo, EstadoLugar.PREFERIDO);
        assertEquals(List.of(a), ReglasSeleccionLugares.validar(List.of(a, a), preferencias));
        assertThrows(IllegalArgumentException.class, () -> ReglasSeleccionLugares.validar(List.of(a,
                new SeleccionLugarDto(museo, EstadoLugar.EVITADO)), preferencias));
        assertTrue(ReglasSeleccionLugares.aplicar(viaje(List.of(new SeleccionLugarDto(museo, EstadoLugar.EVITADO))), List.of(museo)).isEmpty());
    }

    @Test void recortarElCatalogoNoRetiraUnLugarSeleccionado() {
        cargar();
        var config = viaje(List.of(new SeleccionLugarDto(museo, EstadoLugar.OBLIGATORIO)));
        when(preparacion.buscar(any(), eq("Lugar"))).thenReturn(java.util.stream.IntStream.range(0, 4010)
                .mapToObj(i -> lugar("nuevo" + i, "Lugar " + i, CategoriaTuristica.ARTE_MUSEOS)).toList());
        var resultado = servicio.buscar(new BusquedaLugaresDto(config, "Lugar", true), sesion);
        assertEquals(config.selecciones(), resultado.selecciones());
        assertEquals(museo, servicio.resolver(config, sesion).selecciones().getFirst().lugar());
    }

    @Test void limitaSeleccionesYRechazaLugaresSinCoordenadas() {
        var preferencias = viaje(List.of()).preferencias();
        var demasiadas = java.util.stream.IntStream.range(0, 51).mapToObj(i -> new SeleccionLugarDto(
                lugar("l" + i, "Lugar " + i, CategoriaTuristica.ARTE_MUSEOS), EstadoLugar.PREFERIDO)).toList();
        assertThrows(IllegalArgumentException.class, () -> ReglasSeleccionLugares.validar(demasiadas, preferencias));
        var sinPunto = new LugarDto("sin", "Sin coordenadas", null, null, null, museo.categorias(), List.of(), null);
        assertThrows(IllegalArgumentException.class, () -> ReglasSeleccionLugares.validar(
                List.of(new SeleccionLugarDto(sinPunto, EstadoLugar.OBLIGATORIO)), preferencias));
    }

    @Test void ampliarElCatalogoEsExplicitoConservaLaSeleccionYNoSeRepiteAlVolver() {
        cargar();
        var config = viaje(List.of(new SeleccionLugarDto(museo, EstadoLugar.OBLIGATORIO)));
        var nuevo = lugar("nuevo", "Museo Reina Sofía", CategoriaTuristica.ARTE_MUSEOS);
        when(preparacion.preparar(any())).thenReturn(List.of(museo, parque, nuevo));
        var completo = servicio.buscar(new BusquedaLugaresDto(config, "", false, true), sesion);
        assertTrue(completo.catalogoCompleto());
        assertEquals(config.selecciones(), completo.selecciones());
        assertTrue(completo.lugares().contains(nuevo));
        assertTrue(servicio.buscar(new BusquedaLugaresDto(config, "", false), sesion).catalogoCompleto());
        servicio.buscar(new BusquedaLugaresDto(config, "", false, true), sesion);
        verify(preparacion).muestraInicial(any());
        verify(preparacion).preparar(any());
    }

    @Test void buscarPorNombreNoObligaACargarLaMuestraNiElCatalogoCompleto() {
        when(preparacion.buscar(any(), eq("Prado"))).thenReturn(List.of(museo));
        var respuesta = servicio.buscar(new BusquedaLugaresDto(viaje(List.of()), "Prado", true), sesion);
        assertEquals(List.of(museo), respuesta.lugares());
        assertFalse(respuesta.catalogoCompleto());
        verify(preparacion, never()).muestraInicial(any());
        verify(preparacion, never()).preparar(any());
    }
}
