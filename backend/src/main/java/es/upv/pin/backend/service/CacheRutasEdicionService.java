package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.service.NecesidadesRutasEdicionService.Solicitud;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CacheRutasEdicionService {
    private static final String ATRIBUTO =
            CacheRutasEdicionService.class.getName();
    private static final int MAXIMO_ENTRADAS = 512;
    private static final Duration VIGENCIA = Duration.ofMinutes(15);

    private final Clock reloj;

    @Autowired
    public CacheRutasEdicionService() {
        this(Clock.systemUTC());
    }

    CacheRutasEdicionService(Clock reloj) {
        this.reloj = reloj;
    }

    public Optional<DesplazamientoDto> buscar(
            Solicitud solicitud,
            HttpSession sesion
    ) {
        var clave = clave(solicitud);
        var estado = estado(sesion);

        synchronized (estado) {
            retirarCaducadas(estado);
            var entrada = estado.rutas.get(clave);

            return entrada == null
                    ? Optional.empty()
                    : Optional.of(entrada.ruta());
        }
    }

    public void guardar(
            Solicitud solicitud,
            DesplazamientoDto ruta,
            HttpSession sesion
    ) {
        var clave = clave(solicitud);

        if (!utilizable(solicitud, ruta)) {
            return;
        }

        var estado = estado(sesion);

        synchronized (estado) {
            retirarCaducadas(estado);
            estado.rutas.put(clave, new Entrada(
                    ruta, reloj.instant().plus(VIGENCIA)));

            while (estado.rutas.size() > MAXIMO_ENTRADAS) {
                estado.rutas.pollFirstEntry();
            }
        }
    }

    private void retirarCaducadas(Estado estado) {
        var ahora = reloj.instant();
        estado.rutas.values().removeIf(
                entrada -> !ahora.isBefore(entrada.caducaEn()));
    }

    private synchronized Estado estado(HttpSession sesion) {
        var estado = (Estado) sesion.getAttribute(ATRIBUTO);

        if (estado == null) {
            estado = new Estado();
            sesion.setAttribute(ATRIBUTO, estado);
        }

        return estado;
    }

    private static Clave clave(Solicitud solicitud) {
        if (solicitud == null
                || !puntoValido(solicitud.origen())
                || !puntoValido(solicitud.destino())) {
            throw new IllegalArgumentException(
                    "La ruta necesita origen y destino "
                            + "con identificadores y coordenadas válidos.");
        }

        return new Clave(solicitud.origen(), solicitud.destino());
    }

    private static boolean puntoValido(PuntoRutaDto punto) {
        return punto != null
                && punto.id() != null && !punto.id().isBlank()
                && punto.latitud() != null
                && Double.isFinite(punto.latitud())
                && Math.abs(punto.latitud()) <= 90
                && punto.longitud() != null
                && Double.isFinite(punto.longitud())
                && Math.abs(punto.longitud()) <= 180;
    }

    private static boolean utilizable(
            Solicitud solicitud,
            DesplazamientoDto ruta
    ) {
        return ruta != null
                && ruta.estado() == EstadoDesplazamiento.DISPONIBLE
                && solicitud.origen().id().equals(ruta.origenId())
                && solicitud.destino().id().equals(ruta.destinoId())
                && "openrouteservice".equals(ruta.fuente())
                && ruta.distanciaMetros() != null
                && Double.isFinite(ruta.distanciaMetros())
                && ruta.distanciaMetros() >= 0
                && ruta.duracionSegundos() != null
                && Double.isFinite(ruta.duracionSegundos())
                && ruta.duracionSegundos() >= 0;
    }

    private record Clave(
            PuntoRutaDto origen,
            PuntoRutaDto destino
    ) {}

    private record Entrada(
            DesplazamientoDto ruta,
            Instant caducaEn
    ) {}

    private static class Estado {
        private final LinkedHashMap<Clave, Entrada> rutas =
                new LinkedHashMap<>(16, 0.75f, true);
    }
}
