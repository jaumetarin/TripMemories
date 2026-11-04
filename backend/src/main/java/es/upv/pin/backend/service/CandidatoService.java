package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.CandidatoDto;
import es.upv.pin.backend.dto.FeatureLugarGeoapifyDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;
import es.upv.pin.backend.dto.RespuestaLugaresGeoapifyDto;

@Service
public class CandidatoService {

    private static final int LIMITE_POR_BUSQUEDA = 50;

    private static final String CATEGORIAS_TURISTICAS =
            "tourism.sights,entertainment.museum,leisure.park";

    private static final String CATEGORIAS_GASTRONOMICAS =
            "catering.restaurant";

    private final GeoapifyClient geoapifyClient;

    public CandidatoService(GeoapifyClient geoapifyClient) {
        this.geoapifyClient = geoapifyClient;
    }

    public List<CandidatoDto> buscarTuristicos(String destinoId) {
        RespuestaLugaresGeoapifyDto respuesta =
                geoapifyClient.buscarLugares(
                        destinoId,
                        CATEGORIAS_TURISTICAS,
                        LIMITE_POR_BUSQUEDA);

        return extraerCandidatos(respuesta);
    }

    public List<CandidatoDto> buscarGastronomicos(String destinoId) {
        RespuestaLugaresGeoapifyDto respuesta =
                geoapifyClient.buscarLugares(
                        destinoId,
                        CATEGORIAS_GASTRONOMICAS,
                        LIMITE_POR_BUSQUEDA);

        return extraerCandidatos(respuesta);
    }

    private List<CandidatoDto> extraerCandidatos(
            RespuestaLugaresGeoapifyDto respuesta) {

        List<CandidatoDto> candidatos = new ArrayList<>();

        for (FeatureLugarGeoapifyDto resultado : respuesta.features()) {
            if (resultado == null || resultado.properties() == null) {
                throw new IllegalStateException(
                        "El servicio de lugares ha devuelto un resultado no válido.");
            }

            LugarGeoapifyDto lugar = resultado.properties();

            CandidatoDto candidato = new CandidatoDto(
                    lugar.place_id(),
                    lugar.name(),
                    lugar.lat(),
                    lugar.lon(),
                    lugar.categories(),
                    lugar.formatted());

            candidatos.add(candidato);
        }

        return candidatos;
    }
}