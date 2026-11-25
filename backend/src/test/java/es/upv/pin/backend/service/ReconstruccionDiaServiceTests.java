package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Codigo;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Nivel;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconstruccionDiaServiceTests {
    private static final String INICIO = "referencia:inicio";
    private final LocalDate fecha = LocalDate.of(2026, 12, 7);
    private final RitmoDiarioDto ritmo = new RitmoDiarioDto(
            LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10);
    private final ReconstruccionDiaService servicio = new ReconstruccionDiaService(
            new FranjasEdicionService(), new ColocacionVisitaService(new EvaluadorHorarioService()));

    @Test
    void reconstruyeDesdeLaReferenciaYContinuaDesdeElRestauranteDespuesDeComer() {
        var primera = visita("a", 60, "Mo 10:00-18:00");
        var segunda = visita("b", 150, "Mo 10:00-18:00");
        var original = dia(true);
        var resultado = servicio.reconstruir(original, List.of(primera, segunda), ritmo, rutas());

        assertTrue(resultado.incidencias().isEmpty());
        var visitas = bloques(resultado, TipoBloqueItinerario.VISITA);
        assertEquals(List.of("a", "b"), visitas.stream().map(b -> b.candidato().lugar().id()).toList());
        assertEquals(fecha.atTime(10, 20), visitas.getFirst().inicio());
        assertEquals(fecha.atTime(15, 20), visitas.getLast().inicio());
        assertEquals(fecha.atTime(17, 50), visitas.getLast().fin());
        var tramos = bloques(resultado, TipoBloqueItinerario.TRASLADO);
        assertEquals(List.of(INICIO, "a", "restaurante"), tramos.stream().map(BloqueItinerarioDto::origenId).toList());
        assertEquals(List.of("a", "restaurante", "b"), tramos.stream().map(BloqueItinerarioDto::destinoId).toList());
        assertEquals(fecha.atTime(13, 0), bloques(resultado, TipoBloqueItinerario.COMIDA).getFirst().inicio());
        assertEquals(fecha.atTime(15, 0), bloques(resultado, TipoBloqueItinerario.COMIDA).getFirst().fin());
        assertEquals(2, original.bloques().size());
        assertEquals("antiguo", original.bloques().getFirst().origenId());
        assertTrue(tramos.stream().noneMatch(b -> "antiguo".equals(b.origenId())));
        comprobarValido(resultado);
    }

    @Test
    void laComidaGenericaConservaLaSecuenciaSinInventarRestaurante() {
        var resultado = servicio.reconstruir(dia(false),
                List.of(visita("a", 60, "Mo 10:00-18:00"), visita("b", 150, "Mo 10:00-18:00")),
                ritmo, rutas());

        assertTrue(resultado.incidencias().isEmpty());
        assertNull(bloques(resultado, TipoBloqueItinerario.COMIDA).getFirst().restaurante());
        var ultimoTramo = bloques(resultado, TipoBloqueItinerario.TRASLADO).getLast();
        assertEquals("a", ultimoTramo.origenId());
        assertEquals("b", ultimoTramo.destinoId());
        comprobarValido(resultado);
    }

    @Test
    void pruebaDespuesDeComerUnaVisitaQueSoloAbrePorLaTarde() {
        var resultado = servicio.reconstruir(dia(true),
                List.of(visita("a", 60, "Mo 15:00-18:00")), ritmo, rutas());

        assertTrue(resultado.incidencias().isEmpty());
        assertEquals(fecha.atTime(15, 20), bloques(resultado, TipoBloqueItinerario.VISITA).getFirst().inicio());
        comprobarValido(resultado);
    }

    @Test
    void eliminarTodasLasVisitasMantieneLaComidaSinResucitarVisitasAnteriores() {
        var resultado = servicio.reconstruir(dia(true), List.of(), ritmo, rutas());

        assertTrue(resultado.incidencias().isEmpty());
        assertTrue(bloques(resultado, TipoBloqueItinerario.VISITA).isEmpty());
        assertEquals(1, bloques(resultado, TipoBloqueItinerario.COMIDA).size());
        assertEquals(INICIO, bloques(resultado, TipoBloqueItinerario.TRASLADO).getFirst().origenId());
        comprobarValido(resultado);
    }

    @Test
    void identificaCadaVisitaQueNoCabeSinReducirSuDuracion() {
        var primera = visita("a", 300, "Mo 10:00-18:00");
        var segunda = visita("b", 60, "Mo 10:00-18:00");
        var resultado = servicio.reconstruir(dia(false), List.of(primera, segunda), ritmo, rutas());

        assertTrue(bloques(resultado, TipoBloqueItinerario.VISITA).isEmpty());
        assertEquals(List.of(primera.actividadId(), segunda.actividadId()),
                resultado.incidencias().stream().map(IncidenciaEdicionDto::actividadId).toList());
        assertTrue(resultado.incidencias().stream().allMatch(i -> i.nivel() == Nivel.CONFLICTO));
    }

    @Test
    void unOrdenConHorarioIncompatiblePermaneceIdentificadoComoConflicto() {
        var visita = visita("a", 60, "Mo off");
        var resultado = servicio.reconstruir(dia(false), List.of(visita), ritmo, rutas());

        assertEquals(1, bloques(resultado, TipoBloqueItinerario.VISITA).size());
        assertTrue(resultado.incidencias().stream().anyMatch(i -> i.codigo() == Codigo.HORARIO_INCOMPATIBLE
                && visita.actividadId().equals(i.actividadId()) && i.nivel() == Nivel.CONFLICTO));
    }

    @Test
    void laRutaAusenteAlRestauranteNoSePresentaComoUnPlanValido() {
        var resultado = servicio.reconstruir(dia(true), List.of(), ritmo, List.of());

        assertTrue(resultado.incidencias().stream().anyMatch(i -> i.codigo() == Codigo.RUTA_NO_DISPONIBLE
                && "restaurante".equals(i.lugarId()) && i.nivel() == Nivel.CONFLICTO));
        assertTrue(bloques(resultado, TipoBloqueItinerario.COMIDA).isEmpty());
    }

    @Test
    void retiraLasDistanciasAntiguasDeLasOpcionesDeComida() {
        var resultado = servicio.reconstruir(dia(true), List.of(), ritmo, rutas());
        var comida = bloques(resultado, TipoBloqueItinerario.COMIDA).getFirst();

        assertEquals("restaurante", comida.restaurante().id());
        assertNull(comida.restaurante().distanciaMetros());
        assertNull(comida.restaurante().duracionSegundos());
        assertEquals("alternativa", comida.alternativasComida().getFirst().id());
        assertNull(comida.alternativasComida().getFirst().distanciaMetros());
        assertNull(comida.alternativasComida().getFirst().duracionSegundos());
    }

    @Test
    void unDiaSinDisponibilidadConVisitasPendientesDevuelveConflicto() {
        var visita = visita("a", 60, "Mo 10:00-18:00");
        var original = new DiaItinerarioDto(fecha, null, null, List.of());
        var resultado = servicio.reconstruir(original, List.of(visita), ritmo, List.of());

        assertTrue(resultado.dia().bloques().isEmpty());
        assertEquals(visita.actividadId(), resultado.incidencias().getFirst().actividadId());
        assertEquals(Nivel.CONFLICTO, resultado.incidencias().getFirst().nivel());
    }

    private void comprobarValido(ReconstruccionDiaService.Resultado resultado) {
        var preferencias = new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS),
                List.of(), List.of(), NivelGasto.MODERADO, 3.0);
        var validacion = new ValidadorPlanService().validar(List.of(resultado.dia()), preferencias);
        assertTrue(validacion.valido(), () -> String.join("; ", validacion.incidencias()));
        var bloques = resultado.dia().bloques();
        assertEquals(resultado.dia().inicioDisponible(), bloques.getFirst().inicio());
        assertEquals(resultado.dia().finDisponible(), bloques.getLast().fin());
        for (int i = 1; i < bloques.size(); i++) {
            assertEquals(bloques.get(i - 1).fin(), bloques.get(i).inicio());
        }
    }

    private static List<BloqueItinerarioDto> bloques(ReconstruccionDiaService.Resultado resultado,
            TipoBloqueItinerario tipo) {
        return resultado.dia().bloques().stream().filter(b -> b.tipo() == tipo).toList();
    }

    private DiaItinerarioDto dia(boolean conRestaurante) {
        var viejo = new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO,
                fecha.atTime(10, 0), fecha.atTime(10, 5), null,
                "antiguo", "restaurante", 123.0, 300.0, "fuente antigua", false, "Viejo");
        var restaurante = new OpcionComidaDto("restaurante", "Comida", 39.48, -0.37,
                "Valencia", null, null, 9999.0, 9999.0);
        var alternativa = new OpcionComidaDto("alternativa", "Otra opción", 39.48, -0.37,
                "Valencia", null, null, 9999.0, 9999.0);
        var comida = new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA,
                fecha.atTime(13, 0), fecha.atTime(15, 0), null,
                null, null, null, null, null, conRestaurante, "Comida",
                conRestaurante ? restaurante : null, conRestaurante ? List.of(alternativa) : List.of());
        return new DiaItinerarioDto(fecha, fecha.atTime(10, 0), fecha.atTime(18, 0), List.of(viejo, comida));
    }

    private static List<DesplazamientoDto> rutas() {
        var resultado = new ArrayList<DesplazamientoDto>();
        for (String origen : List.of(INICIO, "a", "b", "restaurante")) {
            for (String destino : List.of(INICIO, "a", "b", "restaurante")) {
                if (!origen.equals(destino)) resultado.add(new DesplazamientoDto(origen, destino,
                        500.0, 600.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice"));
            }
        }
        return resultado;
    }

    private static Visita visita(String id, int minutos, String horario) {
        var lugar = new LugarDto(id, "Museo " + id, "Valencia", 39.47, -0.37,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), horario);
        return new Visita(UUID.randomUUID(), new CandidatoEvaluadoDto(lugar, 80, minutos,
                "Arte", "Prueba", CostLevel.FREE, null, List.of(), horario, "Geoapify Places",
                "LOCAL", CostConfidence.CONFIRMED, ScheduleStatus.VERIFIED,
                0.0, 0.0, "EUR", null, null, 80));
    }
}
