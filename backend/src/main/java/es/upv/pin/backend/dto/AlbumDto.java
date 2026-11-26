package es.upv.pin.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AlbumDto(UUID id, UUID viajeId, String titulo, String viajeTitulo, String destino,
        LocalDate fechaInicio, LocalDate fechaFin, long fotos, UUID portadaId, boolean viajeEliminado, boolean eliminando) {}
