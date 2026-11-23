package es.upv.pin.backend.dto;

public record ValoracionGemini(int afinidad, Integer relevancia, int duracionMinutos, String etiqueta, String motivo) {
    // La afinidad pesa el doble. Una relevancia desconocida no añade una penalización.
    public int prioridad() {
        return relevancia == null ? afinidad : (2 * afinidad + relevancia) / 3;
    }
}
