package es.upv.pin.backend.dto;

public record BusquedaLugaresDto(ConfiguracionItinerarioDto configuracion, String texto, boolean ampliar,
        Boolean completarCatalogo, Integer limiteResultados) {
    public BusquedaLugaresDto {
        completarCatalogo = Boolean.TRUE.equals(completarCatalogo);
        limiteResultados = limiteResultados == null ? 30 : limiteResultados;
    }
    public BusquedaLugaresDto(ConfiguracionItinerarioDto configuracion, String texto, boolean ampliar) {
        this(configuracion, texto, ampliar, false, 30);
    }
    public BusquedaLugaresDto(ConfiguracionItinerarioDto configuracion, String texto, boolean ampliar, boolean completarCatalogo) {
        this(configuracion, texto, ampliar, completarCatalogo, 30);
    }
}
