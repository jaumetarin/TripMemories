package es.upv.pin.backend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
    private static final int LIMITE_POR_INTERES = 100;
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
        ConfiguracionItinerarioDto configuracion = configuraciones.validar(entrada);
        PreferenciasDto preferencias = configuracion.preferencias();
        List<CategoriaTuristica> intereses = preferencias.intereses().isEmpty()
                ? Arrays.asList(CategoriaTuristica.values()) : preferencias.intereses();

        // Un grupo por identificador: el mismo lugar puede aparecer en varias consultas.
        Map<String, List<LugarGeoapifyDto>> grupos = new LinkedHashMap<>();
        for (CategoriaTuristica interes : intereses) {
            if (preferencias.categoriasExcluidas().contains(interes)) continue;
            var respuesta = cliente.buscarLugares(configuracion.destino().id(),
                    clasificacion.categoriasConsulta(interes), LIMITE_POR_INTERES);
            for (var feature : respuesta.features()) {
                if (feature == null || feature.properties() == null) {
                    throw new IllegalStateException("El servicio de lugares ha devuelto un resultado no válido.");
                }
                LugarGeoapifyDto lugar = feature.properties();
                if (lugar.place_id() == null || lugar.place_id().isBlank()) continue;
                grupos.computeIfAbsent(lugar.place_id(), id -> new ArrayList<>()).add(lugar);
            }
        }

        List<LugarDto> resultados = new ArrayList<>();
        for (List<LugarGeoapifyDto> grupo : grupos.values()) {
            if (!cumplePreferencias(grupo, preferencias)) continue;
            String nombre = null;
            String direccion = null;
            String horario = null;
            for (LugarGeoapifyDto lugar : grupo) {
                if (nombre == null) nombre = nombreValido(lugar.name());
                if (direccion == null) direccion = textoOpcional(lugar.formatted());
                if (horario == null) horario = horarioDisponible(lugar);
            }
            if (nombre != null) resultados.add(new LugarDto(nombre, direccion, horario));
        }
        return List.copyOf(resultados);
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

    private boolean cumplePreferencias(List<LugarGeoapifyDto> grupo, PreferenciasDto preferencias) {
        Set<CategoriaTuristica> categorias = EnumSet.noneOf(CategoriaTuristica.class);
        Set<SubcategoriaTuristica> subcategorias = EnumSet.noneOf(SubcategoriaTuristica.class);
        // Filtramos después de reunir las etiquetas de todas las copias del lugar.
        for (LugarGeoapifyDto lugar : grupo) {
            categorias.addAll(clasificacion.categorias(lugar));
            subcategorias.addAll(clasificacion.subcategorias(lugar));
        }
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
