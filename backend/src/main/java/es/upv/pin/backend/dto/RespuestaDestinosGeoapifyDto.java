package es.upv.pin.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RespuestaDestinosGeoapifyDto {

    private List<DestinoGeoapifyDto> results;

    public RespuestaDestinosGeoapifyDto() {
    }

    public List<DestinoGeoapifyDto> getResults() {
        return results;
    }

    public void setResults(List<DestinoGeoapifyDto> results) {
        this.results = results;
    }
}
