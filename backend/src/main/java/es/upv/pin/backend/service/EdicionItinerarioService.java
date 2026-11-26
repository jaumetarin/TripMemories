package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.dto.EstadoEdicionDto.*;
import es.upv.pin.backend.dto.IncidenciaEdicionDto.*;
import es.upv.pin.backend.model.*;
import es.upv.pin.backend.service.SecuenciaVisitasService.Visita;
import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EdicionItinerarioService {
    private static final String ATRIBUTO = EdicionItinerarioService.class.getName();
    private final ContextoEdicionService contexto;
    private final SecuenciaVisitasService secuencias;
    private final NecesidadesRutasEdicionService necesidades;
    private final ResolucionRutasEdicionService rutas;
    private final ReconstruccionDiaService reconstruccion;
    private final ValidadorPlanService validador;
    private final ViajeService escritura;

    public EdicionItinerarioService(ContextoEdicionService contexto, SecuenciaVisitasService secuencias,
            NecesidadesRutasEdicionService necesidades, ResolucionRutasEdicionService rutas,
            ReconstruccionDiaService reconstruccion, ValidadorPlanService validador, ViajeService escritura) {
        this.contexto = contexto;
        this.secuencias = secuencias;
        this.necesidades = necesidades;
        this.rutas = rutas;
        this.reconstruccion = reconstruccion;
        this.validador = validador;
        this.escritura = escritura;
    }

    public EstadoEdicionDto iniciar(UUID usuario, UUID viajeId, long revision, HttpSession sesion) {
        var base = contexto.recuperar(usuario, viajeId, revision);
        var estado = estado(sesion);
        synchronized (estado) {
            var visitas = new LinkedHashMap<LocalDate, List<Visita>>();
            var dias = new LinkedHashMap<LocalDate, DiaItinerarioDto>();
            for (var dia : base.itinerario().dias()) {
                var delDia = new ArrayList<Visita>();
                for (int i = 0; i < dia.bloques().size(); i++) {
                    var bloque = dia.bloques().get(i);
                    if (bloque.tipo() != TipoBloqueItinerario.VISITA) continue;
                    int orden = i + 1;
                    var referencia = base.actividades().stream()
                            .filter(a -> a.dia().equals(dia.fecha()) && a.orden() == orden).findFirst()
                            .orElseThrow(() -> conflicto("Falta la referencia de una visita guardada."));
                    if (bloque.candidato() == null) throw conflicto("Una visita no contiene datos editables.");
                    delDia.add(new Visita(referencia.id(), bloque.candidato()));
                }
                visitas.put(dia.fecha(), List.copyOf(delDia));
                dias.put(dia.fecha(), dia);
            }
            var borrador = new Borrador(usuario, base, visitas, dias, new LinkedHashMap<>(), false);
            estado.borradores.put(viajeId, borrador);
            while (estado.borradores.size() > 3) estado.borradores.pollFirstEntry();
            return respuesta(borrador);
        }
    }

    public EstadoEdicionDto recuperar(UUID usuario, UUID viaje, long revision, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, revision);
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = estado.borradores.get(viaje);
            if (borrador == null) throw conflicto("No hay un borrador disponible. Vuelve a abrir el editor.");
            return respuesta(obtener(estado, usuario, viaje, borrador.id, revision));
        }
    }

    public EstadoEdicionDto eliminar(UUID usuario, UUID viaje, UUID propuesta, UUID actividad,
            long revision, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, revision);
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, revision);
            var dia = diaDe(borrador, actividad);
            var anteriores = borrador.visitas.get(dia);
            var eliminada = new Eliminada(dia, visita(borrador, dia, actividad));
            var nueva = secuencias.eliminar(anteriores, actividad);
            var resultado = proponer(borrador, dia, nueva, sesion);
            resultado.eliminadas.put(actividad, eliminada);
            estado.borradores.put(viaje, resultado);
            return respuesta(resultado);
        }
    }

    public EstadoEdicionDto restaurar(UUID usuario, UUID viaje, UUID propuesta, UUID actividad,
            long revision, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, revision);
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, revision);
            var retirada = borrador.eliminadas.get(actividad);
            if (retirada == null) throw new IllegalArgumentException("Esta visita no se puede restaurar en la propuesta actual.");
            if (borrador.visitas.values().stream().flatMap(List::stream).anyMatch(v ->
                    IdentidadLugares.mismoLugar(v.candidato().lugar(), retirada.visita().candidato().lugar()))) {
                throw new IllegalArgumentException("El lugar ya está incluido en el viaje. No se puede duplicar al deshacer.");
            }
            var nueva = new ArrayList<>(borrador.visitas.get(retirada.dia()));
            int posicion = secuencias.posicionRestauracion(borrador.ordenRestauracion.get(retirada.dia()),
                    nueva.stream().map(Visita::actividadId).toList(), actividad);
            nueva.add(posicion, retirada.visita());
            var resultado = proponer(borrador, retirada.dia(), nueva, sesion);
            resultado.eliminadas.remove(actividad);
            estado.borradores.put(viaje, resultado);
            return respuesta(resultado);
        }
    }

    public EstadoEdicionDto reordenar(UUID usuario, UUID viaje, UUID propuesta,
            ReordenarActividadesDto entrada, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, entrada.revision());
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, entrada.revision());
            var originales = borrador.visitas.get(entrada.dia());
            if (originales == null) throw new IllegalArgumentException("El día no pertenece al viaje.");
            var nueva = secuencias.reordenar(originales, entrada.actividades());
            var resultado = proponer(borrador, entrada.dia(), nueva, sesion);
            resultado.ordenRestauracion.put(entrada.dia(), secuencias.ordenConEliminadas(
                    borrador.ordenRestauracion.get(entrada.dia()), nueva.stream().map(Visita::actividadId).toList()));
            estado.borradores.put(viaje, resultado);
            return respuesta(resultado);
        }
    }

    public AlternativasEdicionDto alternativas(UUID usuario, UUID viaje, UUID propuesta, UUID actividad,
            long revision, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, revision);
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, revision);
            var dia = diaDe(borrador, actividad);
            var original = visita(borrador, dia, actividad);
            comprobarNoObligatoria(borrador, original);
            var candidatos = borrador.base.itinerario().candidatos().stream()
                    .filter(c -> compatible(borrador, original, c))
                    .sorted(Comparator.comparingInt(c -> Math.abs(c.duracionMinutos() - original.candidato().duracionMinutos())))
                    .limit(6).toList();
            var opciones = new ArrayList<CandidatoEvaluadoDto>();
            var tiempo = TiempoEdicion.actual();
            for (var candidato : candidatos) {
                if (!tiempo.permiteConsulta()) {
                    borrador.ofrecidas.put(actividad, opciones.stream().map(c -> c.lugar().id()).toList());
                    return new AlternativasEdicionDto(List.copyOf(opciones),
                            "Se agotó el tiempo de comparación. Puedes elegir una opción ya comprobada o reintentar; la visita actual se conserva.");
                }
                var nueva = secuencias.sustituir(borrador.visitas.get(dia), actividad, candidato);
                var prueba = proponer(borrador, dia, nueva, sesion);
                var incidencias = new ArrayList<>(prueba.incidencias.getOrDefault(dia, List.of()));
                validador.validar(List.of(prueba.dias.get(dia)), prueba.base.configuracion().preferencias())
                        .incidencias().forEach(m -> incidencias.add(new IncidenciaEdicionDto(
                                Nivel.CONFLICTO, Codigo.PLAN_INCONSISTENTE, dia, null, null, m)));
                if (incidencias.stream().anyMatch(i -> i.codigo() == Codigo.CONSULTA_RUTAS_FALLIDA)) {
                    borrador.ofrecidas.put(actividad, opciones.stream().map(c -> c.lugar().id()).toList());
                    return new AlternativasEdicionDto(List.copyOf(opciones),
                            "No se pudieron comprobar todas las opciones porque falló el servicio de rutas. Puedes reintentar.");
                }
                if (incidencias.stream().noneMatch(i -> i.nivel() == Nivel.CONFLICTO)) opciones.add(candidato);
                if (opciones.size() == 3) break;
            }
            borrador.ofrecidas.put(actividad, opciones.stream().map(c -> c.lugar().id()).toList());
            return new AlternativasEdicionDto(List.copyOf(opciones), opciones.isEmpty()
                    ? "No encontramos alternativas adecuadas entre los candidatos guardados comparados. La visita original se conserva."
                    : "Opciones comprobadas con tu zona, preferencias, gasto y disponibilidad. Elige una y confirma para aplicarla a la propuesta.");
        }
    }

    public EstadoEdicionDto sustituir(UUID usuario, UUID viaje, UUID propuesta, UUID actividad,
            SustituirActividadDto entrada, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, entrada.revision());
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, entrada.revision());
            var dia = diaDe(borrador, actividad);
            var original = visita(borrador, dia, actividad);
            comprobarNoObligatoria(borrador, original);
            if (!borrador.ofrecidas.getOrDefault(actividad, List.of()).contains(entrada.candidatoId())) {
                throw new IllegalArgumentException("Solicita las alternativas y selecciona una opción comprobada.");
            }
            var candidato = borrador.base.itinerario().candidatos().stream()
                    .filter(c -> c.lugar().id().equals(entrada.candidatoId()) && compatible(borrador, original, c))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("La alternativa ya no es compatible."));
            var resultado = proponer(borrador, dia,
                    secuencias.sustituir(borrador.visitas.get(dia), actividad, candidato), sesion);
            estado.borradores.put(viaje, resultado);
            return respuesta(resultado);
        }
    }

    public EstadoEdicionDto recalcular(UUID usuario, UUID viaje, UUID propuesta,
            long revision, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, revision);
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, revision);
            if (!borrador.modificada) throw new IllegalArgumentException("No hay cambios que recalcular.");
            var actualizado = borrador;
            for (var dia : borrador.incidencias.keySet()) {
                actualizado = proponer(actualizado, dia, actualizado.visitas.get(dia), sesion);
            }
            estado.borradores.put(viaje, actualizado);
            return respuesta(actualizado);
        }
    }

    public ViajeActualizadoDto guardar(UUID usuario, UUID viaje, UUID propuesta,
            ConfirmarEdicionDto entrada, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, entrada.revision());
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, entrada.revision());
            var resultado = respuesta(borrador).propuesta();
            if (!borrador.modificada) throw new IllegalArgumentException("No hay cambios que guardar.");
            if (!resultado.puedeGuardar()) throw conflicto("Corrige los conflictos antes de guardar. Tus cambios pendientes se conservan.");
            if (!entrada.aceptarAvisos() && !resultado.incidencias().isEmpty()) {
                throw new IllegalArgumentException("Lee y acepta los avisos antes de guardar.");
            }
            var confirmado = escritura.actualizar(usuario, viaje,
                    new ActualizarViajeDto(borrador.base.resumen().titulo(), entrada.revision()),
                    borrador.base.configuracion(), resultado.itinerario());
            estado.borradores.remove(viaje);
            return confirmado;
        }
    }

    private Borrador proponer(Borrador anterior, LocalDate fecha, List<Visita> visitas, HttpSession sesion) {
        TiempoEdicion.actual().comprobar();
        var original = anterior.base.itinerario().dias().stream().filter(d -> d.fecha().equals(fecha)).findFirst().orElseThrow();
        var solicitudes = necesidades.obtener(anterior.base.configuracion(), original, visitas);
        var calculadas = rutas.resolver(anterior.base, solicitudes, sesion);
        var reconstruido = reconstruccion.reconstruir(original, visitas, anterior.base.configuracion().ritmo(), calculadas.rutas());
        TiempoEdicion.actual().comprobar();
        var nuevasVisitas = new LinkedHashMap<>(anterior.visitas);
        nuevasVisitas.put(fecha, List.copyOf(visitas));
        var dias = new LinkedHashMap<>(anterior.dias);
        dias.put(fecha, reconstruido.dia());
        var porDia = new LinkedHashMap<>(anterior.incidencias);
        var avisos = new ArrayList<>(reconstruido.incidencias());
        if (calculadas.consultaFallida() && avisos.stream().anyMatch(i ->
                i.codigo() == Codigo.RUTA_NO_DISPONIBLE || i.codigo() == Codigo.DISPONIBILIDAD_EXCEDIDA)) avisos.add(new IncidenciaEdicionDto(Nivel.CONFLICTO,
                Codigo.CONSULTA_RUTAS_FALLIDA, fecha, null, null,
                "El servicio de rutas no respondió correctamente. No se puede confirmar la viabilidad; reintenta la edición."));
        double limiteKm = Math.min(10.0, anterior.base.configuracion().preferencias().distanciaKm());
        for (var bloque : reconstruido.dia().bloques()) {
            if (bloque.tipo() == TipoBloqueItinerario.TRASLADO && bloque.distanciaMetros() > limiteKm * 1000) {
                avisos.add(new IncidenciaEdicionDto(Nivel.CONFLICTO, Codigo.DISTANCIA_EXCEDIDA,
                        fecha, null, bloque.destinoId(), "Un trayecto supera la distancia máxima de " + limiteKm + " km. Cambia el orden o la visita."));
            }
        }
        porDia.put(fecha, List.copyOf(avisos));
        var resultado = new Borrador(anterior.usuario, anterior.base, nuevasVisitas, dias, porDia, true);
        resultado.trazados.putAll(anterior.trazados);
        resultado.eliminadas.putAll(anterior.eliminadas);
        resultado.ordenRestauracion.putAll(anterior.ordenRestauracion);
        return resultado;
    }

    private EstadoEdicionDto respuesta(Borrador borrador) {
        var incidencias = incidencias(borrador);
        var base = borrador.base.itinerario();
        var advertencias = new LinkedHashSet<String>();
        // Los textos antiguos sin metadatos se conservan para no perder avisos.
        var anteriores = base.incidenciasEdicion().stream().map(i -> "Edici\u00f3n: " + i.mensaje()).toList();
        base.advertencias().stream().filter(a -> !anteriores.contains(a)).forEach(advertencias::add);
        incidencias.forEach(i -> advertencias.add("Edición: " + i.mensaje()));
        var plan = new ItinerarioGeneradoDto(List.copyOf(borrador.dias.values()), List.copyOf(advertencias),
                base.parcial() || !incidencias.isEmpty(), base.candidatosEvaluados(), base.candidatosEnMatriz(),
                base.candidatos(), base.evaluacionFallback(), incidencias);
        var geometrias = new LinkedHashMap<>(base.trazados());
        geometrias.putAll(borrador.trazados);
        plan = plan.conTrazados(TramosPlan.vigentes(borrador.base.configuracion(), plan, geometrias));
        var dias = borrador.visitas.entrySet().stream().map(e -> new DiaEditableDto(e.getKey(),
                e.getValue().stream().map(v -> new VisitaEditableDto(v.actividadId(), v.candidato(),
                        ReglasSeleccionLugares.estado(v.candidato().lugar(), borrador.base.configuracion()) == EstadoLugar.OBLIGATORIO)).toList())).toList();
        return new EstadoEdicionDto(new PropuestaEdicionDto(borrador.id,
                borrador.base.resumen().revision(), plan, incidencias), dias, borrador.modificada,
                borrador.eliminadas.values().stream().map(e -> new VisitaEliminadaDto(e.dia(),
                        new VisitaEditableDto(e.visita().actividadId(), e.visita().candidato(),
                                ReglasSeleccionLugares.estado(e.visita().candidato().lugar(), borrador.base.configuracion()) == EstadoLugar.OBLIGATORIO))).toList());
    }

    private List<IncidenciaEdicionDto> incidencias(Borrador borrador) {
        var resultado = new ArrayList<IncidenciaEdicionDto>();
        borrador.base.itinerario().incidenciasEdicion().stream()
                .filter(i -> i.dia() != null && !borrador.incidencias.containsKey(i.dia()))
                .forEach(resultado::add);
        borrador.incidencias.values().forEach(resultado::addAll);
        var validacion = validador.validar(List.copyOf(borrador.dias.values()), borrador.base.configuracion().preferencias());
        for (String mensaje : validacion.incidencias()) {
            String prefijoHorario = "Visita incompatible con el horario de la fuente: ";
            if (mensaje.startsWith(prefijoHorario) && resultado.stream().anyMatch(i ->
                    i.codigo() == Codigo.HORARIO_INCOMPATIBLE
                            && mensaje.substring(prefijoHorario.length()).equals(i.lugarId()))) continue;
            resultado.add(new IncidenciaEdicionDto(
                    Nivel.CONFLICTO, Codigo.PLAN_INCONSISTENTE, null, null, null, mensaje));
        }
        for (var seleccion : borrador.base.configuracion().selecciones()) {
            if (seleccion.estado() != EstadoLugar.OBLIGATORIO) continue;
            boolean presente = borrador.dias.values().stream().flatMap(d -> d.bloques().stream())
                    .anyMatch(b -> b.tipo() == TipoBloqueItinerario.VISITA && b.candidato() != null
                            && IdentidadLugares.mismoLugar(b.candidato().lugar(), seleccion.lugar()));
            if (!presente) resultado.add(new IncidenciaEdicionDto(Nivel.AVISO, Codigo.OBLIGATORIO_INCUMPLIDO,
                    null, null, seleccion.lugar().id(), "El obligatorio «" + seleccion.lugar().nombre()
                    + "» ya no está incluido. Su prioridad se conserva; confirma este incumplimiento si deseas guardar."));
        }
        return List.copyOf(resultado);
    }

    public ViajeGuardadoDto paraTrazados(UUID usuario, UUID viaje, UUID propuesta, long revision, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, revision);
        var estado = estado(sesion);
        synchronized (estado) {
            var borrador = obtener(estado, usuario, viaje, propuesta, revision);
            return new ViajeGuardadoDto(borrador.base.resumen(), borrador.base.configuracion(),
                    respuesta(borrador).propuesta().itinerario(), borrador.base.actividades());
        }
    }
    public void conservarTrazados(UUID usuario, UUID viaje, UUID propuesta, long revision,
            Map<String, GeometriaTramoDto> trazados, HttpSession sesion) {
        contexto.recuperar(usuario, viaje, revision);
        var estado = estado(sesion);
        synchronized (estado) { obtener(estado, usuario, viaje, propuesta, revision).trazados.putAll(trazados); }
    }

    private boolean compatible(Borrador borrador, Visita original, CandidatoEvaluadoDto candidato) {
        var lugar = candidato.lugar();
        var config = borrador.base.configuracion();
        if (lugar == null || candidato.duracionMinutos() <= 0 || !IdentidadLugares.coordenadasValidas(lugar)
                || !ReglasSeleccionLugares.compatible(lugar, config.preferencias())
                || ReglasSeleccionLugares.estado(lugar, config) == EstadoLugar.EVITADO
                || !IdentidadLugares.coordenadasValidas(original.candidato().lugar())) return false;
        if (borrador.visitas.values().stream().flatMap(List::stream)
                .anyMatch(v -> IdentidadLugares.mismoLugar(v.candidato().lugar(), lugar))) return false;
        if (borrador.dias.values().stream().flatMap(d -> d.bloques().stream())
                .anyMatch(b -> b.restaurante() != null && b.restaurante().id().equals(lugar.id()))) return false;
        var referencia = original.candidato().lugar();
        if (IdentidadLugares.distanciaMetros(referencia.latitud(), referencia.longitud(), lugar.latitud(), lugar.longitud()) > 10000) return false;
        var gasto = config.preferencias().nivelGasto();
        if (gasto == NivelGasto.ECONOMICO && candidato.coste() != CostLevel.FREE && candidato.coste() != CostLevel.LOW) return false;
        return gasto != NivelGasto.MODERADO || candidato.coste() != CostLevel.HIGH;
    }

    private static void comprobarNoObligatoria(Borrador borrador, Visita visita) {
        if (ReglasSeleccionLugares.estado(visita.candidato().lugar(), borrador.base.configuracion()) == EstadoLugar.OBLIGATORIO) {
            throw new IllegalArgumentException("Esta visita es obligatoria y debe conservarse. Puedes reordenarla; no se cambia su prioridad automáticamente.");
        }
    }

    private static LocalDate diaDe(Borrador borrador, UUID actividad) {
        return borrador.visitas.entrySet().stream().filter(e -> e.getValue().stream()
                .anyMatch(v -> v.actividadId().equals(actividad))).map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La visita no pertenece a esta propuesta."));
    }

    private static Visita visita(Borrador borrador, LocalDate dia, UUID actividad) {
        return borrador.visitas.get(dia).stream().filter(v -> v.actividadId().equals(actividad)).findFirst().orElseThrow();
    }

    private static Borrador obtener(Estado estado, UUID usuario, UUID viaje, UUID propuesta, long revision) {
        var borrador = estado.borradores.get(viaje);
        if (borrador == null || !borrador.usuario.equals(usuario) || !borrador.id.equals(propuesta)
                || borrador.base.resumen().revision() != revision || !Instant.now().isBefore(borrador.caduca)) {
            throw conflicto("La propuesta ha cambiado o caducado. Vuelve a abrir el editor; el viaje guardado permanece intacto.");
        }
        return borrador;
    }

    private synchronized Estado estado(HttpSession sesion) {
        var estado = (Estado) sesion.getAttribute(ATRIBUTO);
        if (estado == null) { estado = new Estado(); sesion.setAttribute(ATRIBUTO, estado); }
        return estado;
    }

    private static ResponseStatusException conflicto(String mensaje) { return new ResponseStatusException(HttpStatus.CONFLICT, mensaje); }
    private static class Estado { private final LinkedHashMap<UUID, Borrador> borradores = new LinkedHashMap<>(); }
    private record Eliminada(LocalDate dia, Visita visita) {}
    private static class Borrador {
        private final UUID id = UUID.randomUUID();
        private final Instant caduca = Instant.now().plusSeconds(1800);
        private final UUID usuario;
        private final ViajeGuardadoDto base;
        private final Map<LocalDate, List<Visita>> visitas;
        private final Map<LocalDate, DiaItinerarioDto> dias;
        private final Map<LocalDate, List<IncidenciaEdicionDto>> incidencias;
        private final boolean modificada;
        private final Map<UUID, List<String>> ofrecidas = new HashMap<>();
        private final Map<String, GeometriaTramoDto> trazados = new LinkedHashMap<>();
        private final Map<UUID, Eliminada> eliminadas = new LinkedHashMap<>();
        private final Map<LocalDate, List<UUID>> ordenRestauracion = new LinkedHashMap<>();
        private Borrador(UUID usuario, ViajeGuardadoDto base, Map<LocalDate, List<Visita>> visitas,
                Map<LocalDate, DiaItinerarioDto> dias, Map<LocalDate, List<IncidenciaEdicionDto>> incidencias, boolean modificada) {
            this.usuario = usuario; this.base = base; this.visitas = visitas; this.dias = dias;
            this.incidencias = incidencias; this.modificada = modificada;
            visitas.forEach((dia, lista) -> ordenRestauracion.put(dia, lista.stream().map(Visita::actividadId).toList()));
        }
    }
}
