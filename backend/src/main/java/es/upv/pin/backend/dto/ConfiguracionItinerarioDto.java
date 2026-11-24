package es.upv.pin.backend.dto;

import java.util.List;

public record ConfiguracionItinerarioDto(
        DestinoDto destino,
        FechaHoraDto disponibilidad,
        String alojamiento,
        PreferenciasDto preferencias,
        RitmoDiarioDto ritmo,
        List<SeleccionLugarDto> selecciones) {
    public ConfiguracionItinerarioDto(DestinoDto destino, FechaHoraDto disponibilidad,
            String alojamiento, PreferenciasDto preferencias, RitmoDiarioDto ritmo) {
        this(destino, disponibilidad, alojamiento, preferencias, ritmo, List.of());
    }
    public ConfiguracionItinerarioDto(DestinoDto destino, FechaHoraDto disponibilidad,
            String alojamiento, PreferenciasDto preferencias) {
        this(destino, disponibilidad, alojamiento, preferencias, null, List.of());
    }
}
