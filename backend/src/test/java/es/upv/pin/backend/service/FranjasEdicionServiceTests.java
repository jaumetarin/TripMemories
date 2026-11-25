package es.upv.pin.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import es.upv.pin.backend.dto.DiaItinerarioDto;
import es.upv.pin.backend.dto.RitmoDiarioDto;
import es.upv.pin.backend.service.FranjasEdicionService.Franja;
import es.upv.pin.backend.service.FranjasEdicionService.Tipo;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FranjasEdicionServiceTests {
    private final FranjasEdicionService servicio = new FranjasEdicionService();
    private final LocalDate fecha = LocalDate.of(2026, 12, 7);
    private final RitmoDiarioDto ritmo = new RitmoDiarioDto(
            LocalTime.of(13, 0), LocalTime.of(15, 0), 120, 15, 10);

    @Test
    void reservaComidaEntreLasFranjasDeVisitasSinAlterarElDiaOriginal() {
        var dia = dia("10:00", "18:00");
        var resultado = servicio.obtener(dia, ritmo);

        assertEquals(List.of(franja(Tipo.VISITAS, "10:00", "13:00"),
                franja(Tipo.COMIDA, "13:00", "15:00"),
                franja(Tipo.VISITAS, "15:00", "18:00")), resultado);
        assertEquals(fecha.atTime(10, 0), dia.inicioDisponible());
        assertEquals(fecha.atTime(18, 0), dia.finDisponible());
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.add(franja(Tipo.VISITAS, "18:00", "19:00")));
    }

    @ParameterizedTest
    @CsvSource({"14:00,18:00,14:00,15:00", "10:00,14:00,13:00,14:00",
            "13:30,14:30,13:30,14:30", "13:00,15:00,13:00,15:00"})
    void recortaLaComidaALaDisponibilidadReal(String inicio, String fin,
            String comidaInicio, String comidaFin) {
        var resultado = servicio.obtener(dia(inicio, fin), ritmo);
        assertEquals(List.of(franja(Tipo.COMIDA, comidaInicio, comidaFin)),
                resultado.stream().filter(f -> f.tipo() == Tipo.COMIDA).toList());
        assertEquals(fecha.atTime(LocalTime.parse(inicio)), resultado.getFirst().inicio());
        assertEquals(fecha.atTime(LocalTime.parse(fin)), resultado.getLast().fin());
        for (int i = 0; i < resultado.size(); i++) {
            assertTrue(resultado.get(i).fin().isAfter(resultado.get(i).inicio()));
            if (i > 0) assertEquals(resultado.get(i - 1).fin(), resultado.get(i).inicio());
        }
    }

    @ParameterizedTest
    @CsvSource({"10:00,13:00", "15:00,18:00", "10:00,12:00", "16:00,18:00"})
    void noAnadeComidaSiNoSeSolapaConLaDisponibilidad(String inicio, String fin) {
        assertEquals(List.of(franja(Tipo.VISITAS, inicio, fin)),
                servicio.obtener(dia(inicio, fin), ritmo));
    }

    @Test
    void unDiaSinDisponibilidadNoTieneFranjas() {
        assertTrue(servicio.obtener(new DiaItinerarioDto(fecha, null, null, List.of()), ritmo).isEmpty());
    }

    @Test
    void rechazaLimitesAusentesInvertidosOVacios() {
        var invalidos = List.of(
                new DiaItinerarioDto(fecha, fecha.atTime(10, 0), null, List.of()),
                new DiaItinerarioDto(fecha, null, fecha.atTime(18, 0), List.of()),
                dia("18:00", "10:00"), dia("10:00", "10:00"));
        for (var dia : invalidos) {
            assertThrows(IllegalArgumentException.class, () -> servicio.obtener(dia, ritmo));
        }
    }

    private DiaItinerarioDto dia(String inicio, String fin) {
        return new DiaItinerarioDto(fecha, fecha.atTime(LocalTime.parse(inicio)),
                fecha.atTime(LocalTime.parse(fin)), List.of());
    }

    private Franja franja(Tipo tipo, String inicio, String fin) {
        return new Franja(tipo, fecha.atTime(LocalTime.parse(inicio)), fecha.atTime(LocalTime.parse(fin)));
    }
}
