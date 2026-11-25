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

    /** Conserva los huecos de las visitas eliminadas al mover las que siguen visibles. */
    public List<UUID> ordenConEliminadas(List<UUID> referencia, List<UUID> activas) {
        if (new HashSet<>(activas).size() != activas.size() || !referencia.containsAll(activas)) throw ordenInvalido();
        var visibles = new HashSet<>(activas);
        var nuevas = activas.iterator();
        return referencia.stream().map(id -> visibles.contains(id) ? nuevas.next() : id).toList();
    }

    public int posicionRestauracion(List<UUID> referencia, List<UUID> activas, UUID restaurada) {
        int posicion = referencia.indexOf(restaurada);
        if (posicion < 0 || activas.contains(restaurada) || !referencia.containsAll(activas)) throw ordenInvalido();
        for (int i = 0; i < activas.size(); i++) {
            if (referencia.indexOf(activas.get(i)) > posicion) return i;
        }
        return activas.size();
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
