package es.upv.pin.backend.dto;

// Una misma configuración de entrada y salida para la revisión completa del viaje.
public record ConfiguracionItinerarioDto(
        DestinoDto destino,
        FechaHoraDto disponibilidad,
        String alojamiento,
        PreferenciasDto preferencias) {
}
