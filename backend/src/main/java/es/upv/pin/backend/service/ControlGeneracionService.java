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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ControlGeneracionService {
    private static final String ATRIBUTO_SESION = ControlGeneracionService.class.getName();
    private final GeneracionItinerarioService generacion;
    private final ConfiguracionItinerarioService configuraciones;
    private final CuotaGeneracionService cuota;
    private final Duration vigencia;
    private final Clock reloj = Clock.systemUTC();

    public ControlGeneracionService(GeneracionItinerarioService generacion,
            ConfiguracionItinerarioService configuraciones, CuotaGeneracionService cuota,
            @Value("${itinerarios.consumo.vigencia-minutos:15}") int vigenciaMinutos) {
        this.generacion = generacion;
        this.configuraciones = configuraciones;
        this.cuota = cuota;
        if (vigenciaMinutos < 1) throw new IllegalArgumentException("La vigencia debe ser positiva.");
        this.vigencia = Duration.ofMinutes(vigenciaMinutos);
    }

    public ConsumoGeneracionDto consultar(UUID usuario, HttpSession sesion) {
        EstadoSesion estado = estadoDe(sesion);
        var consumo = cuota.consultar(usuario);
        synchronized (estado) {
            return new ConsumoGeneracionDto(consumo.limite(), consumo.restantes(), consumo.reinicio(), estado.enCurso,
                    consumo.limiteMensual(), consumo.restantesMensuales(), consumo.reinicioMensual());
        }
    }

    public Resultado generar(ConfiguracionItinerarioDto entrada, UUID usuario, HttpSession sesion) {
        // La validación es local. Un formulario inválido no consume una generación.
        ConfiguracionItinerarioDto configuracion = normalizar(configuraciones.validar(entrada));
        EstadoSesion estado = estadoDe(sesion);
        synchronized (estado) {
            Instant ahora = reloj.instant();
            actualizar(estado, ahora);
            // Consultar el último resultado sigue permitido incluso sin cuota restante.
            if (estado.resultado != null && configuracion.equals(estado.configuracion)) {
                return new Resultado(
                        estado.resultado,
                        true,
                        estado.vigenteHasta,
                        estado.guardable.id()
                );
            }
            if (estado.enCurso) throw new ControlGeneracionException(HttpStatus.CONFLICT,
                    "Ya estamos preparando un itinerario en esta sesión. Espera a que termine y vuelve a intentarlo.", 2);
            cuota.reservar(usuario);
            estado.enCurso = true;
        }

        try {
            // Las llamadas externas se ejecutan fuera del bloqueo de la sesión.
            ItinerarioGeneradoDto resultado = Objects.requireNonNull(generacion.generar(configuracion));
            synchronized (estado) {
                estado.configuracion = configuracion;
                estado.resultado = resultado;
                estado.vigenteHasta = reloj.instant().plus(vigencia);
                estado.guardable = new GeneracionGuardable(
                        UUID.randomUUID(),
                        configuracion,
                        resultado
                );
                return new Resultado(
                        resultado,
                        false,
                        estado.vigenteHasta,
                        estado.guardable.id()
                );
            }
        } finally {
            // Un fallo no deja la sesión bloqueada ni borra un resultado anterior.
            // La ejecución admitida cuenta: puede haber consumido proveedores.
            synchronized (estado) { estado.enCurso = false; }
        }
    }

    public GeneracionGuardable obtenerParaGuardar(
            UUID generacionId,
            HttpSession sesion
    ) {
        EstadoSesion estado = estadoDe(sesion);

        synchronized (estado) {
            if (estado.guardable == null
                    || !estado.guardable.id().equals(generacionId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Este resultado ya no está disponible para guardar "
                                + "en esta sesión. Comprueba el último "
                                + "itinerario generado."
                );
            }

            return estado.guardable;
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
    public void conservarTrazados(UUID generacionId, java.util.Map<String, es.upv.pin.backend.dto.GeometriaTramoDto> trazados,
            HttpSession sesion) {
        var estado = estadoDe(sesion);
        synchronized (estado) {
            var vigente = obtenerParaGuardar(generacionId, sesion);
            var actualizado = vigente.itinerario().conTrazados(trazados);
            estado.guardable = new GeneracionGuardable(vigente.id(), vigente.configuracion(), actualizado);
            estado.resultado = actualizado;
        }
    }

    private void actualizar(EstadoSesion estado, Instant ahora) {
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
                configuracion.alojamientoLocalizado(),
                configuracion.selecciones());
    }

    public record GeneracionGuardable(
            UUID id,
            ConfiguracionItinerarioDto configuracion,
            ItinerarioGeneradoDto itinerario
    ) {}

    public record Resultado(
            ItinerarioGeneradoDto itinerario,
            boolean reutilizado,
            Instant vigenteHasta,
            UUID generacionId
    ) {}

    private static class EstadoSesion {
        private boolean enCurso;
        private ConfiguracionItinerarioDto configuracion;
        private ItinerarioGeneradoDto resultado;
        private Instant vigenteHasta;
        private GeneracionGuardable guardable;
    }
}
