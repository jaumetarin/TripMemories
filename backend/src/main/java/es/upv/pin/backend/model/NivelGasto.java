package es.upv.pin.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;

// Son preferencias cualitativas: no representan importes ni un presupuesto.
public enum NivelGasto {
    ECONOMICO("Económico", "Priorizar opciones gratuitas o de bajo coste."),
    MODERADO("Moderado", "Combinar opciones gratuitas y de pago."),
    FLEXIBLE("Flexible", "Dar más prioridad al interés de la experiencia."),
    SIN_PREFERENCIA("Sin preferencia", "No priorizar por gasto.");

    private final String nombre;
    private final String descripcion;

    NivelGasto(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    // La API acepta códigos explícitos, nunca la posición numérica del enum.
    @JsonCreator
    public static NivelGasto desdeCodigo(String codigo) {
        return codigo == null ? null : NivelGasto.valueOf(codigo);
    }
}
