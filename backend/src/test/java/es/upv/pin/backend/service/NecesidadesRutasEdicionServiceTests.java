package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.service.NecesidadesRutasEdicionService.Solicitud;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NecesidadesRutasEdicionServiceTests {
    private static final String INICIO = "referencia:inicio";
    private final LocalDate fecha = LocalDate.of(2026, 12, 7);
    private final RitmoDiarioDto ritmo = new RitmoDiarioDto(
            LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10);
    private final NecesidadesRutasEdicionService servicio =
            new NecesidadesRutasEdicionService(new FranjasEdicionService());
    private final Visita a = visita("a", 39.48, -0.38);
    private final Visita b = visita("b", 39.49, -0.39);

    @Test
    void sinRestauranteSoloSolicitaLaSecuenciaDeVisitas() {
        var resultado = servicio.obtener(configuracion(null), dia("10:00", "18:00", false), List.of(a, b));

        assertEquals(Set.of(new Par(INICIO, "a"), new Par("a", "b")), pares(resultado));
        assertEquals(2, resultado.size());
        assertFalse(pares(resultado).contains(new Par("b", "a")));
        assertFalse(pares(resultado).contains(new Par("b", INICIO)));
    }

    @Test
    void conRestauranteIncluyeAccesoALaComidaYPosiblesSalidasPorLaTarde() {
        var resultado = servicio.obtener(configuracion(null), dia("10:00", "18:00", true), List.of(a, b));

        assertEquals(Set.of(new Par(INICIO, "a"), new Par("a", "b"),
                new Par(INICIO, "restaurante"), new Par("a", "restaurante"),
                new Par("b", "restaurante"), new Par("restaurante", "a"),
                new Par("restaurante", "b")), pares(resultado));
        assertEquals(7, resultado.size());
        var haciaComida = buscar(resultado, "a", "restaurante");
        assertEquals(new PuntoRutaDto("a", 39.48, -0.38), haciaComida.origen());
        assertEquals(new PuntoRutaDto("restaurante", 39.50, -0.40), haciaComida.destino());
    }

    @Test
    void sinFranjaDeTardeNoSolicitaSalidasDelRestaurante() {
        var resultado = servicio.obtener(configuracion(null), dia("10:00", "14:00", true), List.of(a, b));

        assertEquals(Set.of(new Par(INICIO, "a"), new Par("a", "b"),
                new Par(INICIO, "restaurante"), new Par("a", "restaurante"),
                new Par("b", "restaurante")), pares(resultado));
        assertTrue(resultado.stream().noneMatch(s -> s.origen().id().equals("restaurante")));
    }

    @Test
    void sinFranjaDeMananaNoSolicitaLlegadasAlRestaurante() {
        var resultado = servicio.obtener(configuracion(null), dia("14:00", "18:00", true), List.of(a, b));

        assertEquals(Set.of(new Par(INICIO, "a"), new Par("a", "b"),
                new Par("restaurante", "a"), new Par("restaurante", "b")), pares(resultado));
        assertTrue(resultado.stream().noneMatch(s -> s.destino().id().equals("restaurante")));
    }

    @Test
    void sinVisitasMantieneLaRutaAlRestauranteSiHayTiempoAntesDeComer() {
        var resultado = servicio.obtener(configuracion(null), dia("10:00", "18:00", true), List.of());

        assertEquals(Set.of(new Par(INICIO, "restaurante")), pares(resultado));
        assertEquals(1, resultado.size());
    }

    @Test
    void sinVisitasNiRestauranteNoSolicitaRutas() {
        assertTrue(servicio.obtener(configuracion(null), dia("10:00", "18:00", false), List.of()).isEmpty());
    }

    @Test
    void utilizaLasCoordenadasDelAlojamientoLocalizado() {
        var hotel = new AlojamientoDto("hotel", "Hotel", "Dirección", 39.60, -0.60);
        var resultado = servicio.obtener(configuracion(hotel), dia("10:00", "18:00", true), List.of(a));

        assertEquals(new PuntoRutaDto(INICIO, 39.60, -0.60), buscar(resultado, INICIO, "a").origen());
        assertEquals(new PuntoRutaDto(INICIO, 39.60, -0.60), buscar(resultado, INICIO, "restaurante").origen());
        assertEquals(new PuntoRutaDto("a", 39.48, -0.38), buscar(resultado, INICIO, "a").destino());
    }

    @Test
    void utilizaElDestinoComoReferenciaSiNoHayAlojamiento() {
        var resultado = servicio.obtener(configuracion(null), dia("10:00", "18:00", false), List.of(a));
        assertEquals(new PuntoRutaDto(INICIO, 39.47, -0.37), resultado.getFirst().origen());
    }

    @Test
    void diaSinDisponibilidadNoSolicitaRutasAunqueQuedenVisitas() {
        var dia = new DiaItinerarioDto(fecha, null, null, List.of());
        assertTrue(servicio.obtener(configuracion(null), dia, List.of(a, b)).isEmpty());
    }

    @Test
    void noDuplicaUnParYConservaLaDireccionDelRecorrido() {
        var resultado = servicio.obtener(configuracion(null), dia("10:00", "18:00", true), List.of(a, b, a, b));

        assertEquals(resultado.size(), pares(resultado).size());
        assertTrue(pares(resultado).contains(new Par("a", "b")));
        assertTrue(pares(resultado).contains(new Par("b", "a")));
        assertTrue(pares(resultado).contains(new Par("a", "restaurante")));
        assertTrue(pares(resultado).contains(new Par("restaurante", "a")));
    }

    @Test
    void noSolicitaTrayectosDesdeUnPuntoHastaSiMismo() {
        var resultado = servicio.obtener(configuracion(null), dia("10:00", "18:00", true),
                List.of(visita("restaurante", 39.50, -0.40)));

        assertEquals(Set.of(new Par(INICIO, "restaurante")), pares(resultado));
        assertEquals(1, resultado.size());
    }

    @Test
    void conservaLasEntradasYDevuelveUnaListaInmutable() {
        var config = configuracion(null);
        var dia = dia("10:00", "18:00", true);
        var visitas = new ArrayList<>(List.of(a, b));
        var resultado = servicio.obtener(config, dia, visitas);
        var esperado = List.copyOf(resultado);

        assertEquals(List.of(a, b), visitas);
        assertEquals(dia("10:00", "18:00", true), dia);
        assertEquals(configuracion(null), config);
        assertThrows(UnsupportedOperationException.class, () -> resultado.add(resultado.getFirst()));
        visitas.clear();
        assertEquals(esperado, resultado);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 50})
    void elNumeroDeParesCreceLinealmenteConLasVisitas(int cantidad) {
        var visitas = new ArrayList<Visita>();
        for (int i = 0; i < cantidad; i++) visitas.add(visita("visita-" + i, 39.48, -0.38));

        var resultado = servicio.obtener(configuracion(null), dia("10:00", "18:00", true), visitas);

        assertEquals(3 * cantidad + 1, resultado.size());
        assertEquals(resultado.size(), pares(resultado).size());
    }

    private static Set<Par> pares(List<Solicitud> solicitudes) {
        var resultado = new HashSet<Par>();
        for (var solicitud : solicitudes) {
            resultado.add(new Par(solicitud.origen().id(), solicitud.destino().id()));
        }
        return resultado;
    }

    private static Solicitud buscar(List<Solicitud> solicitudes, String origen, String destino) {
        return solicitudes.stream().filter(s -> s.origen().id().equals(origen)
                && s.destino().id().equals(destino)).findFirst().orElseThrow();
    }

    private ConfiguracionItinerarioDto configuracion(AlojamientoDto alojamiento) {
        return new ConfiguracionItinerarioDto(
                new DestinoDto("valencia", "Valencia", "Valencia", "España", 39.47, -0.37),
                new FechaHoraDto(fecha.atTime(10, 0), fecha.atTime(18, 0), 1),
                alojamiento == null ? "" : alojamiento.direccion(),
                new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS),
                        List.of(), List.of(), NivelGasto.MODERADO, 3.0),
                ritmo, alojamiento);
    }

    private DiaItinerarioDto dia(String inicio, String fin, boolean restaurante) {
        var opcion = restaurante
                ? new OpcionComidaDto("restaurante", "Comida", 39.50, -0.40, "Valencia") : null;
        var comida = new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA,
                fecha.atTime(13, 0), fecha.atTime(15, 0), null,
                null, null, null, null, null, restaurante, "Comida", opcion, List.of());
        return new DiaItinerarioDto(fecha, fecha.atTime(LocalTime.parse(inicio)),
                fecha.atTime(LocalTime.parse(fin)), List.of(comida));
    }

    private static Visita visita(String id, double latitud, double longitud) {
        var lugar = new LugarDto(id, "Museo " + id, "Valencia", latitud, longitud,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), "24/7");
        return new Visita(UUID.randomUUID(), new CandidatoEvaluadoDto(lugar, 80, 60,
                "Arte", "Prueba", CostLevel.FREE, null, List.of(), "24/7", "Geoapify Places",
                "LOCAL", CostConfidence.CONFIRMED, ScheduleStatus.VERIFIED,
                0.0, 0.0, "EUR", null, null, 80));
    }

    private record Par(String origen, String destino) {}
}
