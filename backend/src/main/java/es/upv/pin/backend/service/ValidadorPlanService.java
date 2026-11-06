package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.EstadoHorarioVisita;
import es.upv.pin.backend.model.TipoBloqueItinerario;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class ValidadorPlanService {
    private final EvaluadorHorarioService horarios = new EvaluadorHorarioService();

    public ResultadoValidacionPlan validar(List<DiaItinerarioDto> dias, PreferenciasDto preferencias) {
        List<String> incidencias = new ArrayList<>();
        Set<String> visitados = new HashSet<>();
        if (dias == null) return new ResultadoValidacionPlan(List.of("Faltan los días del plan"), true);
        for (DiaItinerarioDto dia : dias) {
            // Un día sin ventana y sin bloques representa disponibilidad agotada, no un plan inválido.
            if (dia != null && dia.fecha() != null && dia.inicioDisponible() == null
                    && dia.finDisponible() == null && dia.bloques() != null && dia.bloques().isEmpty()) continue;
            if (dia == null || dia.fecha() == null || dia.inicioDisponible() == null || dia.finDisponible() == null
                    || !dia.finDisponible().isAfter(dia.inicioDisponible())
                    || !dia.fecha().equals(dia.inicioDisponible().toLocalDate())
                    || !dia.fecha().equals(dia.finDisponible().toLocalDate())) {
                incidencias.add("Ventana diaria inválida"); continue;
            }
            if (dia.bloques() == null) { incidencias.add("Faltan los bloques del día " + dia.fecha()); continue; }
            LocalDateTime finAnterior = null;
            String ubicacion = null;
            LugarDto ultimoLugar = null;
            for (BloqueItinerarioDto bloque : dia.bloques()) {
                if (bloque == null || bloque.tipo() == null || bloque.inicio() == null || bloque.fin() == null
                        || !bloque.fin().isAfter(bloque.inicio())) {
                    incidencias.add("Bloque o duración inválidos en " + dia.fecha()); continue;
                }
                if (bloque.inicio().isBefore(dia.inicioDisponible()) || bloque.fin().isAfter(dia.finDisponible()))
                    incidencias.add("Bloque fuera de la ventana en " + dia.fecha());
                if (finAnterior != null && bloque.inicio().isBefore(finAnterior)) incidencias.add("Bloques desordenados o solapados en " + dia.fecha());
                if (finAnterior == null || bloque.fin().isAfter(finAnterior)) finAnterior = bloque.fin();
                if (bloque.tipo() == TipoBloqueItinerario.VISITA) {
                    CandidatoEvaluadoDto c = bloque.candidato();
                    if (c == null || c.lugar() == null || c.lugar().id() == null || c.lugar().id().isBlank()) {
                        incidencias.add("Visita sin lugar identificado en " + dia.fecha()); continue;
                    }
                    if (!visitados.add(c.lugar().id())) incidencias.add("Visita duplicada: " + c.lugar().id());
                    if (ubicacion != null && !ubicacion.equals(c.lugar().id()) && !mismoPunto(ultimoLugar, c.lugar()))
                        incidencias.add("Falta traslado hasta la visita: " + c.lugar().id());
                    ubicacion = c.lugar().id();
                    ultimoLugar = c.lugar();
                    if (c.duracionMinutos() <= 0 || Duration.between(bloque.inicio(), bloque.fin()).toMinutes() < c.duracionMinutos())
                        incidencias.add("Duración insuficiente de visita: " + c.lugar().id());
                    if (horarios.evaluar(c, bloque.inicio(), bloque.fin())
                            == EstadoHorarioVisita.CONFLICTO_SEGUN_FUENTE)
                        incidencias.add("Visita incompatible con el horario de la fuente: " + c.lugar().id());
                    if (preferencias != null && (intersectan(c.lugar().categorias(), preferencias.categoriasExcluidas())
                            || intersectan(c.lugar().subcategorias(), preferencias.subcategoriasExcluidas())))
                        incidencias.add("Visita excluida: " + c.lugar().id());
                }
                if (bloque.tipo() == TipoBloqueItinerario.TRASLADO &&
                        (vacio(bloque.origenId()) || vacio(bloque.destinoId()) || vacio(bloque.fuente())
                                || bloque.distanciaMetros() == null || !Double.isFinite(bloque.distanciaMetros()) || bloque.distanciaMetros() < 0
                                || bloque.duracionSegundos() == null || !Double.isFinite(bloque.duracionSegundos())
                                || bloque.duracionSegundos() < 0
                                || Duration.between(bloque.inicio(), bloque.fin()).toSeconds() < bloque.duracionSegundos()))
                    incidencias.add("Traslado sin ruta o tiempo válido en " + dia.fecha());
                if (bloque.tipo() == TipoBloqueItinerario.TRASLADO) {
                    if (ubicacion != null && !ubicacion.equals(bloque.origenId()))
                        incidencias.add("Origen del traslado no coincide con la ubicación conocida en " + dia.fecha());
                    ubicacion = vacio(bloque.destinoId()) ? null : bloque.destinoId();
                    ultimoLugar = null;
                } else if (bloque.tipo() == TipoBloqueItinerario.COMIDA) {
                    // La comida genérica no acredita una ubicación concreta.
                    OpcionComidaDto restaurante = bloque.restaurante();
                    LugarDto lugarRestaurante = restaurante == null ? null : new LugarDto(
                            restaurante.id(), restaurante.nombre(), restaurante.direccion(),
                            restaurante.latitud(), restaurante.longitud(), List.of(), List.of(), restaurante.horario());
                    if (restaurante != null && ubicacion != null && !ubicacion.equals(restaurante.id())
                            && !mismoPunto(ultimoLugar, lugarRestaurante))
                        incidencias.add("Falta traslado hasta el restaurante: " + restaurante.id());
                    ubicacion = restaurante == null ? null : restaurante.id();
                    ultimoLugar = lugarRestaurante;
                }
            }
        }
        return new ResultadoValidacionPlan(List.copyOf(incidencias), !incidencias.isEmpty());
    }
    private static boolean vacio(String s) { return s == null || s.isBlank(); }
    private static boolean mismoPunto(LugarDto a, LugarDto b) {
        return a != null && b != null && a.latitud() != null && a.longitud() != null
                && b.latitud() != null && b.longitud() != null
                && Double.isFinite(a.latitud()) && Double.isFinite(a.longitud())
                && Double.isFinite(b.latitud()) && Double.isFinite(b.longitud())
                && a.latitud().doubleValue() == b.latitud().doubleValue()
                && a.longitud().doubleValue() == b.longitud().doubleValue();
    }
    private static <T> boolean intersectan(List<T> a, List<T> b) {
        return a != null && b != null && a.stream().anyMatch(b::contains);
    }
}
