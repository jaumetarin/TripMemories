package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

/** Extrae únicamente desplazamientos presentes en el plan autorizado del servidor. */
public final class TramosPlan {
    private TramosPlan() {}
    public record Tramo(String clave, String origenId, String destinoId, PuntoRutaDto origen, PuntoRutaDto destino) {}

    public static List<Tramo> delDia(ConfiguracionItinerarioDto config, ItinerarioGeneradoDto plan, LocalDate fecha) {
        var dia = plan.dias().stream().filter(d -> d.fecha().equals(fecha)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El día no pertenece al itinerario."));
        Map<String, PuntoRutaDto> puntos = new HashMap<>();
        plan.candidatos().forEach(c -> agregar(puntos, c.lugar()));
        plan.dias().forEach(d -> d.bloques().forEach(b -> {
            if (b.candidato() != null) agregar(puntos, b.candidato().lugar());
            if (b.restaurante() != null) {
                var r = b.restaurante();
                puntos.put(r.id(), new PuntoRutaDto(r.id(), r.latitud(), r.longitud()));
            }
        }));
        var alojamiento = config.alojamientoLocalizado();
        puntos.put("referencia:inicio", new PuntoRutaDto("referencia:inicio",
                alojamiento == null ? config.destino().latitud() : alojamiento.latitud(),
                alojamiento == null ? config.destino().longitud() : alojamiento.longitud()));
        List<Tramo> tramos = new ArrayList<>();
        for (var b : dia.bloques()) if (b.tipo() == TipoBloqueItinerario.TRASLADO) {
            var origen = puntos.get(b.origenId());
            var destino = puntos.get(b.destinoId());
            String clave = huella("foot-walking|" + b.origenId() + "|" + origen + "|" + b.destinoId() + "|" + destino);
            tramos.add(new Tramo(clave, b.origenId(), b.destinoId(), origen, destino));
        }
        return List.copyOf(tramos);
    }

    public static Map<String, GeometriaTramoDto> vigentes(ConfiguracionItinerarioDto config,
            ItinerarioGeneradoDto plan, Map<String, GeometriaTramoDto> geometrias) {
        Map<String, GeometriaTramoDto> resultado = new LinkedHashMap<>();
        for (var dia : plan.dias()) for (var tramo : delDia(config, plan, dia.fecha())) {
            var geometria = geometrias.get(tramo.clave());
            if (geometria != null) resultado.put(tramo.clave(), geometria);
        }
        return Map.copyOf(resultado);
    }

    public static boolean localizado(PuntoRutaDto p) {
        return p != null && p.latitud() != null && p.longitud() != null
                && Double.isFinite(p.latitud()) && Math.abs(p.latitud()) <= 90
                && Double.isFinite(p.longitud()) && Math.abs(p.longitud()) <= 180;
    }
    private static void agregar(Map<String, PuntoRutaDto> puntos, LugarDto lugar) {
        puntos.put(lugar.id(), new PuntoRutaDto(lugar.id(), lugar.latitud(), lugar.longitud()));
    }
    private static String huella(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
