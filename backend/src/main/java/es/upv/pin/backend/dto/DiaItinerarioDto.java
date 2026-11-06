package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DiaItinerarioDto(
        LocalDate fecha,
        LocalDateTime inicioDisponible,
        LocalDateTime finDisponible,
        List<BloqueItinerarioDto> bloques) {}
