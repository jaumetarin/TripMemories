package es.upv.pin.backend.dto;

import es.upv.pin.backend.model.NivelGasto;

public record NivelGastoDto(NivelGasto codigo, String nombre, String descripcion) {
}
