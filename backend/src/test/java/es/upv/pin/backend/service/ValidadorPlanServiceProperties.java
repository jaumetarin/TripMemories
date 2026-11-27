package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.BloqueItinerarioDto;
import es.upv.pin.backend.dto.CandidatoEvaluadoDto;
import es.upv.pin.backend.dto.DiaItinerarioDto;
import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.dto.ResultadoValidacionPlan;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.EstadoHorarioVisita;
import es.upv.pin.backend.model.NivelGasto;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

class ValidadorPlanServiceProperties {
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 27);
    private static final LocalDateTime INICIO = FECHA.atTime(10, 0);
    private static final LocalDateTime FIN = FECHA.atTime(18, 0);
    private final ValidadorPlanService validador = new ValidadorPlanService();
    private final EvaluadorHorarioService horarios = new EvaluadorHorarioService();

    private static CandidatoEvaluadoDto candidato(String id, String nombre, int duracion, CategoriaTuristica cat) {
        LugarDto lugar = new LugarDto(id, nombre, "Calle 1", 39.47, -0.37, List.of(cat), List.of(), null);
        return new CandidatoEvaluadoDto(lugar, 80, duracion, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null);
    }

    @Property(tries = 100)
    void secuenciaValidaDeVisitasYTrasladosPasaValidacion(
            @ForAll @IntRange(min = 30, max = 90) int dur1,
            @ForAll @IntRange(min = 5, max = 30) int trasladoMin,
            @ForAll @IntRange(min = 30, max = 90) int dur2) {
        LocalDateTime f1 = INICIO.plusMinutes(dur1);
        LocalDateTime fTras = f1.plusMinutes(trasladoMin);
        LocalDateTime f2 = fTras.plusMinutes(dur2);

        BloqueItinerarioDto v1 = new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, INICIO, f1,
                candidato("p1", "Museo Uno", dur1, CategoriaTuristica.ARTE_MUSEOS),
                null, null, null, null, null, true, null);
        BloqueItinerarioDto tr = new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO, f1, fTras,
                null, "p1", "p2", 400.0, (double) trasladoMin * 60, "ORS", false, null);
        BloqueItinerarioDto v2 = new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, fTras, f2,
                candidato("p2", "Parque Dos", dur2, CategoriaTuristica.NATURALEZA_PARQUES),
                null, null, null, null, null, true, null);

        DiaItinerarioDto dia = new DiaItinerarioDto(FECHA, INICIO, FIN, List.of(v1, tr, v2));
        PreferenciasDto prefs = new PreferenciasDto(
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), List.of(), NivelGasto.MODERADO, 3.0);

        ResultadoValidacionPlan res = validador.validar(List.of(dia), prefs);
        assertTrue(res.valido(), "Incidencias inesperadas: " + res.incidencias());
        assertFalse(res.parcial());
    }

    @Property(tries = 100)
    void solapeDeVisitasEsDetectadoSiempre(
            @ForAll @IntRange(min = 1, max = 59) int solapeMin) {
        LocalDateTime inicioV2 = INICIO.plusMinutes(60 - solapeMin);
        BloqueItinerarioDto v1 = new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, INICIO, INICIO.plusMinutes(60),
                candidato("p1", "Museo Uno", 60, CategoriaTuristica.ARTE_MUSEOS),
                null, null, null, null, null, true, null);
        BloqueItinerarioDto v2 = new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, inicioV2, inicioV2.plusMinutes(60),
                candidato("p2", "Museo Dos", 60, CategoriaTuristica.HISTORIA_ARQUITECTURA),
                null, null, null, null, null, true, null);

        DiaItinerarioDto dia = new DiaItinerarioDto(FECHA, INICIO, FIN, List.of(v1, v2));
        ResultadoValidacionPlan res = validador.validar(List.of(dia), null);
        assertFalse(res.valido());
        assertTrue(res.incidencias().stream().anyMatch(s -> s.contains("solapados")));
    }

    @Property(tries = 80)
    void visitaFueraDeLaVentanaEsRechazada(
            @ForAll @IntRange(min = 1, max = 120) int excesoMinutos,
            @ForAll boolean antesDelInicio) {
        LocalDateTime inicio = antesDelInicio ? INICIO.minusMinutes(excesoMinutos) : FIN.minusMinutes(30);
        LocalDateTime fin = antesDelInicio ? INICIO.plusMinutes(30) : FIN.plusMinutes(excesoMinutos);
        var visita = new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, inicio, fin,
                candidato("p1", "Museo", 30, CategoriaTuristica.ARTE_MUSEOS),
                null, null, null, null, null, true, null);
        var dia = new DiaItinerarioDto(FECHA, INICIO, FIN, List.of(visita));

        ResultadoValidacionPlan res = validador.validar(List.of(dia), null);
        assertFalse(res.valido());
        assertTrue(res.incidencias().stream().anyMatch(incidencia -> incidencia.contains("fuera de la ventana")));
    }

    @Property(tries = 80)
    void visitasDeCategoriaExcluidaSonRechazadasPorValidador(
            @ForAll CategoriaTuristica excluida) {
        BloqueItinerarioDto v = new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, INICIO, INICIO.plusMinutes(60),
                candidato("p1", "Lugar", 60, excluida),
                null, null, null, null, null, true, null);
        DiaItinerarioDto dia = new DiaItinerarioDto(FECHA, INICIO, FIN, List.of(v));
        PreferenciasDto prefs = new PreferenciasDto(List.of(), List.of(excluida), List.of(), NivelGasto.MODERADO, 3.0);

        ResultadoValidacionPlan res = validador.validar(List.of(dia), prefs);
        assertFalse(res.valido());
        assertTrue(res.incidencias().stream().anyMatch(s -> s.contains("Visita excluida")));
    }

    @Property(tries = 100)
    void evaluadorHorarioRespeta24x7YDiasCerrados(
            @ForAll @IntRange(min = 8, max = 18) int horaInicio,
            @ForAll @IntRange(min = 30, max = 120) int duracionMin) {
        LocalDateTime ini = FECHA.atTime(horaInicio, 0);
        LocalDateTime fin = ini.plusMinutes(duracionMin);

        LugarDto abiertoSiempre = new LugarDto("a", "A", null, null, null, List.of(), List.of(), "24/7");
        CandidatoEvaluadoDto c24 = new CandidatoEvaluadoDto(abiertoSiempre, 80, duracionMin, null, null, null, null,
                List.of(), "24/7", "Geoapify", null, null, null, null, null, null, null, null, null);
        assertEquals(EstadoHorarioVisita.COMPATIBLE_SEGUN_FUENTE, horarios.evaluar(c24, ini, fin));

        // 2026-11-27 es viernes (Fr)
        LugarDto cerradoViernes = new LugarDto("b", "B", null, null, null, List.of(), List.of(), "Fr off");
        CandidatoEvaluadoDto cOff = new CandidatoEvaluadoDto(cerradoViernes, 80, duracionMin, null, null, null, null,
                List.of(), "Fr off", "Geoapify", null, null, null, null, null, null, null, null, null);
        assertEquals(EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE, horarios.evaluar(cOff, ini, fin));
    }
}
