package es.upv.pin.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Solo leemos las etiquetas que usamos; el resto de la respuesta se ignora.
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtiquetasLugarGeoapifyDto(
        String amenity, String marketplace, String craft, String route, String historic,
        @JsonProperty("theatre:genre") String theatre_genre, String opening_hours) {
}
