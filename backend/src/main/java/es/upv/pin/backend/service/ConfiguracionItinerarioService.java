package es.upv.pin.backend.service;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.FechaHoraDto;
import es.upv.pin.backend.dto.PreferenciasDto;

@Service
public class ConfiguracionItinerarioService {
    private final FechaHoraService fechaHoraService;
    private final PreferenciasService preferenciasService;

    public ConfiguracionItinerarioService(FechaHoraService fechaHoraService, PreferenciasService preferenciasService) {
        this.fechaHoraService = fechaHoraService;
        this.preferenciasService = preferenciasService;
    }

    // No guarda ni genera: devuelve una copia validada que podrá consumir el Planner.
    public ConfiguracionItinerarioDto validar(ConfiguracionItinerarioDto configuracion) {
        if (configuracion == null) {
            throw new IllegalArgumentException("Envía la configuración completa del viaje.");
        }
        DestinoDto destino = validarDestino(configuracion.destino());
        // La duración se recalcula; nunca se confía en el número enviado por el cliente.
        FechaHoraDto disponibilidad = fechaHoraService.validar(configuracion.disponibilidad());
        PreferenciasDto preferencias = preferenciasService.validar(configuracion.preferencias());
        String alojamiento = textoOpcional(configuracion.alojamiento(), 250, "El alojamiento");
        return new ConfiguracionItinerarioDto(destino, disponibilidad, alojamiento, preferencias);
    }

    private DestinoDto validarDestino(DestinoDto destino) {
        if (destino == null || destino.id() == null || destino.id().isBlank()
                || destino.nombre() == null || destino.nombre().isBlank()) {
            throw new IllegalArgumentException("Selecciona un destino de la lista de sugerencias.");
        }
        if (destino.latitud() == null || destino.longitud() == null
                || !Double.isFinite(destino.latitud()) || !Double.isFinite(destino.longitud())
                || destino.latitud() < -90 || destino.latitud() > 90
                || destino.longitud() < -180 || destino.longitud() > 180) {
            throw new IllegalArgumentException("El destino debe tener coordenadas válidas. Vuelve a seleccionarlo.");
        }
        return new DestinoDto(
                textoOpcional(destino.id(), 2048, "El identificador del destino"),
                textoOpcional(destino.nombre(), 200, "El nombre del destino"),
                textoOpcional(destino.region(), 200, "La región"),
                textoOpcional(destino.pais(), 120, "El país"),
                destino.latitud(), destino.longitud());
    }

    private String textoOpcional(String valor, int longitudMaxima, String campo) {
        String texto = valor == null ? "" : valor.strip();
        if (texto.length() > longitudMaxima) {
            throw new IllegalArgumentException(campo + " no puede superar " + longitudMaxima + " caracteres.");
        }
        return texto;
    }

}
