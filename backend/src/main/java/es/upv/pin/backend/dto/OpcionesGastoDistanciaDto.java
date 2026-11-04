package es.upv.pin.backend.dto;

import java.util.List;

import es.upv.pin.backend.model.NivelGasto;

public record OpcionesGastoDistanciaDto(
        List<NivelGastoDto> nivelesGasto,
        int distanciaMinimaKm,
        int distanciaMaximaKm,
        int pasoDistanciaKm,
        NivelGasto nivelGastoInicial,
        int distanciaInicialKm,
        String significadoGasto,
        String significadoDistancia) {
}
