package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.BloqueItinerarioDto;
import es.upv.pin.backend.dto.DiaItinerarioDto;
import es.upv.pin.backend.dto.ViajeGuardadoDto;
import es.upv.pin.backend.dto.ViajeGuardadoDto.ReferenciaActividadDto;
import es.upv.pin.backend.exception.RevisionViajeException;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContextoEdicionService {
    private final ConsultaViajeService consultas;

    public ContextoEdicionService(ConsultaViajeService consultas) {
        this.consultas = consultas;
    }

    public ViajeGuardadoDto recuperar(
            UUID usuarioId, UUID viajeId, long revision) {
        if (revision < 1) {
            throw new IllegalArgumentException(
                    "Indica una revisión positiva del viaje.");
        }

        var viaje = consultas.recuperar(usuarioId, viajeId);

        if (viaje.resumen().revision() != revision) {
            throw new RevisionViajeException();
        }

        return viaje;
    }

    public VisitaEditable prepararVisita(
            UUID usuarioId,
            UUID viajeId,
            long revision,
            UUID actividadId) {
        var viaje = recuperar(usuarioId, viajeId, revision);

        var referencia = viaje.actividades().stream()
                .filter(actividad -> actividad.id().equals(actividadId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se ha encontrado la actividad en este viaje."));

        var dia = viaje.itinerario().dias().stream()
                .filter(elemento -> elemento.fecha().equals(referencia.dia()))
                .findFirst()
                .orElseThrow(ContextoEdicionService::datosIncompatibles);

        int posicion = referencia.orden() - 1;

        if (posicion < 0 || posicion >= dia.bloques().size()) {
            throw datosIncompatibles();
        }

        var bloque = dia.bloques().get(posicion);

        if (bloque.tipo() != TipoBloqueItinerario.VISITA) {
            throw new IllegalArgumentException(
                    "Esta acción solo permite editar visitas. "
                            + "Los demás bloques se ajustan al recalcular el día.");
        }

        if (bloque.candidato() == null) {
            throw datosIncompatibles();
        }

        return new VisitaEditable(viaje, referencia, dia, bloque);
    }

    private static ResponseStatusException datosIncompatibles() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "La actividad no contiene los datos necesarios para editarla. "
                        + "El viaje guardado no se ha modificado.");
    }

    public record VisitaEditable(
            ViajeGuardadoDto viaje,
            ReferenciaActividadDto referencia,
            DiaItinerarioDto dia,
            BloqueItinerarioDto bloque
    ) {}
}
