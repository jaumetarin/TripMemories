package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.exception.ObligatoriosPendientesException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;
import static es.upv.pin.backend.model.CategoriaTuristica.*;

class PrioridadesItinerarioTests {
    private final ConfiguracionItinerarioService configuraciones = mock(ConfiguracionItinerarioService.class);
    private final EvaluacionCandidatosService evaluacion = mock(EvaluacionCandidatosService.class);
    private final DesplazamientoService rutas = mock(DesplazamientoService.class);
    private final GeneracionItinerarioService servicio = new GeneracionItinerarioService(configuraciones, evaluacion, rutas);

    private CandidatoEvaluadoDto candidato(String id, CategoriaTuristica categoria, int prioridad) {
        return candidato(id, categoria, prioridad, null, false, 0);
    }
    private CandidatoEvaluadoDto candidato(String id, CategoriaTuristica categoria, int prioridad, String horario, boolean documentado, int idiomas) {
        var lugar = new LugarDto(id, id, "Ciudad", 40.4, -3.7, List.of(categoria), List.of(), horario,
                TipoLugar.OTRO, documentado, idiomas);
        return new CandidatoEvaluadoDto(lugar, prioridad, 60, "Visita", "Motivo", CostLevel.UNKNOWN,
                null, List.of(), horario, horario == null ? null : "Geoapify / OpenStreetMap", "LOCAL",
                CostConfidence.UNKNOWN, ScheduleStatus.UNVERIFIED, null, null, null, null, null, null);
    }
    private ConfiguracionItinerarioDto viaje(LocalDateTime inicio, LocalDateTime fin, List<CategoriaTuristica> intereses,
            List<SeleccionLugarDto> selecciones) {
        var config = new ConfiguracionItinerarioDto(new DestinoDto("ciudad", "Ciudad", null, "España", 40.4, -3.7),
                new FechaHoraDto(inicio, fin, (int) java.time.temporal.ChronoUnit.DAYS.between(inicio.toLocalDate(), fin.toLocalDate()) + 1), "",
                new PreferenciasDto(intereses, List.of(), List.of(), NivelGasto.SIN_PREFERENCIA, 3.0),
                new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 240, 15, 0), selecciones);
        when(configuraciones.validar(config)).thenReturn(config);
        return config;
    }
    private ConfiguracionItinerarioDto viaje(int horas, List<CategoriaTuristica> intereses, SeleccionLugarDto... selecciones) {
        var inicio = LocalDateTime.of(2026, 5, 1, 10, 0);
        return viaje(inicio, inicio.plusHours(horas), intereses, List.of(selecciones));
    }
    private void escenario(
            ConfiguracionItinerarioDto config,
            List<CandidatoEvaluadoDto> candidatos) {

        when(evaluacion.evaluar(config)).thenReturn(
                new EvaluacionCandidatosDto(
                        candidatos, true, List.of(), 0, 0));

        List<DesplazamientoDto> tramos = new ArrayList<>();

        for (var candidato : candidatos) {
            tramos.add(new DesplazamientoDto(
                    "referencia:inicio",
                    candidato.lugar().id(),
                    0.0,
                    0.0,
                    EstadoDesplazamiento.DISPONIBLE,
                    "openrouteservice"));
        }

        for (var origen : candidatos) {
            for (var destino : candidatos) {
                if (origen == destino) {
                    continue;
                }

                tramos.add(new DesplazamientoDto(
                        origen.lugar().id(),
                        destino.lugar().id(),
                        0.0,
                        0.0,
                        EstadoDesplazamiento.DISPONIBLE,
                        "openrouteservice"));
            }
        }

        when(rutas.calcular(anyList())).thenReturn(tramos);
    }
    private List<BloqueItinerarioDto> visitas(ItinerarioGeneradoDto plan) {
        return plan.dias().stream().flatMap(d -> d.bloques().stream()).filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).toList();
    }

    @Test void equilibraCategoriasAunqueUnaTengaCienDeAfinidadYLasDemasDiez() {
        var config = viaje(3, List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES));
        var datos = List.of(candidato("museo1", ARTE_MUSEOS, 100), candidato("museo2", ARTE_MUSEOS, 100),
                candidato("monumento", HISTORIA_ARQUITECTURA, 10), candidato("parque", NATURALEZA_PARQUES, 10));
        escenario(config, datos);
        var elegidos = visitas(servicio.generar(config));
        assertEquals(3, elegidos.size());
        assertEquals(Set.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES),
                elegidos.stream().map(b -> b.candidato().lugar().categorias().getFirst()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void equilibraElViajeCompletoAlCambiarDeDiaEnVezDeReiniciarLasCategorias() {
        var inicio = LocalDateTime.of(2026, 5, 1, 16, 0);
        var config = viaje(inicio, inicio.plusDays(1).withHour(12), List.of(ARTE_MUSEOS, NATURALEZA_PARQUES), List.of());
        var datos = List.of(candidato("m1", ARTE_MUSEOS, 100), candidato("m2", ARTE_MUSEOS, 100),
                candidato("p1", NATURALEZA_PARQUES, 10), candidato("p2", NATURALEZA_PARQUES, 10));
        escenario(config, datos);
        var elegidos = visitas(servicio.generar(config));
        assertEquals(4, elegidos.size());
        assertEquals(2, elegidos.stream().filter(b -> b.candidato().lugar().categorias().contains(NATURALEZA_PARQUES)).count());
        assertEquals(2, elegidos.stream().filter(b -> b.candidato().lugar().categorias().contains(ARTE_MUSEOS)).count());
    }

    @Test void obligatoriosYPreferidosNoConsumenLosTurnosDeLasVisitasAutomaticas() {
        var a = candidato("obligatorio", ARTE_MUSEOS, 1);
        var b = candidato("preferido", ARTE_MUSEOS, 1);
        var inicio = LocalDateTime.of(2026, 5, 1, 10, 0);
        var config = viaje(inicio, inicio.withHour(18), List.of(ARTE_MUSEOS, NATURALEZA_PARQUES),
                List.of(new SeleccionLugarDto(a.lugar(), EstadoLugar.OBLIGATORIO), new SeleccionLugarDto(b.lugar(), EstadoLugar.PREFERIDO)));
        escenario(config, List.of(a, b, candidato("museo", ARTE_MUSEOS, 100), candidato("parque", NATURALEZA_PARQUES, 10)));
        var elegidos = visitas(servicio.generar(config));
        assertEquals(List.of("obligatorio", "preferido", "museo", "parque"), elegidos.stream().map(v -> v.candidato().lugar().id()).toList());
        assertTrue(elegidos.stream().allMatch(BloqueItinerarioDto::condicionada));
        assertTrue(elegidos.stream().allMatch(bloque -> bloque.candidato().horario() == null));
    }

    @Test void unReferenteDocumentadoSeAdelantaASuAlternativaDeLaMismaCategoria() {
        var config = viaje(1, List.of(NATURALEZA_PARQUES));
        escenario(config, List.of(candidato("a-jardin", NATURALEZA_PARQUES, 60, null, false, 0),
                candidato("z-parque-emblematico", NATURALEZA_PARQUES, 60, null, true, 7)));
        assertEquals("z-parque-emblematico", visitas(servicio.generar(config)).getFirst().candidato().lugar().id());
    }

    @Test void protegeLaAperturaLimitadaAunqueOtroObligatorioYaSePuedaVisitar() {
        var limitado = candidato("museo-limitado", ARTE_MUSEOS, 1, "Fr 11:00-12:00", false, 0);
        var flexible = candidato("plaza-flexible", HISTORIA_ARQUITECTURA, 100);
        var config = viaje(3, List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA),
                new SeleccionLugarDto(limitado.lugar(), EstadoLugar.OBLIGATORIO), new SeleccionLugarDto(flexible.lugar(), EstadoLugar.OBLIGATORIO));
        escenario(config, List.of(flexible, limitado));
        // Visitar la plaza primero impediría llegar a las 11:00; el orden inverso sí cabe.
        when(rutas.calcular(anyList())).thenReturn(List.of(
                new DesplazamientoDto(
                        "referencia:inicio", "museo-limitado",
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "referencia:inicio", "plaza-flexible",
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "plaza-flexible", "museo-limitado",
                        100.0, 600.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "museo-limitado", "plaza-flexible",
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice")));
        var plan = servicio.generar(config);
        assertEquals(List.of("museo-limitado", "plaza-flexible"), visitas(plan).stream().map(b -> b.candidato().lugar().id()).toList());
        assertEquals(LocalTime.of(11, 0), visitas(plan).getFirst().inicio().toLocalTime());
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test void unCierreConocidoImpideAceptarElPlanPeroNoSeInventaUnHorarioAlternativo() {
        var cerrado = candidato("Museo cerrado", ARTE_MUSEOS, 50, "Fr off", false, 0);
        var config = viaje(3, List.of(ARTE_MUSEOS), new SeleccionLugarDto(cerrado.lugar(), EstadoLugar.OBLIGATORIO));
        escenario(config, List.of(cerrado));
        var error = assertThrows(ObligatoriosPendientesException.class, () -> servicio.generar(config));
        assertTrue(error.getProblemas().getFirst().contains("Museo cerrado"));
        assertTrue(error.getProblemas().getFirst().contains("horario"));
        verifyNoInteractions(rutas);
    }

    @Test void sinCaminoEntreObligatoriosNoDevuelveUnItinerarioQueOmitaAlguno() {
        var a = candidato("Torre", HISTORIA_ARQUITECTURA, 80);
        var b = candidato("Museo", ARTE_MUSEOS, 80);
        var config = viaje(3, List.of(HISTORIA_ARQUITECTURA, ARTE_MUSEOS),
                new SeleccionLugarDto(a.lugar(), EstadoLugar.OBLIGATORIO), new SeleccionLugarDto(b.lugar(), EstadoLugar.OBLIGATORIO));
        escenario(config, List.of(a, b));
        when(rutas.calcular(anyList())).thenReturn(List.of(
                new DesplazamientoDto(
                        "referencia:inicio", a.lugar().id(),
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice"),
                new DesplazamientoDto(
                        "referencia:inicio", b.lugar().id(),
                        0.0, 0.0,
                        EstadoDesplazamiento.DISPONIBLE, "openrouteservice")));
        var error = assertThrows(ObligatoriosPendientesException.class, () -> servicio.generar(config));
        assertTrue(error.getProblemas().stream().anyMatch(p -> p.contains("trayectos")));
    }

    @Test void explicaLaFaltaDeVariedadCuandoSoloUnaCategoriaTieneVisitasFactibles() {
        var config = viaje(3, List.of(ARTE_MUSEOS, NATURALEZA_PARQUES));
        escenario(config, List.of(candidato("m1", ARTE_MUSEOS, 80), candidato("m2", ARTE_MUSEOS, 80), candidato("m3", ARTE_MUSEOS, 80)));
        var plan = servicio.generar(config);
        assertEquals(3, visitas(plan).size());
        assertTrue(plan.advertencias().stream().anyMatch(a -> a.contains("Naturaleza y parques")
                && a.contains("reparto se ha ajustado")));
    }

    @Test void reservaLaCategoriaQueAbreMasTardeSinRellenarTodosLosHuecosConMuseos() {
        var config = viaje(6, List.of(ARTE_MUSEOS, NATURALEZA_PARQUES));
        escenario(config, List.of(candidato("m1", ARTE_MUSEOS, 100), candidato("m2", ARTE_MUSEOS, 100),
                candidato("m3", ARTE_MUSEOS, 100), candidato("parque", NATURALEZA_PARQUES, 10, "Fr 15:00-16:00", false, 0)));
        var plan = servicio.generar(config);
        assertEquals(List.of("m1", "parque"), visitas(plan).stream().map(b -> b.candidato().lugar().id()).toList());
        assertEquals(LocalTime.of(15, 0), visitas(plan).getLast().inicio().toLocalTime());
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test @SuppressWarnings("unchecked") void laMuestraDeRutasConservaTodasLasCategoriasAntesDelLimiteDeCincuenta() {
        var config = viaje(3, List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, CULTURA_LOCAL, NATURALEZA_PARQUES));
        List<CandidatoEvaluadoDto> candidatos = new ArrayList<>();
        for (var categoria : config.preferencias().intereses()) for (int i = 0; i < 70; i++)
            candidatos.add(candidato(categoria.name() + i, categoria, categoria == ARTE_MUSEOS ? 100 : 10));
        escenario(config, candidatos);
        servicio.generar(config);
        org.mockito.ArgumentCaptor<List<PuntoRutaDto>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rutas).calcular(captor.capture());
        assertEquals(51, captor.getValue().size());
        for (var categoria : config.preferencias().intereses()) {
            long cantidad = captor.getValue().stream().filter(p -> p.id().startsWith(categoria.name())).count();
            assertTrue(cantidad >= 12 && cantidad <= 13, categoria + ": " + cantidad);
        }
    }

    @Test void unaCategoriaEscasaNoDejaLosCuatroUltimosDiasVacios() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var intereses = List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES, CULTURA_LOCAL, OCIO_ENTRETENIMIENTO);
        var config = viaje(inicio, inicio.plusDays(6).withHour(18), intereses, List.of());
        List<CandidatoEvaluadoDto> datos = new ArrayList<>();
        for (var categoria : intereses) for (int i = 0; i < (categoria == CULTURA_LOCAL ? 2 : 9); i++)
            datos.add(candidato(categoria.name() + i, categoria, 80));
        escenario(config, datos);

        var plan = servicio.generar(config);

        assertEquals(38, visitas(plan).size());
        assertTrue(plan.dias().stream().allMatch(d -> d.bloques().stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA)));
        assertEquals(38, visitas(plan).stream().map(b -> b.candidato().lugar().id()).distinct().count());
        for (var categoria : intereses) assertEquals(categoria == CULTURA_LOCAL ? 2 : 9,
                visitas(plan).stream().filter(b -> b.candidato().lugar().categorias().contains(categoria)).count());
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
        verify(evaluacion, times(1)).evaluar(config);
        verify(rutas, times(1)).calcular(anyList());
    }

    @Test void sieteVisitasFlexiblesSeRepartenEntreLosSieteDias() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var config = viaje(inicio, inicio.plusDays(6).withHour(18), List.of(ARTE_MUSEOS, NATURALEZA_PARQUES), List.of());
        List<CandidatoEvaluadoDto> datos = new ArrayList<>();
        for (int i = 0; i < 7; i++) datos.add(candidato("Museo " + i, ARTE_MUSEOS, 80));
        escenario(config, datos);
        var plan = servicio.generar(config);
        assertEquals(7, visitas(plan).size());
        for (var dia : plan.dias()) assertEquals(1, dia.bloques().stream().filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).count());
    }

    @Test void unUltimoDiaCortoRecibeUnaVisitaQueCabeEnSuVentana() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var config = viaje(inicio, inicio.plusDays(2).withHour(11), List.of(ARTE_MUSEOS, NATURALEZA_PARQUES), List.of());
        var datos = List.of(candidato("Museo Alfa", ARTE_MUSEOS, 80), candidato("Museo Beta", ARTE_MUSEOS, 80),
                candidato("Parque Central", NATURALEZA_PARQUES, 80), candidato("Parque Norte", NATURALEZA_PARQUES, 80));
        escenario(config, datos);
        var plan = servicio.generar(config);
        assertEquals(4, visitas(plan).size());
        assertTrue(plan.dias().stream().allMatch(d -> d.bloques().stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA)));
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test void noAplazaUnaVisitaQueSoloAbreElPrimerDia() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var config = viaje(inicio, inicio.plusDays(2).withHour(18), List.of(ARTE_MUSEOS), List.of());
        List<CandidatoEvaluadoDto> datos = new ArrayList<>();
        for (int i = 0; i < 4; i++) datos.add(candidato("z-exposicion-" + i, ARTE_MUSEOS, 10,
                "Mo-Th off; Fr 10:00-18:00; Sa-Su off", false, 0));
        for (int i = 0; i < 4; i++) datos.add(candidato("a-flexible-" + i, ARTE_MUSEOS, 100));
        escenario(config, datos);
        var plan = servicio.generar(config);
        assertEquals(8, visitas(plan).size());
        assertEquals(4, plan.dias().getFirst().bloques().stream().filter(b -> b.tipo() == TipoBloqueItinerario.VISITA
                && b.candidato().lugar().id().startsWith("z-exposicion-")).count());
        assertTrue(plan.dias().stream().allMatch(d -> d.bloques().stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA)));
    }

    @Test void unaCategoriaSinRutaNoBloqueaLasVisitasAlcanzables() {
        var config = viaje(3, List.of(ARTE_MUSEOS, NATURALEZA_PARQUES));
        var datos = List.of(candidato("m1", ARTE_MUSEOS, 80), candidato("m2", ARTE_MUSEOS, 80),
                candidato("m3", ARTE_MUSEOS, 80), candidato("parque", NATURALEZA_PARQUES, 80));
        escenario(config, datos);
        List<DesplazamientoDto> tramos = new ArrayList<>();
        for (var origen : List.of("referencia:inicio", "m1", "m2", "m3"))
            for (var destino : List.of("m1", "m2", "m3")) if (!origen.equals(destino))
                tramos.add(new DesplazamientoDto(origen, destino, 0.0, 0.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice"));
        when(rutas.calcular(anyList())).thenReturn(tramos);
        var plan = servicio.generar(config);
        assertEquals(3, visitas(plan).size());
        assertTrue(visitas(plan).stream().noneMatch(b -> b.candidato().lugar().id().equals("parque")));
        assertTrue(plan.parcial());
    }

    @Test void conservaObligatoriosYPreferidosYRechazaEvitadosDuranteElReparto() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var obligatorio = candidato("Obligatorio", HISTORIA_ARQUITECTURA, 1);
        var preferido = candidato("Preferido", ARTE_MUSEOS, 1);
        var evitado = candidato("Evitado", NATURALEZA_PARQUES, 100);
        var config = viaje(inicio, inicio.plusDays(2).withHour(18), List.of(HISTORIA_ARQUITECTURA, ARTE_MUSEOS, NATURALEZA_PARQUES),
                List.of(new SeleccionLugarDto(obligatorio.lugar(), EstadoLugar.OBLIGATORIO),
                        new SeleccionLugarDto(preferido.lugar(), EstadoLugar.PREFERIDO),
                        new SeleccionLugarDto(evitado.lugar(), EstadoLugar.EVITADO)));
        List<CandidatoEvaluadoDto> datos = new ArrayList<>(List.of(obligatorio, preferido, evitado));
        for (var categoria : config.preferencias().intereses()) for (int i = 0; i < 3; i++)
            datos.add(candidato(categoria.name() + i, categoria, 90));
        escenario(config, datos);
        var plan = servicio.generar(config);
        var ids = visitas(plan).stream().map(b -> b.candidato().lugar().id()).toList();
        assertEquals(11, ids.size());
        assertTrue(ids.containsAll(List.of("Obligatorio", "Preferido")));
        assertFalse(ids.contains("Evitado"));
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertTrue(plan.dias().stream().allMatch(d -> d.bloques().stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA)));
    }

    @Test void mantieneLaProporcionCuandoTodasLasCategoriasTienenAlternativas() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var intereses = List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES, CULTURA_LOCAL, OCIO_ENTRETENIMIENTO);
        var config = viaje(inicio, inicio.plusDays(6).withHour(18), intereses, List.of());
        List<CandidatoEvaluadoDto> datos = new ArrayList<>();
        for (var categoria : intereses) for (int i = 0; i < 10; i++)
            datos.add(candidato(categoria.name() + i, categoria, categoria == ARTE_MUSEOS ? 100 : 10));
        escenario(config, datos);
        var plan = servicio.generar(config);
        var cantidades = intereses.stream().mapToLong(c -> visitas(plan).stream()
                .filter(b -> b.candidato().lugar().categorias().contains(c)).count()).summaryStatistics();
        assertTrue(cantidades.getMax() - cantidades.getMin() <= 1);
        assertTrue(plan.dias().stream().allMatch(d -> d.bloques().stream().anyMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA)));
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test void siNoHayVisitasPosiblesExplicaLosDiasVaciosSinInventarActividades() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var config = viaje(inicio, inicio.plusDays(2).withHour(18), List.of(ARTE_MUSEOS), List.of());
        escenario(config, List.of(candidato("Museo cerrado", ARTE_MUSEOS, 80, "Mo-Su off", false, 0)));
        var plan = servicio.generar(config);
        assertTrue(visitas(plan).isEmpty());
        assertTrue(plan.parcial());
        assertEquals(3, plan.advertencias().stream().filter(a -> a.contains("no tiene visitas")).count());
    }

    @Test void reparteTambienLosObligatoriosFlexiblesEntreLosSieteDias() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        List<CandidatoEvaluadoDto> datos = new ArrayList<>();
        for (int i = 0; i < 7; i++) datos.add(candidato("Obligatorio " + i, HISTORIA_ARQUITECTURA, 80));
        var config = viaje(inicio, inicio.plusDays(6).withHour(18), List.of(HISTORIA_ARQUITECTURA),
                datos.stream().map(c -> new SeleccionLugarDto(c.lugar(), EstadoLugar.OBLIGATORIO)).toList());
        escenario(config, datos);
        var plan = servicio.generar(config);
        assertEquals(7, visitas(plan).size());
        for (var dia : plan.dias()) assertEquals(1, dia.bloques().stream().filter(b -> b.tipo() == TipoBloqueItinerario.VISITA).count());
    }

    @Test void recolocaReservasParaNoConsumirLaUnicaVisitaDelSegundoDia() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var a = candidato("A", ARTE_MUSEOS, 100, "Fr-Sa 10:00-11:00; Su off", false, 0);
        var b = candidato("B", ARTE_MUSEOS, 80, "Fr-Sa off; Su 10:00-11:00", false, 0);
        var c = candidato("C", ARTE_MUSEOS, 10, "Fr 10:00-11:00; Sa off; Su 10:00-11:00", false, 0);
        var datos = List.of(a, b, c);
        var config = viaje(inicio, inicio.plusDays(2).withHour(18), List.of(ARTE_MUSEOS),
                datos.stream().map(v -> new SeleccionLugarDto(v.lugar(), EstadoLugar.OBLIGATORIO)).toList());
        escenario(config, datos);
        var plan = servicio.generar(config);
        assertEquals(List.of("C", "A", "B"), visitas(plan).stream().map(v -> v.candidato().lugar().id()).toList());
        for (var dia : plan.dias()) assertEquals(1, dia.bloques().stream().filter(v -> v.tipo() == TipoBloqueItinerario.VISITA).count());
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }

    @Test void lasExclusionesDeBarcelonaSiguenFueraAunqueFaltenCategorias() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var intereses = List.of(ARTE_MUSEOS, HISTORIA_ARQUITECTURA, NATURALEZA_PARQUES, CULTURA_LOCAL, OCIO_ENTRETENIMIENTO);
        var base = viaje(inicio, inicio.plusDays(6).withHour(18), intereses, List.of());
        var exclusiones = List.of(SubcategoriaTuristica.GALERIAS_ARTE, SubcategoriaTuristica.CASTILLOS_PALACIOS,
                SubcategoriaTuristica.PLAYAS, SubcategoriaTuristica.SENDERISMO, SubcategoriaTuristica.TALLERES_ARTESANALES,
                SubcategoriaTuristica.MERCADOS_TRADICIONALES, SubcategoriaTuristica.ACTIVIDADES_RECREATIVAS,
                SubcategoriaTuristica.COMPRAS, SubcategoriaTuristica.PARQUES_ATRACCIONES);
        var config = new ConfiguracionItinerarioDto(base.destino(), base.disponibilidad(), "",
                new PreferenciasDto(intereses, List.of(), exclusiones, NivelGasto.SIN_PREFERENCIA, 3.0), base.ritmo(), List.of());
        when(configuraciones.validar(config)).thenReturn(config);
        List<CandidatoEvaluadoDto> datos = new ArrayList<>();
        for (int i = 0; i < 7; i++) datos.add(candidato("Visita compatible " + i, HISTORIA_ARQUITECTURA, 80));
        for (var exclusion : exclusiones) {
            var lugar = new LugarDto(exclusion.name(), exclusion.name(), "Ciudad", 40.4, -3.7,
                    List.of(exclusion.getCategoria()), List.of(exclusion), null);
            datos.add(new CandidatoEvaluadoDto(lugar, 100, 60, "Visita", "Motivo", CostLevel.UNKNOWN,
                    null, List.of(), null, null, "LOCAL", CostConfidence.UNKNOWN, ScheduleStatus.UNVERIFIED,
                    null, null, null, null, null, null));
        }
        escenario(config, datos);
        var plan = servicio.generar(config);
        assertEquals(7, visitas(plan).size());
        assertTrue(visitas(plan).stream().allMatch(v -> v.candidato().lugar().subcategorias().isEmpty()));
        assertTrue(plan.dias().stream().allMatch(d -> d.bloques().stream().anyMatch(v -> v.tipo() == TipoBloqueItinerario.VISITA)));
    }

    @Test void reservaParaElUltimoDiaUnaVisitaQueCabeIncluyendoElTraslado() {
        var inicio = LocalDateTime.of(2027, 1, 1, 10, 0);
        var config = viaje(inicio, inicio.plusDays(2).withHour(11), List.of(ARTE_MUSEOS), List.of());
        var base = candidato("corto", ARTE_MUSEOS, 100);
        var corto = new CandidatoEvaluadoDto(base.lugar(), 100, 30, "Visita", "Motivo", CostLevel.UNKNOWN,
                null, List.of(), null, null, "LOCAL", CostConfidence.UNKNOWN, ScheduleStatus.UNVERIFIED,
                null, null, null, null, null, null);
        escenario(config, List.of(candidato("m1", ARTE_MUSEOS, 80), candidato("m2", ARTE_MUSEOS, 80), corto));
        List<DesplazamientoDto> tramos = new ArrayList<>();
        var puntos = List.of("referencia:inicio", "m1", "m2", "corto");
        for (var origen : puntos) for (var destino : puntos) if (!origen.equals(destino))
            tramos.add(new DesplazamientoDto(origen, destino, 500.0, 600.0, EstadoDesplazamiento.DISPONIBLE, "openrouteservice"));
        when(rutas.calcular(anyList())).thenReturn(tramos);
        var plan = servicio.generar(config);
        assertEquals(3, visitas(plan).size());
        assertTrue(plan.dias().stream().allMatch(d -> d.bloques().stream().anyMatch(v -> v.tipo() == TipoBloqueItinerario.VISITA)));
        var ultima = plan.dias().getLast().bloques().stream().filter(v -> v.tipo() == TipoBloqueItinerario.VISITA).findFirst().orElseThrow();
        assertEquals("corto", ultima.candidato().lugar().id());
        assertTrue(new ValidadorPlanService().validar(plan.dias(), config.preferencias()).valido());
    }
}
