package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.LugarDto;
import es.upv.pin.backend.model.CategoriaTuristica;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Comparación conservadora: compartir coordenadas o unas palabras no basta. */
public final class IdentidadLugares {
    private IdentidadLugares() {}
    private static final Set<String> OMITIR = Set.of("de", "del", "d", "la", "el", "los", "las", "les", "els", "l",
            "of", "the", "y", "i", "parque", "parc", "park", "jardin", "jardines", "jardins", "garden", "gardens");
    private static final Set<String> GENERICOS = Set.of("museo", "iglesia", "restaurante", "local", "hotel", "zoo",
            "centro comercial", "centro", "plaza", "acuario", "bolera", "recreativos");

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    public static String nombreComparable(String nombre) {
        return Arrays.stream(normalizar(nombre).split("\\s+"))
                .map(palabra -> switch (palabra) { case "laberint" -> "laberinto"; case "museu" -> "museo"; default -> palabra; })
                .filter(palabra -> !OMITIR.contains(palabra)).collect(Collectors.joining(" "));
    }

    public static boolean mismoLugar(LugarDto a, LugarDto b) {
        if (a == null || b == null) return false;
        if (a.id() != null && a.id().equals(b.id())) return true;
        String nombre = nombreComparable(a.nombre());
        if (nombre.length() < 4 || GENERICOS.contains(nombre) || !nombre.equals(nombreComparable(b.nombre()))) return false;
        if (a.categorias() == null || b.categorias() == null) return false;
        boolean recintoParque = nombre.contains(" ")
                && (a.categorias().contains(CategoriaTuristica.NATURALEZA_PARQUES)
                    || b.categorias().contains(CategoriaTuristica.NATURALEZA_PARQUES))
                && (normalizar(a.nombre()).matches(".*\\b(parque|parc|park|jardin|jardines|jardins)\\b.*")
                    || normalizar(b.nombre()).matches(".*\\b(parque|parc|park|jardin|jardines|jardins)\\b.*"));
        if (a.categorias().stream().noneMatch(b.categorias()::contains) && !recintoParque) return false;
        if (!coordenadasValidas(a) || !coordenadasValidas(b)) return false;
        boolean gastronomia = a.categorias().contains(CategoriaTuristica.GASTRONOMIA)
                || b.categorias().contains(CategoriaTuristica.GASTRONOMIA);
        if (gastronomia) {
            // Dos sucursales cercanas de una cadena siguen siendo establecimientos distintos.
            if (a.direccion() == null || b.direccion() == null
                    || !normalizar(a.direccion()).equals(normalizar(b.direccion()))) return false;
            return distanciaMetros(a.latitud(), a.longitud(), b.latitud(), b.longitud()) <= 30;
        }
        boolean parque = recintoParque || (a.categorias().contains(CategoriaTuristica.NATURALEZA_PARQUES)
                && b.categorias().contains(CategoriaTuristica.NATURALEZA_PARQUES));
        return distanciaMetros(a.latitud(), a.longitud(), b.latitud(), b.longitud()) <= (parque ? 500 : 200);
    }

    public static boolean coordenadasValidas(LugarDto lugar) {
        return lugar.latitud() != null && lugar.longitud() != null
                && Double.isFinite(lugar.latitud()) && Double.isFinite(lugar.longitud())
                && Math.abs(lugar.latitud()) <= 90 && Math.abs(lugar.longitud()) <= 180;
    }

    public static double distanciaMetros(double latA, double lonA, double latB, double lonB) {
        double latitud = Math.toRadians(latB - latA), longitud = Math.toRadians(lonB - lonA);
        double valor = Math.pow(Math.sin(latitud / 2), 2)
                + Math.cos(Math.toRadians(latA)) * Math.cos(Math.toRadians(latB)) * Math.pow(Math.sin(longitud / 2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, valor)));
    }
}
