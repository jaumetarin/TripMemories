package es.upv.pin.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record FotoDto(UUID id, String nombre, String mime, long bytesOriginal, long bytesMiniatura,
        Integer ancho, Integer alto, String estado, Instant subidaEn) {}
