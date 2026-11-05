package es.upv.pin.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record LugarDto(String nombre, String direccion,
        @JsonInclude(JsonInclude.Include.NON_NULL) String horario) {
}
