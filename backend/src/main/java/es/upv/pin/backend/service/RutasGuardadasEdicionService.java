package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import es.upv.pin.backend.service.NecesidadesRutasEdicionService.Solicitud;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RutasGuardadasEdicionService {

    public List<DesplazamientoDto> recuperar(
            ViajeGuardadoDto viaje,
            List<Solicitud> solicitudes
    ) {
        Map<String, PuntoRutaDto> puntos = new HashMap<>();
        Set<String> ambiguos = new HashSet<>();
        var configuracion = viaje.configuracion();
        var alojamiento = configuracion.alojamientoLocalizado();

        agregar(puntos, ambiguos, alojamiento == null
                ? new PuntoRutaDto(
                        "referencia:inicio",
                        configuracion.destino().latitud(),
                        configuracion.destino().longitud())
                : new PuntoRutaDto(
                        "referencia:inicio",
                        alojamiento.latitud(),
                        alojamiento.longitud()));

        for (var dia : viaje.itinerario().dias()) {
            for (var bloque : dia.bloques()) {
                if (bloque.tipo() == TipoBloqueItinerario.VISITA
                        && bloque.candidato() != null
                        && bloque.candidato().lugar() != null) {
                    var lugar = bloque.candidato().lugar();
                    agregar(puntos, ambiguos, new PuntoRutaDto(
                            lugar.id(), lugar.latitud(), lugar.longitud()));
                }

                if (bloque.tipo() == TipoBloqueItinerario.COMIDA
                        && bloque.restaurante() != null) {
                    var restaurante = bloque.restaurante();
                    agregar(puntos, ambiguos, new PuntoRutaDto(
                            restaurante.id(),
                            restaurante.latitud(),
                            restaurante.longitud()));
                }
            }
        }

        Map<Par, DesplazamientoDto> guardadas = new HashMap<>();

        for (var dia : viaje.itinerario().dias()) {
            for (var bloque : dia.bloques()) {
                if (bloque.tipo() != TipoBloqueItinerario.TRASLADO
                        || !utilizable(bloque)) {
                    continue;
                }

                guardadas.putIfAbsent(
                        new Par(bloque.origenId(), bloque.destinoId()),
                        new DesplazamientoDto(
                                bloque.origenId(),
                                bloque.destinoId(),
                                bloque.distanciaMetros(),
                                bloque.duracionSegundos(),
                                EstadoDesplazamiento.DISPONIBLE,
                                bloque.fuente()));
            }
        }

        Map<Solicitud, DesplazamientoDto> resultado =
                new LinkedHashMap<>();

        for (var solicitud : solicitudes) {
            if (!coincide(solicitud.origen(), puntos, ambiguos)
                    || !coincide(solicitud.destino(), puntos, ambiguos)) {
                continue;
            }

            var ruta = guardadas.get(new Par(
                    solicitud.origen().id(),
                    solicitud.destino().id()));

            if (ruta != null) {
                resultado.putIfAbsent(solicitud, ruta);
            }
        }

        return List.copyOf(resultado.values());
    }

    private static void agregar(
            Map<String, PuntoRutaDto> puntos,
            Set<String> ambiguos,
            PuntoRutaDto punto
    ) {
        if (punto.id() == null || punto.id().isBlank()) {
            return;
        }

        if (!coordenadasValidas(punto)) {
            ambiguos.add(punto.id());
            return;
        }

        var anterior = puntos.putIfAbsent(punto.id(), punto);

        if (anterior != null && !anterior.equals(punto)) {
            ambiguos.add(punto.id());
        }
    }

    private static boolean coincide(
            PuntoRutaDto solicitado,
            Map<String, PuntoRutaDto> puntos,
            Set<String> ambiguos
    ) {
        return solicitado != null
                && coordenadasValidas(solicitado)
                && !ambiguos.contains(solicitado.id())
                && solicitado.equals(puntos.get(solicitado.id()));
    }

    private static boolean coordenadasValidas(PuntoRutaDto punto) {
        return punto.latitud() != null
                && Double.isFinite(punto.latitud())
                && Math.abs(punto.latitud()) <= 90
                && punto.longitud() != null
                && Double.isFinite(punto.longitud())
                && Math.abs(punto.longitud()) <= 180;
    }

    private static boolean utilizable(BloqueItinerarioDto bloque) {
        return bloque.origenId() != null
                && bloque.destinoId() != null
                && "openrouteservice".equals(bloque.fuente())
                && bloque.distanciaMetros() != null
                && Double.isFinite(bloque.distanciaMetros())
                && bloque.distanciaMetros() >= 0
                && bloque.duracionSegundos() != null
                && Double.isFinite(bloque.duracionSegundos())
                && bloque.duracionSegundos() >= 0;
    }

    private record Par(String origen, String destino) {}
}
