package es.upv.pin.backend.model;

public enum SubcategoriaTuristica {

    COMIDA_RAPIDA("Comida rápida", CategoriaTuristica.GASTRONOMIA),
    CAFETERIAS("Cafeterías", CategoriaTuristica.GASTRONOMIA),
    MERCADOS_GASTRONOMICOS("Mercados gastronómicos", CategoriaTuristica.GASTRONOMIA),

    MUSEOS("Museos", CategoriaTuristica.ARTE_MUSEOS),
    GALERIAS_ARTE("Galerías de arte", CategoriaTuristica.ARTE_MUSEOS),
    ARTE_URBANO("Arte urbano", CategoriaTuristica.ARTE_MUSEOS),

    EDIFICIOS_RELIGIOSOS("Edificios religiosos", CategoriaTuristica.HISTORIA_ARQUITECTURA),
    CASTILLOS_PALACIOS("Castillos y palacios", CategoriaTuristica.HISTORIA_ARQUITECTURA),
    YACIMIENTOS_ARQUEOLOGICOS("Yacimientos arqueológicos", CategoriaTuristica.HISTORIA_ARQUITECTURA),

    SENDERISMO("Senderismo", CategoriaTuristica.NATURALEZA_PARQUES),
    PARQUES_JARDINES("Parques y jardines", CategoriaTuristica.NATURALEZA_PARQUES),
    PLAYAS("Playas", CategoriaTuristica.NATURALEZA_PARQUES),

    MERCADOS_TRADICIONALES("Mercados tradicionales", CategoriaTuristica.CULTURA_LOCAL),
    TALLERES_ARTESANALES("Talleres artesanales", CategoriaTuristica.CULTURA_LOCAL),
    ESPECTACULOS_TRADICIONALES("Espectáculos tradicionales", CategoriaTuristica.CULTURA_LOCAL),

    VIDA_NOCTURNA("Vida nocturna", CategoriaTuristica.OCIO_ENTRETENIMIENTO),
    COMPRAS("Compras", CategoriaTuristica.OCIO_ENTRETENIMIENTO),
    PARQUES_ATRACCIONES("Parques de atracciones", CategoriaTuristica.OCIO_ENTRETENIMIENTO);

    private final String nombre;
    private final CategoriaTuristica categoria;

    private SubcategoriaTuristica(String nombre, CategoriaTuristica categoria) {
        this.nombre = nombre;
        this.categoria = categoria;
    }

    public String getNombre() {
        return nombre;
    }

    public CategoriaTuristica getCategoria() {
        return categoria;
    }
}
