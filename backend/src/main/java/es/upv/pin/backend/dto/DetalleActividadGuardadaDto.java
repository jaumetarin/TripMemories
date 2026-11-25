package es.upv.pin.backend.dto;

import java.util.List;

public record DetalleActividadGuardadaDto(
        CandidatoEvaluadoDto candidato,
        String origenId,
        String destinoId,
        Double distanciaMetros,
        Double duracionSegundos,
        String fuente,
        boolean condicionada,
        String descripcion,
        OpcionComidaDto restaurante,
        List<OpcionComidaDto> alternativasComida
) {

    public static DetalleActividadGuardadaDto de(
            BloqueItinerarioDto bloque
    ) {
        return new DetalleActividadGuardadaDto(
                bloque.candidato(),
                bloque.origenId(),
                bloque.destinoId(),
                bloque.distanciaMetros(),
                bloque.duracionSegundos(),
                bloque.fuente(),
                bloque.condicionada(),
                bloque.descripcion(),
                bloque.restaurante(),
                List.copyOf(bloque.alternativasComida())
        );
    }
}
