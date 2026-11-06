package es.upv.pin.backend.dto;

public record OpcionComidaDto(String id, String nombre, Double latitud, Double longitud, String direccion,
        String horario, String horarioFuente, Double distanciaMetros, Double duracionSegundos) {
    public OpcionComidaDto(String id, String nombre, Double latitud, Double longitud, String direccion,
            String horario, String horarioFuente) {
        this(id, nombre, latitud, longitud, direccion, horario, horarioFuente, null, null);
    }
    public OpcionComidaDto(String id, String nombre, Double latitud, Double longitud, String direccion) {
        this(id, nombre, latitud, longitud, direccion, null, null, null, null);
    }
}
