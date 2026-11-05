package es.upv.pin.backend.client;

import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.dto.ValoracionGemini;
import es.upv.pin.backend.model.CategoriaTuristica;

import java.util.List;
import java.util.Map;

public interface AiProvider {
    Map<String, ValoracionGemini> evaluar(
            List<LugarDto> lugares, List<CategoriaTuristica> intereses, String destino);
}
