package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.ViajeGuardadoDto.ReferenciaActividadDto;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import es.upv.pin.backend.repository.ViajeRepository;
import es.upv.pin.backend.repository.ViajeRepository.ActividadGuardada;
import es.upv.pin.backend.repository.ViajeRepository.CabeceraGuardada;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConsultaViajeService {

    private final ViajeRepository viajes;
    private final ObjectMapper json;
    private final TransactionTemplate lecturas;

    public ConsultaViajeService(
            ViajeRepository viajes,
            ObjectMapper json,
            PlatformTransactionManager gestorTransacciones
    ) {
        this.viajes = viajes;
        this.json = json;

        this.lecturas = new TransactionTemplate(gestorTransacciones);
        this.lecturas.setReadOnly(true);
        this.lecturas.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ
        );
        this.lecturas.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public List<ResumenViajeDto> listarPropios(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "Falta el usuario autenticado.");
        return viajes.listarPropios(usuarioId);
    }

    public ViajeGuardadoDto recuperar(
            UUID usuarioId,
            UUID viajeId
    ) {
        Objects.requireNonNull(usuarioId, "Falta el usuario autenticado.");
        Objects.requireNonNull(viajeId, "Falta el identificador del viaje.");

        return lecturas.execute(estado -> {
            var cabecera = viajes.buscarPropio(usuarioId, viajeId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "No se ha encontrado el viaje."
                    ));

            if (cabecera.itinerarioId() == null
                    || !Integer.valueOf(1).equals(cabecera.formato())
                    || cabecera.configuracionJson() == null
                    || cabecera.datosGeneracionJson() == null) {
                throw noRecuperable();
            }

            var actividades = viajes.listarActividadesPropias(
                    usuarioId,
                    cabecera.itinerarioId()
            );

            return reconstruir(cabecera, actividades);
        });
    }

    private ViajeGuardadoDto reconstruir(
            CabeceraGuardada cabecera,
            List<ActividadGuardada> actividades
    ) {
        try {
            var configuracion = json.readValue(
                    cabecera.configuracionJson(),
                    ConfiguracionItinerarioDto.class
            );

            var datos = json.readValue(
                    cabecera.datosGeneracionJson(),
                    DatosGeneracionGuardadosDto.class
            );

            if (configuracion == null
                    || configuracion.destino() == null
                    || configuracion.disponibilidad() == null
                    || configuracion.preferencias() == null
                    || datos == null
                    || datos.dias() == null
                    || datos.advertencias() == null
                    || datos.candidatos() == null) {
                throw noRecuperable();
            }

            var bloquesPorDia =
                    new LinkedHashMap<LocalDate, List<BloqueItinerarioDto>>();

            for (var dia : datos.dias()) {
                if (dia == null
                        || dia.fecha() == null
                        || dia.inicioDisponible() == null
                        || dia.finDisponible() == null
                        || bloquesPorDia.putIfAbsent(
                                dia.fecha(),
                                new ArrayList<>()
                        ) != null) {
                    throw noRecuperable();
                }
            }

            var referencias = new ArrayList<ReferenciaActividadDto>();

            for (var actividad : actividades) {
                var bloques = bloquesPorDia.get(actividad.dia());

                if (bloques == null) {
                    throw noRecuperable();
                }

                bloques.add(reconstruirActividad(actividad));

                referencias.add(new ReferenciaActividadDto(
                        actividad.id(),
                        actividad.dia(),
                        actividad.orden()
                ));
            }

            var dias = datos.dias().stream()
                    .map(dia -> new DiaItinerarioDto(
                            dia.fecha(),
                            dia.inicioDisponible(),
                            dia.finDisponible(),
                            List.copyOf(bloquesPorDia.get(dia.fecha()))
                    ))
                    .toList();

            var itinerario = new ItinerarioGeneradoDto(
                    dias,
                    datos.advertencias(),
                    datos.parcial(),
                    datos.candidatosEvaluados(),
                    datos.candidatosEnMatriz(),
                    datos.candidatos(),
                    datos.evaluacionFallback(), datos.incidenciasEdicion()
            );

            return new ViajeGuardadoDto(
                    cabecera.resumen(),
                    configuracion,
                    itinerario,
                    List.copyOf(referencias)
            );
        } catch (JacksonException | IllegalArgumentException error) {
            throw noRecuperable();
        }
    }

    private BloqueItinerarioDto reconstruirActividad(
            ActividadGuardada actividad
    ) {
        if (actividad.inicio() == null
                || actividad.fin() == null
                || actividad.tipo() == null
                || actividad.detalleJson() == null) {
            throw noRecuperable();
        }

        var inicio = actividad.dia().atTime(actividad.inicio());

        if (actividad.fin().isBefore(inicio)) {
            throw noRecuperable();
        }

        var detalle = json.readValue(
                actividad.detalleJson(),
                DetalleActividadGuardadaDto.class
        );

        if (detalle == null || detalle.alternativasComida() == null) {
            throw noRecuperable();
        }

        return new BloqueItinerarioDto(
                TipoBloqueItinerario.valueOf(actividad.tipo()),
                inicio,
                actividad.fin(),
                detalle.candidato(),
                detalle.origenId(),
                detalle.destinoId(),
                detalle.distanciaMetros(),
                detalle.duracionSegundos(),
                detalle.fuente(),
                detalle.condicionada(),
                detalle.descripcion(),
                detalle.restaurante(),
                detalle.alternativasComida()
        );
    }

    private static ResponseStatusException noRecuperable() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El viaje no contiene un itinerario completo "
                        + "en un formato compatible. "
                        + "Sus datos guardados no se han modificado."
        );
    }
}
