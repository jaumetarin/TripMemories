package es.upv.pin.backend.dto;

import java.util.List;

public record ConfiguracionItinerarioDto(
        DestinoDto destino,
        FechaHoraDto disponibilidad,
        String alojamiento,
        PreferenciasDto preferencias,
        RitmoDiarioDto ritmo,
        AlojamientoDto alojamientoLocalizado,
        List<SeleccionLugarDto> selecciones) {

    public ConfiguracionItinerarioDto(
            DestinoDto destino,
            FechaHoraDto disponibilidad,
            String alojamiento,
            PreferenciasDto preferencias,
            RitmoDiarioDto ritmo,
            AlojamientoDto alojamientoLocalizado) {

        this(destino, disponibilidad, alojamiento, preferencias,
                ritmo, alojamientoLocalizado, List.of());
    }

    public ConfiguracionItinerarioDto(
            DestinoDto destino,
            FechaHoraDto disponibilidad,
            String alojamiento,
            PreferenciasDto preferencias,
            RitmoDiarioDto ritmo,
            List<SeleccionLugarDto> selecciones) {

        this(destino, disponibilidad, alojamiento, preferencias,
                ritmo, null, selecciones);
    }

    public ConfiguracionItinerarioDto(
            DestinoDto destino,
            FechaHoraDto disponibilidad,
            String alojamiento,
            PreferenciasDto preferencias,
            RitmoDiarioDto ritmo) {

        this(destino, disponibilidad, alojamiento, preferencias,
                ritmo, null, List.of());
    }

    public ConfiguracionItinerarioDto(
            DestinoDto destino,
            FechaHoraDto disponibilidad,
            String alojamiento,
            PreferenciasDto preferencias) {

        this(destino, disponibilidad, alojamiento, preferencias,
                null, null, List.of());
    }
}