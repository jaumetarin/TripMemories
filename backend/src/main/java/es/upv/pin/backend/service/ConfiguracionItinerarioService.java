package es.upv.pin.backend.service;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.DestinoDto;
import es.upv.pin.backend.dto.FechaHoraDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.dto.RitmoDiarioDto;
import java.time.LocalTime;

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
        return new ConfiguracionItinerarioDto(destino, disponibilidad, alojamiento, preferencias, validarRitmo(configuracion.ritmo()));
    }

    private RitmoDiarioDto validarRitmo(RitmoDiarioDto ritmo) {
        LocalTime inicio = ritmo == null || ritmo.comidaInicio() == null ? LocalTime.of(13, 0) : ritmo.comidaInicio();
        LocalTime fin = ritmo == null || ritmo.comidaFin() == null ? LocalTime.of(15, 0) : ritmo.comidaFin();
        int cada = ritmo == null || ritmo.descansoCadaMinutos() == null ? 120 : ritmo.descansoCadaMinutos();
        int duracion = ritmo == null || ritmo.descansoDuracionMinutos() == null ? 15 : ritmo.descansoDuracionMinutos();
        int margen = ritmo == null || ritmo.margenMinutos() == null ? 10 : ritmo.margenMinutos();
        if (inicio.isBefore(LocalTime.of(10, 0)) || fin.isAfter(LocalTime.of(18, 0))
                || !inicio.isBefore(fin) || cada < 30 || cada > 480
                || duracion < 5 || duracion > 120 || margen < 0 || margen > 120) {
            throw new IllegalArgumentException("El ritmo diario debe tener una comida entre las 10:00 y las 18:00 y duraciones validas.");
        }
        return new RitmoDiarioDto(inicio, fin, cada, duracion, margen);
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
