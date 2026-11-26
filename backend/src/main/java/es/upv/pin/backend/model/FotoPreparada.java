package es.upv.pin.backend.model;

/** Resultado interno de validar y preparar una imagen; nunca se devuelve por la API. */
public record FotoPreparada(String nombre, String mime, byte[] original, byte[] miniatura, int ancho, int alto) {
    public long bytesTotales() { return (long) original.length + miniatura.length; }
}
