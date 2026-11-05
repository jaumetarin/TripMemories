package es.upv.pin.backend.client;

import es.upv.pin.backend.dto.HallazgoGemini;
import es.upv.pin.backend.dto.LugarDto;

import java.util.List;
import java.util.Map;

public interface ResearchProvider {
    boolean disponible();

    Map<String, HallazgoGemini> investigar(List<LugarDto> lugares, String destino);
}
