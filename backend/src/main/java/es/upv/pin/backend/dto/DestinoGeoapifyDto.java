package es.upv.pin.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DestinoGeoapifyDto(
        String place_id,
        String city,
        String name,
        String address_line1,
        String state,
        String country,
        Double lat,
        Double lon) {
}
