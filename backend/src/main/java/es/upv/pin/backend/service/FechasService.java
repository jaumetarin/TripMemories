package es.upv.pin.backend.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.dto.FechasDto;

@Service
public class FechasService {

    private static final int MAXIMO_DIAS = 10;

    public FechasDto validar(FechasDto fechas) {

        if (fechas == null) {
            throw new IllegalArgumentException(
                    "Selecciona las fechas del viaje.");
        }

        LocalDate inicio = fechas.fechaInicio();
        LocalDate fin = fechas.fechaFin();

        if (inicio == null) {
            throw new IllegalArgumentException(
                    "Selecciona una fecha de inicio.");
        }

        if (fin == null) {
            throw new IllegalArgumentException(
                    "Selecciona una fecha de fin.");
        }

        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException(
                    "La fecha de fin debe ser igual o posterior a la de inicio.");
        }

        long duracionDias = ChronoUnit.DAYS.between(inicio, fin) + 1;

        if (duracionDias > MAXIMO_DIAS) {
            throw new IllegalArgumentException(
                    "El viaje puede durar como máximo 10 días, contando el inicio y el fin.");
        }

        return new FechasDto(
                inicio,
                fin,
                (int) duracionDias);
    }
}
