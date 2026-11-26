package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** El catálogo guarda datos de Geoapify en la sesión, no las decisiones del usuario. */
@Service
public class CatalogoLugaresService {
    private static final String ATRIBUTO = CatalogoLugaresService.class.getName();
    private final PreparacionCandidatosService preparacion;
    private final ConfiguracionItinerarioService configuraciones;
    private final MuestraLugaresService muestras;
    private final LimiteProveedorService limiteProveedor;

    public CatalogoLugaresService(PreparacionCandidatosService preparacion, ConfiguracionItinerarioService configuraciones,
            MuestraLugaresService muestras, LimiteProveedorService limiteProveedor) {
        this.preparacion = preparacion;
        this.configuraciones = configuraciones;
        this.muestras = muestras;
        this.limiteProveedor = limiteProveedor;
    }

    public CatalogoLugaresDto buscar(BusquedaLugaresDto consulta, HttpSession sesion) {
        if (consulta == null) throw new IllegalArgumentException("Envía el destino y las preferencias.");
        var base = base(consulta.configuracion());
        String texto = consulta.texto() == null ? "" : consulta.texto().strip();
        if (texto.length() > 100) throw new IllegalArgumentException("El texto de búsqueda no puede superar 100 caracteres.");
        if (consulta.ampliar() && texto.length() < 3)
            throw new IllegalArgumentException("Escribe al menos tres caracteres para buscar más lugares.");
        if (consulta.ampliar() && consulta.completarCatalogo())
            throw new IllegalArgumentException("Elige buscar por nombre o cargar más lugares, una acción cada vez.");
        int limite = consulta.limiteResultados() == 0 ? 30 : consulta.limiteResultados();
        if (limite < 1 || limite > 150) throw new IllegalArgumentException("Muestra entre 1 y 150 resultados y escribe un nombre para acotar la búsqueda.");
        var catalogo = catalogo(sesion, base.destino().id());
        synchronized (catalogo) {
            String filtros = filtros(base);
            var protegidos = new HashSet<String>();
            if (consulta.configuracion().selecciones() != null) consulta.configuracion().selecciones().stream()
                    .filter(s -> s != null && s.lugar() != null).map(s -> localizar(catalogo, s.lugar().id()))
                    .filter(Objects::nonNull).forEach(l -> protegidos.add(l.id()));
            String completa = filtros + "|completo";
            if (consulta.completarCatalogo()) {
                cargar(catalogo, completa, () -> consultaExterna(() -> preparacion.preparar(base)), protegidos);
            } else if (consulta.ampliar()) {
                cargar(catalogo, filtros + "|nombre|" + texto, () -> consultaExterna(() -> preparacion.buscar(base, texto)), protegidos);
            } else if (!vigente(catalogo, completa)) {
                cargar(catalogo, filtros + "|inicial", () -> consultaExterna(() -> preparacion.muestraInicial(base)), protegidos);
            }
            List<String> avisos = new ArrayList<>();
            var selecciones = revisar(consulta.configuracion(), base, catalogo, avisos, false);
            var elegidos = selecciones.stream().map(s -> s.lugar().id()).collect(java.util.stream.Collectors.toSet());
            String patron = IdentidadLugares.normalizar(texto);
            var lugares = catalogo.lugares.values().stream()
                    .filter(l -> ReglasSeleccionLugares.compatible(l, base.preferencias()))
                    .filter(l -> !elegidos.contains(l.id()))
                    .filter(l -> patron.isEmpty() || IdentidadLugares.normalizar(l.nombre() + " " + l.direccion()).contains(patron))
                    .toList();
            var muestra = muestras.seleccionar(lugares, base, limite, patron.isEmpty());
            return new CatalogoLugaresDto(muestra.lugares(), selecciones, List.copyOf(avisos),
                    lugares.size() > muestra.lugares().size(), ReglasSeleccionLugares.MAX_SELECCIONES, vigente(catalogo, completa),
                    muestra.reparto(), muestra.categorias());
        }
    }

    public CatalogoLugaresDto revisar(ConfiguracionItinerarioDto entrada, HttpSession sesion) {
        var base = base(entrada);
        var catalogo = catalogo(sesion, base.destino().id());
        synchronized (catalogo) {
            List<String> avisos = new ArrayList<>();
            return new CatalogoLugaresDto(List.of(), revisar(entrada, base, catalogo, avisos, false),
                    List.copyOf(avisos), false, ReglasSeleccionLugares.MAX_SELECCIONES);
        }
    }

