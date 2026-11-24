package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.ConfiguracionItinerarioDto;
import es.upv.pin.backend.dto.ConsumoGeneracionDto;
import es.upv.pin.backend.dto.ItinerarioGeneradoDto;
import es.upv.pin.backend.dto.PreferenciasDto;
import es.upv.pin.backend.exception.ControlGeneracionException;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ControlGeneracionService {
    private static final String ATRIBUTO_SESION = ControlGeneracionService.class.getName();
    private final GeneracionItinerarioService generacion;
    private final ConfiguracionItinerarioService configuraciones;
    private final int limite;
    private final Duration ventana;
    private final Duration vigencia;
    private final Clock reloj;

    @Autowired
    public ControlGeneracionService(GeneracionItinerarioService generacion,
            ConfiguracionItinerarioService configuraciones,
            @Value("${itinerarios.consumo.max-generaciones:3}") int limite,
            @Value("${itinerarios.consumo.ventana-minutos:60}") int ventanaMinutos,
            @Value("${itinerarios.consumo.vigencia-minutos:15}") int vigenciaMinutos) {
        this(generacion, configuraciones, limite, ventanaMinutos, vigenciaMinutos, Clock.systemUTC());
    }

    ControlGeneracionService(GeneracionItinerarioService generacion,
            ConfiguracionItinerarioService configuraciones, int limite,
            int ventanaMinutos, int vigenciaMinutos, Clock reloj) {
        if (limite < 1 || ventanaMinutos < 1 || vigenciaMinutos < 1)
            throw new IllegalArgumentException("Los límites de generación deben ser positivos.");
        this.generacion = generacion;
        this.configuraciones = configuraciones;
        this.limite = limite;
        this.ventana = Duration.ofMinutes(ventanaMinutos);
        this.vigencia = Duration.ofMinutes(vigenciaMinutos);
        this.reloj = reloj;
    }

    public ConsumoGeneracionDto consultar(HttpSession sesion) {
        EstadoSesion estado = estadoDe(sesion);
        synchronized (estado) {
            actualizar(estado, reloj.instant());
            return new ConsumoGeneracionDto(limite, limite - estado.usadas,
                    estado.inicioPeriodo.plus(ventana), estado.enCurso);
        }
    }

    public Resultado generar(ConfiguracionItinerarioDto entrada, HttpSession sesion) {
        // La validación es local. Un formulario inválido no consume una generación.
        ConfiguracionItinerarioDto configuracion = normalizar(configuraciones.validar(entrada));
        EstadoSesion estado = estadoDe(sesion);
        synchronized (estado) {
            Instant ahora = reloj.instant();
            actualizar(estado, ahora);
            // Consultar el último resultado sigue permitido incluso sin cuota restante.
            if (estado.resultado != null && configuracion.equals(estado.configuracion))
                return new Resultado(estado.resultado, true, estado.vigenteHasta);
            if (estado.enCurso) throw new ControlGeneracionException(HttpStatus.CONFLICT,
                    "Ya estamos preparando un itinerario en esta sesión. Espera a que termine y vuelve a intentarlo.", 2);
            if (estado.usadas >= limite) throw new ControlGeneracionException(HttpStatus.TOO_MANY_REQUESTS,
                    "Has alcanzado el límite de generaciones de esta sesión. Espera a que se renueve o consulta el último itinerario vigente sin cambiar sus datos.",
                    Math.max(1, Duration.between(ahora, estado.inicioPeriodo.plus(ventana)).toSeconds() + 1));
            // La reserva es atómica: dos peticiones no pueden iniciar el mismo trabajo.
            estado.enCurso = true;
            estado.usadas++;
        }

        try {
            // Las llamadas externas se ejecutan fuera del bloqueo de la sesión.
            ItinerarioGeneradoDto resultado = Objects.requireNonNull(generacion.generar(configuracion));
            synchronized (estado) {
                estado.configuracion = configuracion;
                estado.resultado = resultado;
                estado.vigenteHasta = reloj.instant().plus(vigencia);
                return new Resultado(resultado, false, estado.vigenteHasta);
            }
        } finally {
            // Un fallo no deja la sesión bloqueada ni borra un resultado anterior.
            // La ejecución admitida cuenta: puede haber consumido proveedores.
            synchronized (estado) { estado.enCurso = false; }
        }
    }

    private synchronized EstadoSesion estadoDe(HttpSession sesion) {
        EstadoSesion estado = (EstadoSesion) sesion.getAttribute(ATRIBUTO_SESION);
        if (estado == null) {
            estado = new EstadoSesion();
            sesion.setAttribute(ATRIBUTO_SESION, estado);
        }
        return estado;
    }

    private void actualizar(EstadoSesion estado, Instant ahora) {
        if (estado.inicioPeriodo == null || !ahora.isBefore(estado.inicioPeriodo.plus(ventana))) {
            estado.inicioPeriodo = ahora;
            estado.usadas = 0;
        }
        if (estado.vigenteHasta != null && !ahora.isBefore(estado.vigenteHasta)) {
            estado.configuracion = null;
            estado.resultado = null;
            estado.vigenteHasta = null;
        }
    }

    private static ConfiguracionItinerarioDto normalizar(ConfiguracionItinerarioDto configuracion) {
        PreferenciasDto p = configuracion.preferencias();
        PreferenciasDto ordenadas = new PreferenciasDto(p.intereses().stream().sorted().toList(),
                p.categoriasExcluidas().stream().sorted().toList(),
                p.subcategoriasExcluidas().stream().sorted().toList(), p.nivelGasto(), p.distanciaKm());
        return new ConfiguracionItinerarioDto(
                configuracion.destino(),
                configuracion.disponibilidad(),
                configuracion.alojamiento(),
                ordenadas,
                configuracion.ritmo(),
                configuracion.alojamientoLocalizado());
    }

    public record Resultado(ItinerarioGeneradoDto itinerario, boolean reutilizado, Instant vigenteHasta) {}

    private static class EstadoSesion {
        private Instant inicioPeriodo;
        private int usadas;
        private boolean enCurso;
        private ConfiguracionItinerarioDto configuracion;
        private ItinerarioGeneradoDto resultado;
        private Instant vigenteHasta;
    }
}
