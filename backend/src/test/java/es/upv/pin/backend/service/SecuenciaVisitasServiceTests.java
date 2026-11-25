package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.CandidatoEvaluadoDto;
import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.CostConfidence;
import es.upv.pin.backend.model.CostLevel;
import es.upv.pin.backend.model.ScheduleStatus;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecuenciaVisitasServiceTests {
    @org.junit.jupiter.api.Test
    void restauraCuatroVisitasEnCualquierOrden() {
        var servicio = new SecuenciaVisitasService();
        var referencia = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        comprobarPermutaciones(servicio, referencia, new ArrayList<>(), new ArrayList<>(referencia));
    }

    private void comprobarPermutaciones(SecuenciaVisitasService servicio, List<UUID> referencia,
            List<UUID> prefijo, List<UUID> pendientes) {
        if (pendientes.isEmpty()) {
            var visibles = new ArrayList<UUID>();
            for (UUID id : prefijo) visibles.add(servicio.posicionRestauracion(referencia, visibles, id), id);
            assertEquals(referencia, visibles);
            return;
        }
        for (UUID id : pendientes) {
            var siguientes = new ArrayList<>(pendientes); siguientes.remove(id);
            var orden = new ArrayList<>(prefijo); orden.add(id);
            comprobarPermutaciones(servicio, referencia, orden, siguientes);
        }
    }

    @org.junit.jupiter.api.Test
    void moverVisitasVisiblesConservaLosHuecosYElOrdenActualAlRestaurar() {
        var servicio = new SecuenciaVisitasService();
        UUID a=UUID.randomUUID(), b=UUID.randomUUID(), c=UUID.randomUUID(), d=UUID.randomUUID();
        var referencia = servicio.ordenConEliminadas(List.of(a,b,c,d),List.of(d,a,c));
        assertEquals(List.of(d,b,a,c), referencia);
        var visibles = new ArrayList<>(List.of(d,a,c));
        visibles.add(servicio.posicionRestauracion(referencia,visibles,b),b);
        assertEquals(List.of(d,b,a,c),visibles);
    }

    private final SecuenciaVisitasService servicio = new SecuenciaVisitasService();
    private final Visita primera = visita("primera");
    private final Visita segunda = visita("segunda");
    private final Visita tercera = visita("tercera");
    private final List<Visita> originales = new ArrayList<>(
            List.of(primera, segunda, tercera));

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void eliminaUnaVisitaEnCualquierPosicionConservandoElResto(int posicion) {
        var esperadas = new ArrayList<>(originales);
        var retirada = esperadas.remove(posicion);

        var resultado = servicio.eliminar(originales, retirada.actividadId());

        assertEquals(esperadas, resultado);
        comprobarOriginales();
        comprobarInmutable(resultado);
    }

    @Test
    void permiteEliminarLaUltimaVisitaDelDia() {
        var entrada = new ArrayList<>(List.of(primera));
        var resultado = servicio.eliminar(entrada, primera.actividadId());

        assertTrue(resultado.isEmpty());
        assertEquals(List.of(primera), entrada);
        comprobarInmutable(resultado);
    }

    @Test
    void eliminarUnaActividadAjenaNoCambiaLaSecuencia() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.eliminar(originales, UUID.randomUUID()));
        comprobarOriginales();
    }

    @Test
    void reordenaSinPerderLosDatosDeLasVisitas() {
        var resultado = servicio.reordenar(originales, List.of(
                tercera.actividadId(), primera.actividadId(), segunda.actividadId()));

        assertEquals(List.of(tercera, primera, segunda), resultado);
        assertSame(tercera, resultado.getFirst());
        assertSame(primera, resultado.get(1));
        assertSame(segunda, resultado.getLast());
        comprobarOriginales();
        comprobarInmutable(resultado);
    }

    @Test
    void aceptaConservarElOrdenActual() {
        assertEquals(originales, servicio.reordenar(originales, List.of(
                primera.actividadId(), segunda.actividadId(), tercera.actividadId())));
        comprobarOriginales();
    }

    @Test
    void rechazaOrdenesQueOmitenAnadenRepitenOIntroducenActividades() {
        var ordenesInvalidos = List.of(
                List.of(primera.actividadId(), segunda.actividadId()),
                List.of(primera.actividadId(), segunda.actividadId(),
                        tercera.actividadId(), UUID.randomUUID()),
                List.of(primera.actividadId(), primera.actividadId(), tercera.actividadId()),
                List.of(primera.actividadId(), segunda.actividadId(), UUID.randomUUID()),
                Arrays.asList(primera.actividadId(), null, tercera.actividadId()));

        for (var orden : ordenesInvalidos) {
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.reordenar(originales, orden));
            comprobarOriginales();
        }
        assertThrows(IllegalArgumentException.class,
                () -> servicio.reordenar(originales, null));
        comprobarOriginales();
    }

    @Test
    void rechazaUnaSecuenciaOriginalConIdentificadoresDuplicados() {
        var entrada = new ArrayList<>(List.of(primera, primera));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.reordenar(entrada,
                        List.of(primera.actividadId(), primera.actividadId())));
        assertEquals(List.of(primera, primera), entrada);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void sustituyeSoloLaVisitaElegidaConservandoSuReferenciaYPosicion(int posicion) {
        var alternativa = candidato("alternativa");
        var anterior = originales.get(posicion);
        var resultado = servicio.sustituir(
                originales, anterior.actividadId(), alternativa);

        assertEquals(originales.size(), resultado.size());
        assertEquals(anterior.actividadId(), resultado.get(posicion).actividadId());
        assertSame(alternativa, resultado.get(posicion).candidato());
        for (int i = 0; i < originales.size(); i++) {
            if (i != posicion) assertSame(originales.get(i), resultado.get(i));
        }
        comprobarOriginales();
        comprobarInmutable(resultado);
    }

    @Test
    void sustituirUnaActividadAjenaNoCambiaLaSecuencia() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.sustituir(originales, UUID.randomUUID(), candidato("otra")));
        comprobarOriginales();
    }

    @Test
    void rechazaUnaAlternativaAusenteSinCambiarElOriginal() {
        assertThrows(NullPointerException.class,
                () -> servicio.sustituir(originales, segunda.actividadId(), null));
        comprobarOriginales();
    }

    @Test
    void elResultadoNoDependeDeCambiosPosterioresEnLaListaDeEntrada() {
        var resultado = servicio.reordenar(originales, List.of(
                tercera.actividadId(), segunda.actividadId(), primera.actividadId()));
        originales.clear();

        assertEquals(List.of(tercera, segunda, primera), resultado);
    }

    private void comprobarOriginales() {
        assertEquals(List.of(primera, segunda, tercera), originales);
    }

    private void comprobarInmutable(List<Visita> resultado) {
        assertThrows(UnsupportedOperationException.class, () -> resultado.add(primera));
        if (!resultado.isEmpty()) {
            assertThrows(UnsupportedOperationException.class, () -> resultado.set(0, primera));
        }
    }

    private static Visita visita(String id) {
        return new Visita(UUID.randomUUID(), candidato(id));
    }

    private static CandidatoEvaluadoDto candidato(String id) {
        var lugar = new LugarDto(id, "Museo " + id, "Valencia", 39.47, -0.37,
                List.of(CategoriaTuristica.ARTE_MUSEOS), List.of(), null);
        return new CandidatoEvaluadoDto(lugar, 80, 60, "Arte", "Visita de prueba",
                CostLevel.FREE, null, List.of(), null, null, "LOCAL",
                CostConfidence.CONFIRMED, ScheduleStatus.UNVERIFIED,
                0.0, 0.0, "EUR", null, null, 80);
    }
}
