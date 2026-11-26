package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.BloqueItinerarioDto;
import es.upv.pin.backend.dto.ActualizarViajeDto;
import es.upv.pin.backend.dto.ViajeActualizadoDto;
import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.DatosGeneracionGuardadosDto;
import es.upv.pin.backend.dto.DetalleActividadGuardadaDto;
import es.upv.pin.backend.dto.GuardarViajeDto;
import es.upv.pin.backend.dto.ItinerarioGeneradoDto;
import es.upv.pin.backend.repository.ViajeRepository;
import es.upv.pin.backend.exception.RevisionViajeException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class ViajeService {

    private final ViajeRepository viajes;
    private final TransactionTemplate transacciones;
    private final ObjectMapper json;

    public ViajeService(
            ViajeRepository viajes,
            TransactionTemplate transacciones,
            ObjectMapper json
    ) {
        this.viajes = viajes;
        this.transacciones = transacciones;
        this.json = json;
    }

    public UUID guardarNuevo(
            UUID usuarioId,
            GuardarViajeDto entrada,
            ConfiguracionItinerarioDto configuracion,
            ItinerarioGeneradoDto itinerario
    ) {
        Objects.requireNonNull(usuarioId, "Falta el usuario autenticado.");
        Objects.requireNonNull(configuracion, "Falta la configuración generada.");
        Objects.requireNonNull(itinerario, "Falta el itinerario generado.");

        String titulo = validarTitulo(entrada == null ? null : entrada.titulo());

        String configuracionJson = json.writeValueAsString(
                configuracion
        );

        String datosGeneracionJson = json.writeValueAsString(
                DatosGeneracionGuardadosDto.de(itinerario)
        );

        return transacciones.execute(estado -> {
            UUID viajeId = viajes.insertarViaje(
                    usuarioId,
                    titulo,
                    configuracion.destino().nombre(),
                    configuracion.disponibilidad().llegada().toLocalDate(),
                    configuracion.disponibilidad().salida().toLocalDate()
            );

            guardarItinerario(viajeId, configuracionJson, datosGeneracionJson, itinerario);

            return viajeId;
        });
    }

    public ViajeActualizadoDto actualizar(UUID usuarioId, UUID viajeId, ActualizarViajeDto entrada,
            ConfiguracionItinerarioDto configuracion, ItinerarioGeneradoDto itinerario) {
        Objects.requireNonNull(usuarioId, "Falta el usuario autenticado.");
        Objects.requireNonNull(viajeId, "Falta el identificador del viaje.");
        Objects.requireNonNull(configuracion, "Falta la configuración generada.");
        Objects.requireNonNull(itinerario, "Falta el itinerario generado.");
        String titulo = validarTitulo(entrada == null ? null : entrada.titulo());
        if (entrada.revision() == null || entrada.revision() < 1)
            throw new IllegalArgumentException("Indica una revisión positiva del viaje.");
        String configuracionJson = json.writeValueAsString(configuracion);
        String datosGeneracionJson = json.writeValueAsString(DatosGeneracionGuardadosDto.de(itinerario));

        return transacciones.execute(estado -> {
            boolean actualizado = viajes.actualizarViaje(usuarioId, viajeId, entrada.revision(), titulo,
                    configuracion.destino().nombre(), configuracion.disponibilidad().llegada().toLocalDate(),
                    configuracion.disponibilidad().salida().toLocalDate());
            if (!actualizado) {
                if (viajes.buscarPropio(usuarioId, viajeId).isEmpty())
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se ha encontrado el viaje.");
                throw new RevisionViajeException();
            }
            viajes.marcarItinerariosAnteriores(usuarioId, viajeId);
            guardarItinerario(viajeId, configuracionJson, datosGeneracionJson, itinerario);
            return new ViajeActualizadoDto(viajeId, entrada.revision() + 1);
        });
    }

    private void guardarItinerario(UUID viajeId, String configuracionJson, String datosGeneracionJson,
            ItinerarioGeneradoDto itinerario) {
        UUID itinerarioId = viajes.insertarItinerario(viajeId, configuracionJson, datosGeneracionJson);
        for (var dia : itinerario.dias()) {
            int orden = 1;
            for (var bloque : dia.bloques()) {
                validarBloque(dia.fecha(), bloque);
                viajes.insertarActividad(itinerarioId, dia.fecha(), orden++, bloque,
                        json.writeValueAsString(DetalleActividadGuardadaDto.de(bloque)));
            }
        }
    }

    public void conservarTrazados(UUID usuario, UUID viaje, long revision,
            java.util.Map<String, es.upv.pin.backend.dto.GeometriaTramoDto> trazados) {
        if (!viajes.guardarTrazados(usuario, viaje, revision, json.writeValueAsString(trazados)))
            throw new RevisionViajeException();
    }

    private String validarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank() || titulo.length() > 160)
            throw new IllegalArgumentException("Introduce un título de entre 1 y 160 caracteres.");
        return titulo.strip();
    }

    private void validarBloque(
            LocalDate dia,
            BloqueItinerarioDto bloque
    ) {
        if (dia == null
                || bloque == null
                || bloque.tipo() == null
                || bloque.inicio() == null
                || bloque.fin() == null
                || !bloque.inicio().toLocalDate().equals(dia)
                || bloque.fin().isBefore(bloque.inicio())) {
            throw new IllegalArgumentException(
                    "El itinerario contiene una actividad con horario inválido."
            );
        }
    }
}
