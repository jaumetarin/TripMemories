package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.CandidatoEvaluadoDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SecuenciaVisitasService {

    public List<Visita> eliminar(
            List<Visita> originales,
            UUID actividadId
    ) {
        int posicion = localizar(originales, actividadId);
        var resultado = new ArrayList<>(originales);
        resultado.remove(posicion);
        return List.copyOf(resultado);
    }

    public List<Visita> reordenar(
            List<Visita> originales,
            List<UUID> orden
    ) {
        if (orden == null || orden.size() != originales.size()) {
            throw ordenInvalido();
        }

        var porId = new HashMap<UUID, Visita>();

        for (var visita : originales) {
            if (porId.putIfAbsent(
                    visita.actividadId(), visita) != null) {
                throw ordenInvalido();
            }
        }

        var utilizados = new HashSet<UUID>();
        var resultado = new ArrayList<Visita>();

        for (UUID id : orden) {
            var visita = porId.get(id);

            if (visita == null || !utilizados.add(id)) {
                throw ordenInvalido();
            }

            resultado.add(visita);
        }

        return List.copyOf(resultado);
    }

    public List<Visita> sustituir(
            List<Visita> originales,
            UUID actividadId,
            CandidatoEvaluadoDto alternativa
    ) {
        int posicion = localizar(originales, actividadId);
        var resultado = new ArrayList<>(originales);
        resultado.set(
                posicion,
                new Visita(actividadId, alternativa));
        return List.copyOf(resultado);
    }

    private int localizar(
            List<Visita> visitas,
            UUID actividadId
    ) {
        for (int posicion = 0; posicion < visitas.size(); posicion++) {
            if (visitas.get(posicion).actividadId().equals(actividadId)) {
                return posicion;
            }
        }

        throw new IllegalArgumentException(
                "La visita no pertenece a la secuencia que estás editando.");
    }

    private static IllegalArgumentException ordenInvalido() {
        return new IllegalArgumentException(
                "El nuevo orden debe contener exactamente las visitas "
                        + "del día, sin añadir, retirar ni repetir ninguna.");
    }

    public record Visita(
            UUID actividadId,
            CandidatoEvaluadoDto candidato
    ) {
        public Visita {
            Objects.requireNonNull(
                    actividadId, "Falta el identificador de la actividad.");
            Objects.requireNonNull(
                    candidato, "Faltan los datos de la visita.");
        }
    }
}
