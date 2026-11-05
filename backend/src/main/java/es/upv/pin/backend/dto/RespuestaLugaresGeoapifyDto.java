package es.upv.pin.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaLugaresGeoapifyDto(
        List<FeatureLugarGeoapifyDto> features) {
}