    /** Solo recibe la configuración persistida de un viaje cuya propiedad ya se comprobó. */
    public void restaurarSeleccionGuardada(java.util.UUID viajeId, long revision,
            ConfiguracionItinerarioDto configuracion, HttpSession sesion) {
        if (configuracion.selecciones() == null || configuracion.selecciones().isEmpty()) return;
        var catalogo = catalogo(sesion, configuracion.destino().id());
        var lugares = configuracion.selecciones().stream().map(SeleccionLugarDto::lugar).toList();
        var protegidos = lugares.stream().map(LugarDto::id).collect(java.util.stream.Collectors.toSet());
        synchronized (catalogo) {
            cargar(catalogo, "viaje-guardado:" + viajeId + ":" + revision, () -> lugares, protegidos);
        }
    }

    public CatalogoLugaresDto cambiar(CambioSeleccionLugarDto cambio, HttpSession sesion) {
        if (cambio == null || cambio.lugarId() == null || cambio.lugarId().isBlank())
            throw new IllegalArgumentException("Indica el lugar que quieres modificar.");
        var base = base(cambio.configuracion());
        var catalogo = catalogo(sesion, base.destino().id());
        synchronized (catalogo) {
            List<String> avisos = new ArrayList<>();
            List<SeleccionLugarDto> selecciones = new ArrayList<>(revisar(cambio.configuracion(), base, catalogo, avisos, false));
            LugarDto lugar = localizar(catalogo, cambio.lugarId());
            if (cambio.estado() != null && (lugar == null || !ReglasSeleccionLugares.compatible(lugar, base.preferencias())))
                throw new IllegalArgumentException("Busca un lugar compatible en el destino antes de seleccionarlo.");
            selecciones.removeIf(s -> s.lugar().id().equals(cambio.lugarId())
                    || (lugar != null && IdentidadLugares.mismoLugar(s.lugar(), lugar)));
            if (cambio.estado() != null) selecciones.add(new SeleccionLugarDto(lugar, cambio.estado()));
            return new CatalogoLugaresDto(List.of(), ReglasSeleccionLugares.validar(selecciones, base.preferencias()),
                    List.copyOf(avisos), false, ReglasSeleccionLugares.MAX_SELECCIONES);
        }
    }

    /** La confirmación y la generación resuelven los datos desde el catálogo del servidor. */
    public ConfiguracionItinerarioDto resolver(ConfiguracionItinerarioDto entrada, HttpSession sesion) {
        if (entrada == null || entrada.selecciones() == null || entrada.selecciones().isEmpty()) return entrada;
        var base = base(entrada);
        var catalogo = catalogo(sesion, base.destino().id());
        synchronized (catalogo) {
            var selecciones = revisar(entrada, base, catalogo, new ArrayList<>(), true);
            return new ConfiguracionItinerarioDto(
                    base.destino(),
                    base.disponibilidad(),
                    base.alojamiento(),
                    base.preferencias(),
                    base.ritmo(),
                    base.alojamientoLocalizado(),
                    selecciones);
        }
    }

    private ConfiguracionItinerarioDto base(ConfiguracionItinerarioDto entrada) {
        if (entrada == null) {
            throw new IllegalArgumentException("Envía la configuración del viaje.");
        }

        return configuraciones.validar(new ConfiguracionItinerarioDto(
                entrada.destino(),
                entrada.disponibilidad(),
                entrada.alojamiento(),
                entrada.preferencias(),
                entrada.ritmo(),
                entrada.alojamientoLocalizado(),
                List.of()));
    }

    private List<SeleccionLugarDto> revisar(ConfiguracionItinerarioDto entrada, ConfiguracionItinerarioDto base,
            Catalogo catalogo, List<String> avisos, boolean estricto) {
        List<SeleccionLugarDto> resultado = new ArrayList<>();
        var actuales = entrada.selecciones() == null ? List.<SeleccionLugarDto>of() : entrada.selecciones();
        if (actuales.size() > 200) throw new IllegalArgumentException("Se han enviado demasiadas selecciones.");
        for (var seleccion : actuales) {
            if (seleccion == null || seleccion.lugar() == null || seleccion.estado() == null)
                throw new IllegalArgumentException("Cada lugar necesita un único estado válido.");
            var lugar = localizar(catalogo, seleccion.lugar().id());
            if (lugar == null || !ReglasSeleccionLugares.compatible(lugar, base.preferencias())) {
                if (estricto) throw new IllegalArgumentException("Revisa los lugares: alguno ya no está disponible para esta configuración o sesión.");
                avisos.add("Se ha retirado «" + seleccion.lugar().nombre() + "» porque ya no es compatible con el destino o las preferencias.");
            } else resultado.add(new SeleccionLugarDto(lugar, seleccion.estado()));
        }
        return ReglasSeleccionLugares.validar(resultado, base.preferencias());
    }

