package es.upv.pin.backend.dto;

import java.time.LocalTime;

public record RitmoDiarioDto(LocalTime comidaInicio, LocalTime comidaFin,
        Integer descansoCadaMinutos, Integer descansoDuracionMinutos, Integer margenMinutos) {}
