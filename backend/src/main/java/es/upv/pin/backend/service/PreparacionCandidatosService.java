package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import es.upv.pin.backend.client.GeoapifyClient;
import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.dto.LugarGeoapifyDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import es.upv.pin.backend.model.SubcategoriaTuristica;

@Service
public class PreparacionCandidatosService {
    private static final int LIMITE_POR_CONSULTA = 100;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PreparacionCandidatosService.class);
    private static final Set<String> NOMBRES_DESCONOCIDOS = Set.of(
            "desconocido", "desconocida", "nombre desconocido", "sin nombre",
            "unknown", "unnamed", "no name", "n/a", "null", "undefined");
    private final GeoapifyClient cliente;
    private final ConfiguracionItinerarioService configuraciones;
    private final ClasificacionLugaresService clasificacion;

    public PreparacionCandidatosService(GeoapifyClient cliente, ConfiguracionItinerarioService configuraciones,
            ClasificacionLugaresService clasificacion) {
        this.cliente = cliente;
        this.configuraciones = configuraciones;
        this.clasificacion = clasificacion;
    }

    public List<LugarDto> preparar(ConfiguracionItinerarioDto entrada) {
        return preparar(entrada, "", Modo.COMPLETO);
    }

    public List<LugarDto> muestraInicial(ConfiguracionItinerarioDto entrada) {
        return preparar(entrada, "", Modo.INICIAL);
    }

    public List<LugarDto> buscar(ConfiguracionItinerarioDto entrada, String texto) {
        if (texto == null || texto.strip().length() < 3 || texto.length() > 100)
            throw new IllegalArgumentException("Escribe entre 3 y 100 caracteres para ampliar la búsqueda.");
        return preparar(entrada, texto.strip(), Modo.NOMBRE);
    }

    private List<LugarDto> preparar(ConfiguracionItinerarioDto entrada, String texto, Modo modo) {
        ConfiguracionItinerarioDto configuracion = configuraciones.validar(entrada);
        PreferenciasDto preferencias = configuracion.preferencias();
        List<CategoriaTuristica> intereses = preferencias.intereses().isEmpty()
                ? Arrays.asList(CategoriaTuristica.values()) : preferencias.intereses();

        // Un grupo por identificador: el mismo lugar puede aparecer en varias consultas.
        Map<String, List<LugarGeoapifyDto>> grupos = new LinkedHashMap<>();
        Map<String, List<String>> idsPorConsulta = new LinkedHashMap<>();
        int llamadas = 0;
        Map<String, Integer> consultas = new LinkedHashMap<>();
        for (CategoriaTuristica interes : intereses) {
            if (preferencias.categoriasExcluidas().contains(interes)) continue;
            for (String consulta : modo == Modo.INICIAL ? clasificacion.consultasIniciales(interes) : clasificacion.consultasPorInteres(interes)) {
                if (interes == CategoriaTuristica.OCIO_ENTRETENIMIENTO
                        && preferencias.subcategoriasExcluidas().contains(SubcategoriaTuristica.COMPRAS)
                        && consulta.startsWith("commercial.")) continue;
                consultas.putIfAbsent(consulta, modo == Modo.COMPLETO ? clasificacion.maxPaginas(consulta) : 1);
            }
        }
        if (modo == Modo.NOMBRE && !consultas.isEmpty()) {
            String categorias = consultas.keySet().stream().flatMap(c -> Arrays.stream(c.split(",")))
                    .distinct().collect(java.util.stream.Collectors.joining(","));
            consultas.clear();
            consultas.put(categorias, 1); // El nombre y el destino ya acotan la búsqueda.
        }
        for (var resultado : consultarEnParalelo(configuracion.destino(), consultas, texto)) {
            Set<String> ids = new LinkedHashSet<>();
            llamadas += resultado.paginas();
            for (var lugar : resultado.lugares()) {
                grupos.computeIfAbsent(lugar.place_id(), id -> new ArrayList<>()).add(lugar);
                ids.add(lugar.place_id());
            }
            idsPorConsulta.put(resultado.consulta(), List.copyOf(ids));
        }

        Set<String> idsCalles = new LinkedHashSet<>();
        if (modo == Modo.COMPLETO && intereses.contains(CategoriaTuristica.OCIO_ENTRETENIMIENTO)
                && !preferencias.categoriasExcluidas().contains(CategoriaTuristica.OCIO_ENTRETENIMIENTO)
                && !preferencias.subcategoriasExcluidas().contains(SubcategoriaTuristica.COMPRAS)) {
            var calles = new CallesComercialesService(cliente).buscar(configuracion.destino(),
                    grupos.values().stream().flatMap(List::stream).toList());
            for (var calle : calles) {
                grupos.computeIfAbsent(calle.place_id(), k -> new ArrayList<>()).add(calle);
                idsCalles.add(calle.place_id());
            }
            if (!idsCalles.isEmpty()) idsPorConsulta.put("calles comerciales localizadas", List.copyOf(idsCalles));
        }
        Map<String, String> alias = agruparEquivalentes(grupos, idsCalles);
        idsPorConsulta.replaceAll((consulta, ids) -> ids.stream().map(alias::get).distinct().toList());

        Map<String, LugarDto> admitidos = new LinkedHashMap<>();
        for (var entradaGrupo : grupos.entrySet()) {
            LugarDto lugar = convertir(entradaGrupo.getKey(), entradaGrupo.getValue(), idsCalles);
            if (lugar == null || !cumplePreferencias(Set.copyOf(lugar.categorias()), Set.copyOf(lugar.subcategorias()), preferencias)) {
                LOG.debug("Candidato {} descartado por nombre ausente o preferencias", entradaGrupo.getKey());
                continue;
            }
            admitidos.put(lugar.id(), lugar);
        }
        // Una ronda por grupo evita que cien resultados de la primera consulta
        // desplacen a los tipos y los intereses que se consultaron después.
        List<List<String>> listas = idsPorConsulta.values().stream()
                .map(ids -> ids.stream().filter(admitidos::containsKey)
                        .sorted(Comparator.comparingInt((String id) -> grupos.get(id).stream()
                                .mapToInt(clasificacion::prioridadBusqueda).max().orElse(0)).reversed())
                        .toList()).toList();
        Set<String> orden = intercalar(listas);
        // Se da un turno a cada interés. Dentro de Historia/Cultura se adelantan
        // los referentes por tipo sin quitar los turnos de Arte, Naturaleza, etc.
        List<List<String>> porInteres = intereses.stream()
                .filter(interes -> !preferencias.categoriasExcluidas().contains(interes))
                .map(interes -> orden.stream().filter(id -> admitidos.get(id).categorias().contains(interes))
                        .sorted(Comparator.comparingInt((String id) ->
                                interes == CategoriaTuristica.HISTORIA_ARQUITECTURA || interes == CategoriaTuristica.CULTURA_LOCAL
                                        ? grupos.get(id).stream().mapToInt(clasificacion::prioridadBusqueda).max().orElse(0)
                                        : 0).reversed()).toList()).toList();
        LOG.info("Candidatos {}: {} consultas (incluida caché), {} grupos, {} lugares únicos, {} admitidos tras preferencias",
                modo, llamadas, idsPorConsulta.size(), grupos.size(), admitidos.size());
        var lugares = intercalar(porInteres).stream().map(admitidos::get).toList();
        return modo == Modo.COMPLETO ? ReglasSeleccionLugares.aplicar(configuracion, lugares) : lugares;
    }

    private enum Modo { INICIAL, COMPLETO, NOMBRE }
    private record ResultadoConsulta(String consulta, List<LugarGeoapifyDto> lugares, int paginas) {}

    private List<ResultadoConsulta> consultarEnParalelo(es.upv.pin.backend.dto.DestinoDto destino, Map<String, Integer> consultas, String texto) {
        // Los hilos virtuales permiten esperar respuestas independientes. GeoapifyClient impone
        // el límite global de tres peticiones simultáneas y separa sus inicios entre usuarios.
        var ejecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        List<java.util.concurrent.Future<ResultadoConsulta>> tareas = new ArrayList<>();
        try {
            consultas.forEach((consulta, paginas) -> tareas.add(ejecutor.submit(() -> consultar(destino, consulta, paginas, texto))));
            List<ResultadoConsulta> resultados = new ArrayList<>();
            // Recoger en orden conserva la misma variedad y un resultado determinista.
            for (var tarea : tareas) resultados.add(tarea.get());
            return resultados;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se ha cancelado la búsqueda de lugares.");
        } catch (java.util.concurrent.ExecutionException error) {
            if (error.getCause() instanceof RuntimeException causa) throw causa;
            if (error.getCause() instanceof Error causa) throw causa;
            throw new IllegalStateException("No se han podido consultar los lugares.");
        } finally {
            tareas.forEach(tarea -> { if (!tarea.isDone()) tarea.cancel(true); });
            ejecutor.shutdownNow();
        }
    }

    private ResultadoConsulta consultar(es.upv.pin.backend.dto.DestinoDto destino, String consulta, int maxPaginas, String texto) {
        List<LugarGeoapifyDto> lugares = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int paginas = 0;
        for (int pagina = 0; pagina < maxPaginas; pagina++) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Se ha cancelado la búsqueda de lugares.");
            var respuesta = cliente.buscarLugares(destino, consulta, texto.isEmpty() ? LIMITE_POR_CONSULTA : 30,
                    pagina * LIMITE_POR_CONSULTA, texto);
            paginas++;
            int anteriores = ids.size();
            for (var feature : respuesta.features()) {
                if (feature == null || feature.properties() == null)
                    throw new IllegalStateException("El servicio de lugares ha devuelto un resultado no válido.");
                var lugar = feature.properties();
                if (lugar.place_id() == null || lugar.place_id().isBlank()) continue;
                lugares.add(lugar);
                ids.add(lugar.place_id());
            }
            if (respuesta.features().size() < LIMITE_POR_CONSULTA || (pagina > 0 && ids.size() == anteriores)) break;
        }
        return new ResultadoConsulta(consulta, List.copyOf(lugares), paginas);
    }

    private Map<String, String> agruparEquivalentes(Map<String, List<LugarGeoapifyDto>> grupos, Set<String> idsCalles) {
        Map<String, String> alias = new LinkedHashMap<>();
        Map<String, List<String>> porNombre = new LinkedHashMap<>();
        Map<String, LugarDto> representantes = new LinkedHashMap<>();
        for (var entrada : new ArrayList<>(grupos.entrySet())) {
            String id = entrada.getKey();
            LugarDto lugar = convertir(id, entrada.getValue(), idsCalles);
            alias.put(id, id);
            if (lugar == null) continue;
            String nombre = IdentidadLugares.nombreComparable(lugar.nombre());
            List<String> similares = porNombre.computeIfAbsent(nombre, k -> new ArrayList<>());
            String canonico = similares.stream().filter(c -> IdentidadLugares.mismoLugar(lugar, representantes.get(c)))
                    .findFirst().orElse(null);
            if (canonico == null) {
                similares.add(id);
                representantes.put(id, lugar);
            } else {
                grupos.get(canonico).addAll(entrada.getValue());
                grupos.remove(id);
                alias.put(id, canonico);
                // Todas las etiquetas se reúnen ANTES de aplicar las exclusiones.
                representantes.put(canonico, convertir(canonico, grupos.get(canonico), idsCalles));
                LOG.debug("Candidatos equivalentes {} y {} agrupados en una visita", canonico, id);
            }
        }
        return alias;
    }

    private LugarDto convertir(String id, List<LugarGeoapifyDto> grupo, Set<String> idsCalles) {
        String nombre = null, direccion = null, horario = null;
        Set<String> horarios = new LinkedHashSet<>();
        Double latitud = null, longitud = null;
        Set<CategoriaTuristica> categorias = EnumSet.noneOf(CategoriaTuristica.class);
        Set<SubcategoriaTuristica> subcategorias = EnumSet.noneOf(SubcategoriaTuristica.class);
        boolean calleComercial = false, descartadoOcio = false;
        for (LugarGeoapifyDto lugar : grupo) {
            categorias.addAll(clasificacion.categorias(lugar));
            subcategorias.addAll(clasificacion.subcategorias(lugar));
            calleComercial |= idsCalles.contains(lugar.place_id());
            descartadoOcio |= clasificacion.descartadoParaOcio(lugar);
            if (nombre == null) nombre = nombreValido(lugar.name());
            if (nombre == null && idsCalles.contains(lugar.place_id())) nombre = nombreValido(lugar.street());
            if (direccion == null) direccion = textoOpcional(lugar.formatted());
            String publicado = horarioDisponible(lugar);
            if (publicado != null) horarios.add(publicado);
            if (latitud == null && coordenadasValidas(lugar)) {
                latitud = lugar.lat();
                longitud = lugar.lon();
            }
        }
        if (calleComercial) {
            categorias.add(CategoriaTuristica.OCIO_ENTRETENIMIENTO);
            subcategorias.add(SubcategoriaTuristica.COMPRAS);
        }
        if (descartadoOcio) {
            categorias.remove(CategoriaTuristica.OCIO_ENTRETENIMIENTO);
            subcategorias.removeIf(s -> s.getCategoria() == CategoriaTuristica.OCIO_ENTRETENIMIENTO);
        }
        // Dos horarios incompatibles no se convierten en una disponibilidad confirmada.
        if (horarios.size() == 1) horario = horarios.iterator().next();
        var tipos = grupo.stream().map(clasificacion::tipo).toList();
        var tipo = calleComercial ? es.upv.pin.backend.model.TipoLugar.CALLE_COMERCIAL
                : tipos.contains(es.upv.pin.backend.model.TipoLugar.PLAZA) ? es.upv.pin.backend.model.TipoLugar.PLAZA
                : tipos.stream().filter(t -> t != es.upv.pin.backend.model.TipoLugar.OTRO)
                    .findFirst().orElse(es.upv.pin.backend.model.TipoLugar.OTRO);
        return nombre == null ? null : new LugarDto(id, nombre, direccion, latitud, longitud,
                List.copyOf(categorias), List.copyOf(subcategorias), horario, tipo,
                grupo.stream().anyMatch(clasificacion::referenciaDocumentada),
                grupo.stream().filter(l -> l.name_international() != null)
                        .mapToInt(l -> (int) l.name_international().values().stream().filter(n -> n != null && !n.isBlank()).count())
                        .max().orElse(0));
    }

    private Set<String> intercalar(List<List<String>> listas) {
        Set<String> orden = new LinkedHashSet<>();
        int mayor = listas.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < mayor; i++) {
            for (List<String> lista : listas) {
                if (i < lista.size()) orden.add(lista.get(i));
            }
        }
        return orden;
    }

    private String nombreValido(String valor) {
        String nombre = textoOpcional(valor);
        if (nombre == null || NOMBRES_DESCONOCIDOS.contains(nombre.toLowerCase(Locale.ROOT))) return null;
        return nombre;
    }

    private String horarioDisponible(LugarGeoapifyDto lugar) {
        String horario = textoOpcional(lugar.opening_hours());
        if (horario != null) return horario;
        if (lugar.datasource() == null || lugar.datasource().raw() == null) return null;
        return textoOpcional(lugar.datasource().raw().opening_hours());
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private boolean coordenadasValidas(LugarGeoapifyDto lugar) {
        return lugar.lat() != null && lugar.lon() != null
                && Double.isFinite(lugar.lat()) && Double.isFinite(lugar.lon())
                && lugar.lat() >= -90 && lugar.lat() <= 90
                && lugar.lon() >= -180 && lugar.lon() <= 180;
    }

    private boolean cumplePreferencias(Set<CategoriaTuristica> categorias,
            Set<SubcategoriaTuristica> subcategorias, PreferenciasDto preferencias) {
        if (categorias.isEmpty()) return false;
        for (CategoriaTuristica categoria : preferencias.categoriasExcluidas()) {
            if (categorias.contains(categoria)) return false;
        }
        for (SubcategoriaTuristica subcategoria : preferencias.subcategoriasExcluidas()) {
            if (subcategorias.contains(subcategoria)) return false;
        }
        if (preferencias.intereses().isEmpty()) return true;
        for (CategoriaTuristica interes : preferencias.intereses()) {
            if (categorias.contains(interes)) return true;
        }
        return false;
    }
}