    private List<LugarDto> consultaExterna(java.util.function.Supplier<List<LugarDto>> consulta) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof es.upv.pin.backend.model.UsuarioAutenticado usuario) {
            long espera = limiteProveedor.reservar(usuario.getId(), "lugares", 20);
            if (espera > 0) throw new es.upv.pin.backend.exception.ControlGeneracionException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Has alcanzado el limite temporal de busquedas de lugares.", espera);
        }
        return consulta.get();
    }

    private void cargar(Catalogo catalogo, String clave, java.util.function.Supplier<List<LugarDto>> consulta,
            Set<String> protegidos) {
        if (vigente(catalogo, clave)) return;
        var lugares = consulta.get();
        for (var nuevo : lugares) {
            String nombre = IdentidadLugares.nombreComparable(nuevo.nombre());
            var similares = catalogo.porNombre.computeIfAbsent(nombre, k -> new ArrayList<>());
            LugarDto existente = localizar(catalogo, nuevo.id());
            if (existente == null) existente = similares.stream().map(catalogo.lugares::get).filter(Objects::nonNull)
                    .filter(l -> IdentidadLugares.mismoLugar(l, nuevo)).findFirst().orElse(null);
            LugarDto canonico = existente == null ? nuevo : unir(existente, nuevo);
            catalogo.lugares.put(canonico.id(), canonico);
            if (!similares.contains(canonico.id())) similares.add(canonico.id());
            catalogo.alias.put(nuevo.id(), canonico.id());
        }
        if (catalogo.lugares.size() > 4000) {
            // Una búsqueda nueva no invalida los lugares que siguen seleccionados.
            var antiguos = catalogo.lugares.keySet().iterator();
            while (catalogo.lugares.size() > 4000 && antiguos.hasNext()) {
                if (!protegidos.contains(antiguos.next())) antiguos.remove();
            }
            catalogo.alias.entrySet().removeIf(e -> !catalogo.lugares.containsKey(e.getValue()));
            catalogo.porNombre.values().forEach(ids -> ids.removeIf(id -> !catalogo.lugares.containsKey(id)));
            catalogo.porNombre.values().removeIf(List::isEmpty);
            catalogo.consultas.clear();
        }
        catalogo.consultas.put(clave, Instant.now().plusSeconds(900));
        while (catalogo.consultas.size() > 32) catalogo.consultas.remove(catalogo.consultas.keySet().iterator().next());
    }

    private static boolean vigente(Catalogo catalogo, String clave) {
        return catalogo.consultas.getOrDefault(clave, Instant.EPOCH).isAfter(Instant.now());
    }

    private static LugarDto unir(LugarDto a, LugarDto b) {
        Set<es.upv.pin.backend.model.CategoriaTuristica> categorias = new TreeSet<>(a.categorias());
        categorias.addAll(b.categorias());
        Set<es.upv.pin.backend.model.SubcategoriaTuristica> subcategorias = new TreeSet<>(a.subcategorias());
        subcategorias.addAll(b.subcategorias());
        String horario = a.horario() == null ? b.horario() : b.horario() == null || a.horario().equals(b.horario()) ? a.horario() : null;
        return new LugarDto(a.id(), a.nombre(), b.direccion() == null ? a.direccion() : b.direccion(),
                IdentidadLugares.coordenadasValidas(b) ? b.latitud() : a.latitud(),
                IdentidadLugares.coordenadasValidas(b) ? b.longitud() : a.longitud(), List.copyOf(categorias), List.copyOf(subcategorias), horario,
                b.tipo() == es.upv.pin.backend.model.TipoLugar.PLAZA || a.tipo() == es.upv.pin.backend.model.TipoLugar.OTRO ? b.tipo() : a.tipo(),
                a.referenciaDocumentada() || b.referenciaDocumentada(), Math.max(a.nombresInternacionales(), b.nombresInternacionales()));
    }

    private static LugarDto localizar(Catalogo catalogo, String id) {
        return catalogo.lugares.get(catalogo.alias.getOrDefault(id, id));
    }

    private static String filtros(ConfiguracionItinerarioDto c) {
        var p = c.preferencias();
        return p.intereses().stream().sorted().toList() + "|" + p.categoriasExcluidas().stream().sorted().toList()
                + "|" + p.subcategoriasExcluidas().stream().sorted().toList();
    }

    @SuppressWarnings("unchecked")
    private synchronized Catalogo catalogo(HttpSession sesion, String destino) {
        var catalogos = (Map<String, Catalogo>) sesion.getAttribute(ATRIBUTO);
        if (catalogos == null) { catalogos = new LinkedHashMap<>(); sesion.setAttribute(ATRIBUTO, catalogos); }
        var resultado = catalogos.computeIfAbsent(destino, k -> new Catalogo());
        while (catalogos.size() > 3) catalogos.remove(catalogos.keySet().iterator().next());
        return resultado;
    }

    private static class Catalogo {
        private final Map<String, LugarDto> lugares = new LinkedHashMap<>();
        private final Map<String, String> alias = new LinkedHashMap<>();
        private final Map<String, List<String>> porNombre = new HashMap<>();
        private final Map<String, Instant> consultas = new LinkedHashMap<>();
    }
}
