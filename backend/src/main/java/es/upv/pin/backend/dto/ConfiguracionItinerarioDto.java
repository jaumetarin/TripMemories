package es.upv.pin.backend.dto;

public record ConfiguracionItinerarioDto(
        DestinoDto destino,
        FechaHoraDto disponibilidad,
        String alojamiento,
        PreferenciasDto preferencias,
        RitmoDiarioDto ritmo,
        AlojamientoDto alojamientoLocalizado) {

    public ConfiguracionItinerarioDto(
            DestinoDto destino,
            FechaHoraDto disponibilidad,
            String alojamiento,
            PreferenciasDto preferencias,
            RitmoDiarioDto ritmo) {

        this(destino, disponibilidad, alojamiento, preferencias, ritmo, null);
    }

    public ConfiguracionItinerarioDto(
            DestinoDto destino,
            FechaHoraDto disponibilidad,
            String alojamiento,
            PreferenciasDto preferencias) {

        this(destino, disponibilidad, alojamiento, preferencias, null, null);
    }
}