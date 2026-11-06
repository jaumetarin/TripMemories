package es.upv.pin.backend.service;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ValidacionUt14Test {
 private final EvaluadorHorarioService horarios = new EvaluadorHorarioService();
 private static final LocalDateTime L = LocalDateTime.of(2026,11,9,9,0);
 private CandidatoEvaluadoDto candidato(String h,String f) {
  LugarDto lugar = new LugarDto("museo","Museo",null,null,null,List.of(CategoriaTuristica.ARTE_MUSEOS),List.of(),h);
  return new CandidatoEvaluadoDto(lugar,1,60,null,null,null,null,List.of(),h,f,null,null,null,null,null,null);
 }
 private BloqueItinerarioDto visita(LocalDateTime t) { return new BloqueItinerarioDto(TipoBloqueItinerario.VISITA,t,t.plusHours(1),candidato(null,null),null,null,null,null,null,true,null); }
 @Test void horarioExplicitoYAmbiguedad() {
  assertEquals(EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE,horarios.evaluar(candidato("Mo-Fr 10:00-18:00","Geoapify (sin verificar)"),L,L.plusHours(1)));
  assertEquals(EstadoHorarioVisita.COMPATIBLE_SEGUN_FUENTE,horarios.evaluar(candidato("Mo-Fr 10:00-18:00","Geoapify (sin verificar)"),L.plusHours(1),L.plusHours(2)));
  assertEquals(EstadoHorarioVisita.INCIERTO,horarios.evaluar(candidato("Mo-Fr 10:00-18:00","Fuentes externas"),L,L.plusHours(1)));
  assertEquals(EstadoHorarioVisita.INCIERTO,horarios.evaluar(candidato("Mo-Fr 10:00-18:00; PH off","Geoapify (sin verificar)"),L,L.plusHours(1)));
 }
 @Test void detectaIncidencias() {
  BloqueItinerarioDto traslado = new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO,L.plusHours(2),L.plusHours(3),null,"a","b",null,null,null,false,null);
  DiaItinerarioDto dia = new DiaItinerarioDto(L.toLocalDate(),L,L.plusHours(8),List.of(visita(L),visita(L.plusMinutes(30)),traslado));
  PreferenciasDto prefs = new PreferenciasDto(List.of(),List.of(CategoriaTuristica.ARTE_MUSEOS),List.of(),null,null);
  ResultadoValidacionPlan resultado = new ValidadorPlanService().validar(List.of(dia),prefs);
  assertTrue(resultado.parcial());
  assertFalse(resultado.valido());
  for (String texto : List.of("solapados","duplicada","excluida","Traslado")) assertTrue(resultado.incidencias().stream().anyMatch(s -> s.contains(texto)),texto);
 }
 @Test void admiteDiaSinDisponibilidadJuntoADiaDisponible() {
  DiaItinerarioDto disponible = new DiaItinerarioDto(L.toLocalDate(), L, L.plusHours(8), List.of(visita(L)));
  DiaItinerarioDto noDisponible = new DiaItinerarioDto(L.toLocalDate().plusDays(1), null, null, List.of());
  ResultadoValidacionPlan resultado = new ValidadorPlanService().validar(List.of(disponible, noDisponible), null);
  assertTrue(resultado.valido());
  assertTrue(resultado.incidencias().isEmpty());
 }
 @Test void detectaFinAusenteYSolape() {
  BloqueItinerarioDto sinFin = new BloqueItinerarioDto(TipoBloqueItinerario.DESCANSO, L, null, null, null, null, null, null, null, false, null);
  DiaItinerarioDto dia = new DiaItinerarioDto(L.toLocalDate(), L, L.plusHours(8), List.of(visita(L), visita(L.plusMinutes(30)), sinFin));
  ResultadoValidacionPlan resultado = new ValidadorPlanService().validar(List.of(dia), null);
  assertFalse(resultado.valido());
  assertTrue(resultado.incidencias().stream().anyMatch(s -> s.contains("solapados")));
  assertTrue(resultado.incidencias().stream().anyMatch(s -> s.contains("Bloque o duración inválidos")));
 }
 @Test void validaDistanciaYContinuidadYAdmiteComidaGenerica() {
  BloqueItinerarioDto ruta = new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO, L.plusHours(1), L.plusHours(2), null, "otro", "destino", Double.NaN, 60.0, "fuente", false, null);
  DiaItinerarioDto dia = new DiaItinerarioDto(L.toLocalDate(), L, L.plusHours(8), List.of(visita(L), ruta));
  ResultadoValidacionPlan invalido = new ValidadorPlanService().validar(List.of(dia), null);
  assertFalse(invalido.valido());
  assertTrue(invalido.incidencias().stream().anyMatch(s -> s.contains("Traslado sin ruta")));
  assertTrue(invalido.incidencias().stream().anyMatch(s -> s.contains("Origen del traslado")));
  BloqueItinerarioDto comida = new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA, L.plusHours(1), L.plusHours(2), null, null, null, null, null, null, false, null);
  BloqueItinerarioDto rutaTrasComida = new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO, L.plusHours(2), L.plusHours(3), null, "otro", "destino", 100.0, 60.0, "fuente", false, null);
  DiaItinerarioDto conComida = new DiaItinerarioDto(L.toLocalDate(), L, L.plusHours(8), List.of(visita(L), comida, rutaTrasComida));
  assertTrue(new ValidadorPlanService().validar(List.of(conComida), null).valido());
 }
 @Test void detectaHorarioIncompatibleYRestauranteSinTraslado() {
  CandidatoEvaluadoDto cerrado = candidato("Mo 11:00-12:00", "Geoapify (sin verificar)");
  BloqueItinerarioDto visitaCerrada = new BloqueItinerarioDto(TipoBloqueItinerario.VISITA,
      L.plusHours(1), L.plusHours(2), cerrado, null, null, null, null, null, true, null);
  OpcionComidaDto restaurante = new OpcionComidaDto("restaurante", "Restaurante", 40.0, -1.0, null);
  BloqueItinerarioDto comida = new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA,
      L.plusHours(2), L.plusHours(3), null, null, null, null, null, null, true, null,
      restaurante, List.of());
  DiaItinerarioDto dia = new DiaItinerarioDto(L.toLocalDate(), L, L.plusHours(8),
      List.of(visitaCerrada, comida));
  ResultadoValidacionPlan resultado = new ValidadorPlanService().validar(List.of(dia), null);
  assertFalse(resultado.valido());
  assertTrue(resultado.incidencias().stream().anyMatch(s -> s.contains("horario de la fuente")));
  assertTrue(resultado.incidencias().stream().anyMatch(s -> s.contains("Falta traslado hasta el restaurante")));
 }
}
