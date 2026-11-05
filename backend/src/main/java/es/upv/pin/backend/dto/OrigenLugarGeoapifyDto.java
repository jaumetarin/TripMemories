package es.upv.pin.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrigenLugarGeoapifyDto(EtiquetasLugarGeoapifyDto raw) {
}
