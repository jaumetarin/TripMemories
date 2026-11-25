package es.upv.pin.backend.service;

import es.upv.pin.backend.client.OpenRouteServiceClient;
import es.upv.pin.backend.dto.DesplazamientoDto;
import es.upv.pin.backend.dto.PuntoRutaDto;
import es.upv.pin.backend.dto.ViajeGuardadoDto;
import es.upv.pin.backend.model.EstadoDesplazamiento;
import es.upv.pin.backend.model.LimitesItinerario;
import es.upv.pin.backend.service.NecesidadesRutasEdicionService.Solicitud;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResolucionRutasEdicionService {
    private static final String ATRIBUTO = ResolucionRutasEdicionService.class.getName();
    private final CacheRutasEdicionService cache;
    private final RutasGuardadasEdicionService guardadas;
    private final OpenRouteServiceClient cliente;

    public ResolucionRutasEdicionService(CacheRutasEdicionService cache,
            RutasGuardadasEdicionService guardadas, OpenRouteServiceClient cliente) {
        this.cache = cache;
        this.guardadas = guardadas;
        this.cliente = cliente;
    }

    public Resultado resolver(ViajeGuardadoDto viaje, List<Solicitud> solicitudes,
            HttpSession sesion) {
        var unicas = validar(solicitudes);
        if (unicas.isEmpty()) return new Resultado(List.of(), false);
        var control = control(sesion);
        synchronized (control) {
            if (control.enCurso) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya estamos calculando rutas en esta sesión. Espera a que termine.");
            }
            control.enCurso = true;
        }
        try {
            return resolverInterno(viaje, unicas, sesion, TiempoEdicion.actual());
        } finally {
            synchronized (control) { control.enCurso = false; }
        }
    }

    private Resultado resolverInterno(ViajeGuardadoDto viaje, List<Solicitud> solicitudes,
            HttpSession sesion, TiempoEdicion tiempo) {
        Map<Solicitud, DesplazamientoDto> resueltas = new LinkedHashMap<>();
        var pendientes = new ArrayList<Solicitud>();
        for (var solicitud : solicitudes) {
            if (!coordenadasValidas(solicitud.origen()) || !coordenadasValidas(solicitud.destino())) {
                resueltas.put(solicitud, sinRuta(solicitud, EstadoDesplazamiento.COORDENADAS_AUSENTES, null));
            } else if (mismasCoordenadas(solicitud.origen(), solicitud.destino())) {
                resueltas.put(solicitud, new DesplazamientoDto(solicitud.origen().id(),
                        solicitud.destino().id(), 0.0, 0.0, EstadoDesplazamiento.DISPONIBLE, "misma ubicación"));
            } else {
                var encontrada = cache.buscar(solicitud, sesion);
                if (encontrada.isPresent()) resueltas.put(solicitud, encontrada.get());
                else pendientes.add(solicitud);
            }
        }

        Map<Par, DesplazamientoDto> reutilizables = new HashMap<>();
        if (!pendientes.isEmpty()) {
            for (var ruta : guardadas.recuperar(viaje, pendientes)) {
                reutilizables.put(new Par(ruta.origenId(), ruta.destinoId()), ruta);
            }
        }
        Map<PuntoRutaDto, List<Solicitud>> grupos = new LinkedHashMap<>();
        for (var solicitud : pendientes) {
            var ruta = reutilizables.get(new Par(solicitud.origen().id(), solicitud.destino().id()));
            if (ruta != null) {
                resueltas.put(solicitud, ruta);
                cache.guardar(solicitud, ruta, sesion);
            } else {
                grupos.computeIfAbsent(solicitud.origen(), clave -> new ArrayList<>()).add(solicitud);
            }
        }

        boolean consultaFallida = false;
        int maximoDestinos = LimitesItinerario.MAXIMO_PUNTOS_RUTA - 1;
        for (var grupo : grupos.values()) {
            for (int desde = 0; desde < grupo.size(); desde += maximoDestinos) {
                var lote = grupo.subList(desde, Math.min(grupo.size(), desde + maximoDestinos));
                if (consultaFallida || !tiempo.permiteConsulta()) {
                    consultaFallida = true;
                    marcarNoDisponibles(lote, resueltas);
                    continue;
                }
                es.upv.pin.backend.dto.RespuestaMatrizOrsDto matriz;
                try {
                    matriz = cliente.calcularDesde(lote.getFirst().origen(),
                            lote.stream().map(Solicitud::destino).toList());
                } catch (IllegalStateException error) {
                    // No repetimos el fallo para cada origen si el proveedor no responde o rechaza la petición.
                    consultaFallida = true;
                    marcarNoDisponibles(lote, resueltas);
                    continue;
                }
                for (int columna = 0; columna < lote.size(); columna++) {
                    var solicitud = lote.get(columna);
                    var distancia = matriz.distances().getFirst().get(columna);
                    var segundos = matriz.durations().getFirst().get(columna);
                    var ruta = distancia == null || segundos == null
                            ? sinRuta(solicitud, EstadoDesplazamiento.RUTA_NO_DISPONIBLE, "openrouteservice")
                            : new DesplazamientoDto(solicitud.origen().id(), solicitud.destino().id(),
                                    distancia, segundos, EstadoDesplazamiento.DISPONIBLE, "openrouteservice");
                    resueltas.put(solicitud, ruta);
                    cache.guardar(solicitud, ruta, sesion);
                }
            }
        }
        return new Resultado(solicitudes.stream().map(resueltas::get).toList(), consultaFallida);
    }

    private static void marcarNoDisponibles(List<Solicitud> solicitudes,
            Map<Solicitud, DesplazamientoDto> resueltas) {
        for (var solicitud : solicitudes) {
            resueltas.put(solicitud, sinRuta(solicitud, EstadoDesplazamiento.RUTA_NO_DISPONIBLE, "openrouteservice"));
        }
    }

    private static DesplazamientoDto sinRuta(Solicitud solicitud, EstadoDesplazamiento estado, String fuente) {
        return new DesplazamientoDto(solicitud.origen().id(), solicitud.destino().id(),
                null, null, estado, fuente);
    }

    private static List<Solicitud> validar(List<Solicitud> solicitudes) {
        if (solicitudes == null) throw new IllegalArgumentException("Faltan las rutas que quieres calcular.");
        Map<String, PuntoRutaDto> puntos = new HashMap<>();
        for (var solicitud : solicitudes) {
            if (solicitud == null) throw new IllegalArgumentException("Hay una solicitud de ruta inválida.");
            registrar(puntos, solicitud.origen());
            registrar(puntos, solicitud.destino());
        }
        return List.copyOf(new LinkedHashSet<>(solicitudes));
    }

    private static void registrar(Map<String, PuntoRutaDto> puntos, PuntoRutaDto punto) {
        if (punto == null || punto.id() == null || punto.id().isBlank()) {
            throw new IllegalArgumentException("Cada punto de ruta necesita un identificador.");
        }
        var anterior = puntos.putIfAbsent(punto.id(), punto);
        if (anterior != null && !anterior.equals(punto)) {
            throw new IllegalArgumentException("Un punto de ruta tiene coordenadas contradictorias.");
        }
    }

    private static boolean coordenadasValidas(PuntoRutaDto punto) {
        return punto.latitud() != null && Double.isFinite(punto.latitud()) && Math.abs(punto.latitud()) <= 90
                && punto.longitud() != null && Double.isFinite(punto.longitud()) && Math.abs(punto.longitud()) <= 180;
    }

    private static boolean mismasCoordenadas(PuntoRutaDto a, PuntoRutaDto b) {
        return a.latitud().doubleValue() == b.latitud().doubleValue()
                && a.longitud().doubleValue() == b.longitud().doubleValue();
    }

    private synchronized Control control(HttpSession sesion) {
        var control = (Control) sesion.getAttribute(ATRIBUTO);
        if (control == null) { control = new Control(); sesion.setAttribute(ATRIBUTO, control); }
        return control;
    }

    private static class Control { private boolean enCurso; }
    private record Par(String origen, String destino) {}

    public record Resultado(List<DesplazamientoDto> rutas, boolean consultaFallida) {}
}
