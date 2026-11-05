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
    OrigenLugarGeoapifyDto datasource){
}
