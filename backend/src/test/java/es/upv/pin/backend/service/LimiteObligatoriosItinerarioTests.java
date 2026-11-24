package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;

class LimiteObligatoriosItinerarioTests {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void incluyeCincuentaObligatoriosSinPerderElOrigen(boolean conAlojamiento) {
        var configuraciones = mock(ConfiguracionItinerarioService.class);
        var evaluacion = mock(EvaluacionCandidatosService.class);
        var desplazamientos = mock(DesplazamientoService.class);
        var candidatos = new ArrayList<CandidatoEvaluadoDto>();
        for (int i = 0; i < 50; i++) {
            var lugar = new LugarDto("lugar-" + i, "Lugar " + i, "Direccion " + i,
                    40.0 + i * .01, -3.0, List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);
            candidatos.add(new CandidatoEvaluadoDto(lugar, 90, 15, "Arte", "Interes", CostLevel.FREE,
                    null, List.of(), null, null, "LOCAL", CostConfidence.UNKNOWN,
                    ScheduleStatus.UNVERIFIED, null, null, null, null, null, null));
        }
        var alojamiento = conAlojamiento
                ? new AlojamientoDto("hotel", "Hotel", "Direccion del hotel", 40.1, -3.1) : null;
        var configuracion = new ConfiguracionItinerarioDto(
                new DestinoDto("destino", "Madrid", "Madrid", "España", 40.0, -3.0),
                new FechaHoraDto(LocalDateTime.of(2030, 5, 1, 10, 0), LocalDateTime.of(2030, 5, 10, 18, 0), 10),
                alojamiento == null ? "" : alojamiento.nombre(),
                new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), List.of(), NivelGasto.MODERADO, 10.0),
                new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 0),
                alojamiento, candidatos.stream().map(c -> new SeleccionLugarDto(c.lugar(), EstadoLugar.OBLIGATORIO)).toList());
        // La configuración admite las cincuenta selecciones antes de planificar.
        assertEquals(50, ReglasSeleccionLugares.validar(configuracion.selecciones(), configuracion.preferencias()).size());
        when(configuraciones.validar(configuracion)).thenReturn(configuracion);
        when(evaluacion.evaluar(configuracion)).thenReturn(new EvaluacionCandidatosDto(candidatos, false, List.of(), 4800, 0));
        when(desplazamientos.calcular(anyList())).thenAnswer(invocacion -> {
            List<PuntoRutaDto> puntos = invocacion.getArgument(0);
            var rutas = new ArrayList<DesplazamientoDto>();
            for (var origen : puntos) for (var destino : puntos) if (!origen.id().equals(destino.id())) {
                rutas.add(new DesplazamientoDto(origen.id(), destino.id(), 10.0, 1.0,
                        EstadoDesplazamiento.DISPONIBLE, "ORS simulado"));
            }
            return rutas;
        });
        var servicio = new GeneracionItinerarioService(configuraciones, evaluacion, desplazamientos);

        var resultado = assertDoesNotThrow(() -> servicio.generar(configuracion));

        assertEquals(50, resultado.dias().stream().flatMap(d -> d.bloques().stream())
                .filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).count());
        assertEquals(50, resultado.candidatosEnMatriz());
        verify(desplazamientos).calcular(argThat(puntos -> puntos.size() == 51
                && puntos.stream().filter(p -> p.id().equals("referencia:inicio")).count() == 1
                && candidatos.stream().allMatch(c -> puntos.stream().anyMatch(p -> p.id().equals(c.lugar().id())))));
    }
}
