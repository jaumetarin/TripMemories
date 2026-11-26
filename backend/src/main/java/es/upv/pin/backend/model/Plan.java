package es.upv.pin.backend.model;

/** Politica unica de limites para cada plan. */
public enum Plan {
    FREE(5, 3, 500L * 1024 * 1024), PREMIUM(20, 5, 2L * 1024 * 1024 * 1024);
    private final int mes, hora;
    private final long bytes;
    Plan(int mes, int hora, long bytes) { this.mes=mes; this.hora=hora; this.bytes=bytes; }
    public int generacionesMes() { return mes; }
    public int generacionesHora() { return hora; }
    public long cuotaBytes() { return bytes; }
}
