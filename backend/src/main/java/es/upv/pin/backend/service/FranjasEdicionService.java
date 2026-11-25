package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.DiaItinerarioDto;
import es.upv.pin.backend.dto.RitmoDiarioDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FranjasEdicionService {

    public List<Franja> obtener(
            DiaItinerarioDto dia,
            RitmoDiarioDto ritmo
    ) {
        Objects.requireNonNull(dia, "Falta el día que quieres editar.");
        Objects.requireNonNull(ritmo, "Falta el ritmo del viaje.");

        var inicio = dia.inicioDisponible();
        var fin = dia.finDisponible();

        if (inicio == null && fin == null) {
            return List.of();
        }

        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new IllegalArgumentException(
                    "El día no tiene una disponibilidad válida.");
        }

        if (ritmo.comidaInicio() == null
                || ritmo.comidaFin() == null
                || !ritmo.comidaFin().isAfter(ritmo.comidaInicio())) {
            throw new IllegalArgumentException(
                    "El viaje no tiene una franja de comida válida.");
        }

        var comidaInicio = dia.fecha().atTime(ritmo.comidaInicio());
        var comidaFin = dia.fecha().atTime(ritmo.comidaFin());

        if (!comidaFin.isAfter(inicio)
                || !comidaInicio.isBefore(fin)) {
            return List.of(new Franja(Tipo.VISITAS, inicio, fin));
        }

        // La comida solo ocupa la parte incluida en la disponibilidad.
        if (comidaInicio.isBefore(inicio)) {
            comidaInicio = inicio;
        }
        if (comidaFin.isAfter(fin)) {
            comidaFin = fin;
        }

        var franjas = new ArrayList<Franja>();

        if (inicio.isBefore(comidaInicio)) {
            franjas.add(new Franja(
                    Tipo.VISITAS, inicio, comidaInicio));
        }

        franjas.add(new Franja(
                Tipo.COMIDA, comidaInicio, comidaFin));

        if (comidaFin.isBefore(fin)) {
            franjas.add(new Franja(
                    Tipo.VISITAS, comidaFin, fin));
        }

        return List.copyOf(franjas);
    }

    public enum Tipo {
        VISITAS,
        COMIDA
    }

    public record Franja(
            Tipo tipo,
            LocalDateTime inicio,
            LocalDateTime fin
    ) {}
}
