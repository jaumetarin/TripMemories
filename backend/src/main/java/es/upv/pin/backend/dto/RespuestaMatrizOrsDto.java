package es.upv.pin.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaMatrizOrsDto(
        List<List<Double>> distances,
        List<List<Double>> durations) {
}
