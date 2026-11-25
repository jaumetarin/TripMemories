package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Codigo;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.Nivel;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ColocacionVisitaServiceTests {
    private final ColocacionVisitaService servicio =
            new ColocacionVisitaService(new EvaluadorHorarioService());
    private final LocalDate fecha = LocalDate.of(2026, 12, 7); // Lunes.
    private final LocalDateTime inicio = fecha.atTime(10, 0);
    private final RitmoDiarioDto ritmo = new RitmoDiarioDto(
            LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10);

    @Test
    void redondeaElTrasladoHaciaArribaYAnadeMargenYVisita() {
        var visita = visita("Mo 10:00-18:00");
        var resultado = servicio.colocar(visita, "origen", ruta(61),
                inicio, fecha.atTime(13, 0), 0, ritmo);

        assertEquals(List.of(TipoBloqueItinerario.TRASLADO,
                TipoBloqueItinerario.MARGEN, TipoBloqueItinerario.VISITA), tipos(resultado));
        assertEquals(inicio.plusMinutes(2), resultado.bloques().getFirst().fin());
        assertEquals(inicio.plusMinutes(12), resultado.bloques().getLast().inicio());
        assertEquals(inicio.plusMinutes(72), resultado.fin());
        assertEquals(62, resultado.minutosActividad());
        assertTrue(resultado.incidencias().isEmpty());
        assertSame(visita.candidato(), resultado.bloques().getLast().candidato());
    }

    @Test
    void anadeDescansoAlAlcanzarElUmbralYReiniciaElContador() {
        var resultado = servicio.colocar(visita("Mo 10:00-18:00"), "origen", ruta(61),
                inicio, fecha.atTime(13, 0), 120, ritmo);

        assertEquals(TipoBloqueItinerario.DESCANSO, resultado.bloques().getFirst().tipo());
        assertEquals(inicio.plusMinutes(15), resultado.bloques().getFirst().fin());
        assertEquals(inicio.plusMinutes(87), resultado.fin());
        assertEquals(62, resultado.minutosActividad());
    }

    @Test
    void esperaHastaUnaAperturaPosteriorSinCambiarLaVisita() {
        var resultado = servicio.colocar(visita("Mo 11:30-18:00"), "origen", ruta(61),
                inicio, fecha.atTime(13, 0), 0, ritmo);

        assertEquals(List.of(TipoBloqueItinerario.TRASLADO, TipoBloqueItinerario.MARGEN,
                TipoBloqueItinerario.TIEMPO_LIBRE, TipoBloqueItinerario.VISITA), tipos(resultado));
        var espera = resultado.bloques().get(2);
        assertEquals(inicio.plusMinutes(12), espera.inicio());
        assertEquals(fecha.atTime(11, 30), espera.fin());
        assertEquals(fecha.atTime(12, 30), resultado.fin());
        assertEquals(62, resultado.minutosActividad());
        assertTrue(resultado.incidencias().isEmpty());
    }

    @Test
    void unCierreIncompatibleSeMuestraComoConflictoConLaVisitaOriginal() {
        var visita = visita("Mo off");
        var resultado = servicio.colocar(visita, "origen", ruta(61),
                inicio, fecha.atTime(13, 0), 0, ritmo);

        assertEquals(inicio.plusMinutes(12), resultado.bloques().getLast().inicio());
        assertEquals(inicio.plusMinutes(72), resultado.fin());
        assertEquals(1, resultado.incidencias().size());
        var incidencia = resultado.incidencias().getFirst();
        assertEquals(Nivel.CONFLICTO, incidencia.nivel());
        assertEquals(Codigo.HORARIO_INCOMPATIBLE, incidencia.codigo());
        assertEquals(visita.actividadId(), incidencia.actividadId());
        assertEquals("museo", incidencia.lugarId());
        assertEquals(fecha, incidencia.dia());
        assertFalse(tipos(resultado).contains(TipoBloqueItinerario.TIEMPO_LIBRE));
    }

    @Test
    void unHorarioDesconocidoEsUnAvisoYNoUnaAperturaConfirmada() {
        var resultado = servicio.colocar(visita(null), "origen", ruta(61),
                inicio, fecha.atTime(13, 0), 0, ritmo);

        assertTrue(resultado.bloques().getLast().condicionada());
        assertEquals(Nivel.AVISO, resultado.incidencias().getFirst().nivel());
        assertEquals(Codigo.HORARIO_NO_CONFIRMADO, resultado.incidencias().getFirst().codigo());
    }

    @Test
    void noDejaDescansoTrasladoNiMargenParcialesSiNoCabeLaVisita() {
        var resultado = servicio.colocar(visita("Mo 10:00-18:00"), "origen", ruta(61),
                inicio, inicio.plusMinutes(30), 120, ritmo);

        assertTrue(resultado.bloques().isEmpty());
        assertEquals(inicio, resultado.fin());
        assertEquals(120, resultado.minutosActividad());
        assertEquals(Codigo.DISPONIBILIDAD_EXCEDIDA, resultado.incidencias().getFirst().codigo());
    }

    @Test
    void permiteTerminarExactamenteEnElLimite() {
        var limite = inicio.plusMinutes(72);
        var resultado = servicio.colocar(visita("Mo 10:00-18:00"), "origen", ruta(61),
                inicio, limite, 0, ritmo);

        assertEquals(limite, resultado.fin());
        assertTrue(resultado.incidencias().isEmpty());
        assertEquals(3, resultado.bloques().size());
    }

    @Test
    void rutaDeTiempoCeroNoCreaUnTrasladoVacio() {
        var sinMargen = new RitmoDiarioDto(ritmo.comidaInicio(), ritmo.comidaFin(), 120, 15, 0);
        var resultado = servicio.colocar(visita("Mo 10:00-18:00"), "origen", ruta(0),
                inicio, fecha.atTime(13, 0), 0, sinMargen);

        assertEquals(List.of(TipoBloqueItinerario.VISITA), tipos(resultado));
        assertEquals(inicio.plusHours(1), resultado.fin());
    }

    @ParameterizedTest
    @MethodSource("rutasInvalidas")
    void noInventaUnTrayectoCuandoLaRutaFaltaOEsInvalida(DesplazamientoDto ruta) {
        var resultado = servicio.colocar(visita("Mo 10:00-18:00"), "origen", ruta,
                inicio, fecha.atTime(13, 0), 35, ritmo);

        assertTrue(resultado.bloques().isEmpty());
        assertEquals(inicio, resultado.fin());
        assertEquals(35, resultado.minutosActividad());
        assertEquals(Nivel.CONFLICTO, resultado.incidencias().getFirst().nivel());
        assertEquals(Codigo.RUTA_NO_DISPONIBLE, resultado.incidencias().getFirst().codigo());
    }

    private static Stream<DesplazamientoDto> rutasInvalidas() {
        return Stream.of(null,
                new DesplazamientoDto("origen", "museo", null, null,
                        EstadoDesplazamiento.RUTA_NO_DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("otro", "museo", 100.0, 60.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("origen", "otro", 100.0, 60.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("origen", "museo", Double.NaN, 60.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("origen", "museo", 100.0, Double.POSITIVE_INFINITY,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("origen", "museo", 100.0, -1.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("origen", "museo", -1.0, 60.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto("origen", "museo", 100.0, 60.0,
                        EstadoDesplazamiento.DISPONIBLE, " "));
    }

    private static List<TipoBloqueItinerario> tipos(ColocacionVisitaService.Resultado resultado) {
        return resultado.bloques().stream().map(BloqueItinerarioDto::tipo).toList();
    }

    private static DesplazamientoDto ruta(double segundos) {
        return new DesplazamientoDto("origen", "museo", 100.0, segundos,
                EstadoDesplazamiento.DISPONIBLE, "openrouteservice");
    }

    private static Visita visita(String horario) {
        var lugar = new LugarDto("museo", "Museo", "Valencia", 39.47, -0.37,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), horario);
        var candidato = new CandidatoEvaluadoDto(lugar, 80, 60, "Arte", "Prueba",
                CostLevel.FREE, null, List.of(), horario, horario == null ? null : "Geoapify Places",
                "LOCAL", CostConfidence.CONFIRMED, ScheduleStatus.VERIFIED,
                0.0, 0.0, "EUR", null, null, 80);
        return new Visita(UUID.randomUUID(), candidato);
    }
}
