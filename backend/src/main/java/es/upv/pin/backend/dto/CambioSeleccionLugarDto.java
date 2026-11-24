package es.upv.pin.backend.dto;

import es.upv.pin.backend.model.EstadoLugar;

// Un estado null retira la selección, manteniendo el lugar como candidato normal.
public record CambioSeleccionLugarDto(ConfiguracionItinerarioDto configuracion, String lugarId, EstadoLugar estado) {}
