package es.upv.pin.backend.model;

public enum CategoriaTuristica {
    GASTRONOMIA("Gastronomía"),
    ARTE_MUSEOS("Arte y museos"),
    HISTORIA_ARQUITECTURA("Historia y arquitectura"),
    NATURALEZA_PARQUES("Naturaleza y parques"),
    CULTURA_LOCAL("Cultura local"),
    OCIO_ENTRETENIMIENTO("Ocio y entretenimiento");

    private final String nombre;

    private CategoriaTuristica(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
