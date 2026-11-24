package es.upv.pin.backend.dto;

import es.upv.pin.backend.model.EstadoLugar;

public record SeleccionLugarDto(LugarDto lugar, EstadoLugar estado) {}
