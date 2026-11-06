package es.upv.pin.backend.dto;

import java.util.List;

public record RecomendacionComidaDto(List<OpcionComidaDto> opciones, List<String> advertencias) {}
