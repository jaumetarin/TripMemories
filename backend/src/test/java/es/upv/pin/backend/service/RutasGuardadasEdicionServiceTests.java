package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.service.NecesidadesRutasEdicionService.Solicitud;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RutasGuardadasEdicionServiceTests {
    private static final LocalDate FECHA = LocalDate.of(2026, 12, 7);
    private static final PuntoRutaDto INICIO = new PuntoRutaDto("referencia:inicio", 39.47, -0.37);
    private static final PuntoRutaDto A = new PuntoRutaDto("a", 39.48, -0.38);
    private static final PuntoRutaDto B = new PuntoRutaDto("b", 39.49, -0.39);
    private static final PuntoRutaDto RESTAURANTE = new PuntoRutaDto("restaurante", 39.50, -0.40);
    private final RutasGuardadasEdicionService servicio = new RutasGuardadasEdicionService();

    @Test
    void recuperaLosValoresExactosDeRutasConExtremosConocidos() {
        var viaje = viaje(null, bloques());
        var resultado = servicio.recuperar(viaje, List.of(new Solicitud(INICIO, A),
                new Solicitud(A, B), new Solicitud(B, RESTAURANTE)));

        assertEquals(List.of(dto(INICIO, A, 100.0, 60.0), dto(A, B, 200.0, 120.0),
                dto(B, RESTAURANTE, 300.0, 180.0)), resultado);
        assertEquals(bloques(), viaje.itinerario().dias().getFirst().bloques());
        assertThrows(UnsupportedOperationException.class, () -> resultado.add(resultado.getFirst()));
    }

    @Test
    void noAprovechaElSentidoContrarioAunqueConozcaAmbosPuntos() {
        assertTrue(servicio.recuperar(viaje(null, bloques()),
                List.of(new Solicitud(B, A))).isEmpty());
    }

    @Test
    void cambiarCoordenadasDeCualquieraDeLosExtremosImpideReutilizar() {
        var viaje = viaje(null, bloques());
        assertTrue(servicio.recuperar(viaje, List.of(
                new Solicitud(new PuntoRutaDto(INICIO.id(), 40.0, INICIO.longitud()), A),
                new Solicitud(INICIO, new PuntoRutaDto(A.id(), A.latitud(), -0.9)))).isEmpty());
    }

    @Test
    void cambiarElIdentificadorConservandoCoordenadasNoEsLaMismaRuta() {
        var desconocido = new PuntoRutaDto("otro", A.latitud(), A.longitud());
        assertTrue(servicio.recuperar(viaje(null, bloques()),
                List.of(new Solicitud(INICIO, desconocido))).isEmpty());
    }

    @Test
    void conAlojamientoComparaConSusCoordenadasYNoConLasDelDestino() {
        var hotel = new AlojamientoDto("hotel", "Hotel", "Valencia", 39.60, -0.60);
        var viaje = viaje(hotel, bloques());
        var referenciaHotel = new PuntoRutaDto(INICIO.id(), hotel.latitud(), hotel.longitud());

        assertEquals(List.of(dto(INICIO, A, 100.0, 60.0)),
                servicio.recuperar(viaje, List.of(new Solicitud(referenciaHotel, A))));
        assertTrue(servicio.recuperar(viaje, List.of(new Solicitud(INICIO, A))).isEmpty());
    }

    @Test
    void noDevuelveRutasQueNoSeHanSolicitadoYConservaElOrdenSinDuplicados() {
        var solicitudes = new ArrayList<>(List.of(new Solicitud(B, RESTAURANTE),
                new Solicitud(INICIO, A), new Solicitud(B, RESTAURANTE)));
        var copia = List.copyOf(solicitudes);
        var resultado = servicio.recuperar(viaje(null, bloques()), solicitudes);

        assertEquals(List.of(dto(B, RESTAURANTE, 300.0, 180.0), dto(INICIO, A, 100.0, 60.0)), resultado);
        assertEquals(copia, solicitudes);
        solicitudes.clear();
        assertEquals(2, resultado.size());
    }

    @Test
    void unIdentificadorConCoordenadasContradictoriasNoSeReutiliza() {
        var bloques = new ArrayList<>(bloques());
        var otraUbicacion = new PuntoRutaDto(A.id(), 40.0, -4.0);
        bloques.add(visita(otraUbicacion));
        var viaje = viaje(null, bloques);

        assertTrue(servicio.recuperar(viaje,
                List.of(new Solicitud(INICIO, A), new Solicitud(INICIO, otraUbicacion),
                        new Solicitud(A, B))).isEmpty());
        assertEquals(List.of(dto(B, RESTAURANTE, 300.0, 180.0)),
                servicio.recuperar(viaje, List.of(new Solicitud(B, RESTAURANTE))));
    }

    @Test
    void unaUbicacionOriginalIncompletaNoSeSupleConLasCoordenadasDeLaSolicitud() {
        var bloques = List.of(traslado(INICIO.id(), A.id(), 100.0, 60.0, "openrouteservice"),
                visita(new PuntoRutaDto(A.id(), null, A.longitud())));
        assertTrue(servicio.recuperar(viaje(null, bloques), List.of(new Solicitud(INICIO, A))).isEmpty());
    }

    @Test
    void unaUbicacionOriginalInvalidaHaceAmbiguoElIdentificadorAunqueAparezcaOtraValida() {
        var bloques = new ArrayList<>(bloques());
        bloques.add(visita(new PuntoRutaDto(A.id(), Double.NaN, A.longitud())));
        assertTrue(servicio.recuperar(viaje(null, bloques), List.of(new Solicitud(INICIO, A))).isEmpty());
    }

    @Test
    void noReutilizaUnTrayectoSiElExtremoNoEstaEnLasVisitasGuardadas() {
        var bloques = List.of(traslado(INICIO.id(), A.id(), 100.0, 60.0, "openrouteservice"));
        assertTrue(servicio.recuperar(viaje(null, bloques), List.of(new Solicitud(INICIO, A))).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("trasladosInvalidos")
    void rechazaTrasladosConDatosIncompletosInvalidosODeOtraFuente(BloqueItinerarioDto traslado) {
        var viaje = viaje(null, List.of(traslado, visita(A)));
        assertTrue(servicio.recuperar(viaje, List.of(new Solicitud(INICIO, A))).isEmpty());
    }

    private static Stream<BloqueItinerarioDto> trasladosInvalidos() {
        return Stream.of(traslado(INICIO.id(), A.id(), null, 60.0, "openrouteservice"),
                traslado(INICIO.id(), A.id(), 100.0, null, "openrouteservice"),
                traslado(INICIO.id(), A.id(), -1.0, 60.0, "openrouteservice"),
                traslado(INICIO.id(), A.id(), 100.0, -1.0, "openrouteservice"),
                traslado(INICIO.id(), A.id(), Double.NaN, 60.0, "openrouteservice"),
                traslado(INICIO.id(), A.id(), 100.0, Double.POSITIVE_INFINITY, "openrouteservice"),
                traslado(INICIO.id(), A.id(), 100.0, 60.0, "otra fuente"),
                traslado(INICIO.id(), A.id(), 100.0, 60.0, null),
                traslado(null, A.id(), 100.0, 60.0, "openrouteservice"),
                traslado(INICIO.id(), null, 100.0, 60.0, "openrouteservice"));
    }

    @Test
    void aceptaValoresCeroValidosSinConvertirAusenciasEnCeros() {
        var viaje = viaje(null, List.of(traslado(INICIO.id(), A.id(), 0.0, 0.0, "openrouteservice"), visita(A)));
        assertEquals(List.of(dto(INICIO, A, 0.0, 0.0)),
                servicio.recuperar(viaje, List.of(new Solicitud(INICIO, A))));
    }

    @Test
    void sinSolicitudesNoDevuelveRutas() {
        assertTrue(servicio.recuperar(viaje(null, bloques()), List.of()).isEmpty());
    }

    private static List<BloqueItinerarioDto> bloques() {
        var restaurante = new OpcionComidaDto(RESTAURANTE.id(), "Comida", RESTAURANTE.latitud(),
                RESTAURANTE.longitud(), "Valencia");
        var comida = new BloqueItinerarioDto(TipoBloqueItinerario.COMIDA,
                FECHA.atTime(13, 0), FECHA.atTime(15, 0), null,
                null, null, null, null, null, true, "Comida", restaurante, List.of());
        return List.of(traslado(INICIO.id(), A.id(), 100.0, 60.0, "openrouteservice"), visita(A),
                traslado(A.id(), B.id(), 200.0, 120.0, "openrouteservice"), visita(B),
                traslado(B.id(), RESTAURANTE.id(), 300.0, 180.0, "openrouteservice"), comida);
    }

    private static ViajeGuardadoDto viaje(AlojamientoDto hotel, List<BloqueItinerarioDto> bloques) {
        var config = new ConfiguracionItinerarioDto(
                new DestinoDto("valencia", "Valencia", "Valencia", "España", INICIO.latitud(), INICIO.longitud()),
                new FechaHoraDto(FECHA.atTime(10, 0), FECHA.atTime(18, 0), 1), hotel == null ? "" : hotel.direccion(),
                new PreferenciasDto(List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), List.of(), NivelGasto.MODERADO, 3.0),
                new RitmoDiarioDto(LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10), hotel);
        var plan = new ItinerarioGeneradoDto(List.of(new DiaItinerarioDto(FECHA, FECHA.atTime(10, 0),
                FECHA.atTime(18, 0), List.copyOf(bloques))), List.of(), false, 2, 2, List.of(), false);
        return new ViajeGuardadoDto(new ResumenViajeDto(UUID.randomUUID(), "Prueba", "Valencia", FECHA, FECHA,
                Instant.parse("2026-11-25T10:00:00Z"), 1), config, plan, List.of());
    }

    private static BloqueItinerarioDto visita(PuntoRutaDto punto) {
        var lugar = new LugarDto(punto.id(), "Museo " + punto.id(), "Valencia", punto.latitud(), punto.longitud(),
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), "24/7");
        var candidato = new CandidatoEvaluadoDto(lugar, 80, 60, "Arte", "Prueba", CostLevel.FREE,
                null, List.of(), "24/7", "Geoapify Places", "LOCAL", CostConfidence.CONFIRMED,
                ScheduleStatus.VERIFIED, 0.0, 0.0, "EUR", null, null, 80);
        return new BloqueItinerarioDto(TipoBloqueItinerario.VISITA, FECHA.atTime(10, 10),
                FECHA.atTime(11, 10), candidato, null, null, null, null, null, false, "Visita");
    }

    private static BloqueItinerarioDto traslado(String origen, String destino, Double distancia,
            Double segundos, String fuente) {
        return new BloqueItinerarioDto(TipoBloqueItinerario.TRASLADO, FECHA.atTime(10, 0), FECHA.atTime(10, 10),
                null, origen, destino, distancia, segundos, fuente, false, "Traslado");
    }

    private static DesplazamientoDto dto(PuntoRutaDto origen, PuntoRutaDto destino, double distancia, double segundos) {
        return new DesplazamientoDto(origen.id(), destino.id(), distancia, segundos,
                EstadoDesplazamiento.DISPONIBLE, "openrouteservice");
    }
}
