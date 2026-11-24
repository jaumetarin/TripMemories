package es.upv.pin.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record LugarGeoapifyDto (    
    String place_id,
    String name,
    Double lat,
    Double lon,
    List<String> categories,
    String formatted,
    String opening_hours,
    OrigenLugarGeoapifyDto datasource,
    String street,
    String housenumber,
    String result_type,
    java.util.Map<String, String> name_international){
    public LugarGeoapifyDto(String place_id, String name, Double lat, Double lon, List<String> categories,
            String formatted, String opening_hours, OrigenLugarGeoapifyDto datasource, String street,
            String housenumber, String result_type) {
        this(place_id, name, lat, lon, categories, formatted, opening_hours, datasource, street, housenumber, result_type, null);
    }
}
