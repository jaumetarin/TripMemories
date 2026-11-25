package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import es.upv.pin.backend.service.FranjasEdicionService.Tipo;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NecesidadesRutasEdicionService {
    private final FranjasEdicionService franjas;

    public NecesidadesRutasEdicionService(FranjasEdicionService franjas) {
        this.franjas = franjas;
    }

    public List<Solicitud> obtener(
            ConfiguracionItinerarioDto configuracion,
            DiaItinerarioDto dia,
            List<Visita> visitas
    ) {
        var ventanas = franjas.obtener(dia, configuracion.ritmo());

        if (ventanas.isEmpty()) {
            return List.of();
        }

        var alojamiento = configuracion.alojamientoLocalizado();
        var destino = configuracion.destino();

        var inicio = alojamiento == null
                ? new PuntoRutaDto(
                        "referencia:inicio",
                        destino.latitud(),
                        destino.longitud())
                : new PuntoRutaDto(
                        "referencia:inicio",
                        alojamiento.latitud(),
                        alojamiento.longitud());

        var comida = dia.bloques().stream()
                .filter(b -> b.tipo() == TipoBloqueItinerario.COMIDA)
                .findFirst()
                .orElse(null);

        var restaurante = comida == null ? null : comida.restaurante();
        PuntoRutaDto puntoComida = restaurante == null
                ? null
                : new PuntoRutaDto(
                        restaurante.id(),
                        restaurante.latitud(),
                        restaurante.longitud());

        Map<Par, Solicitud> necesarias = new LinkedHashMap<>();

        PuntoRutaDto anterior = inicio;

        for (var visita : visitas) {
            var lugar = visita.candidato().lugar();
            var actual = new PuntoRutaDto(
                    lugar.id(), lugar.latitud(), lugar.longitud());

            agregar(necesarias, anterior, actual);
            anterior = actual;
        }

        int indiceComida = -1;

        for (int i = 0; i < ventanas.size(); i++) {
            if (ventanas.get(i).tipo() == Tipo.COMIDA) {
                indiceComida = i;
                break;
            }
        }

        if (puntoComida != null && indiceComida >= 0) {
            boolean hayManana = indiceComida > 0;
            boolean hayTarde = indiceComida + 1 < ventanas.size();

            if (hayManana) {
                agregar(necesarias, inicio, puntoComida);
            }

            for (var visita : visitas) {
                var lugar = visita.candidato().lugar();
                var puntoVisita = new PuntoRutaDto(
                        lugar.id(), lugar.latitud(), lugar.longitud());

                if (hayManana) {
                    agregar(necesarias, puntoVisita, puntoComida);
                }

                if (hayTarde) {
                    agregar(necesarias, puntoComida, puntoVisita);
                }
            }
        }

        return List.copyOf(necesarias.values());
    }

    private static void agregar(
            Map<Par, Solicitud> necesarias,
            PuntoRutaDto origen,
            PuntoRutaDto destino
    ) {
        if (origen.id().equals(destino.id())) {
            return;
        }

        necesarias.putIfAbsent(
                new Par(origen.id(), destino.id()),
                new Solicitud(origen, destino));
    }

    private record Par(String origen, String destino) {}

    public record Solicitud(
            PuntoRutaDto origen,
            PuntoRutaDto destino
    ) {}
}
