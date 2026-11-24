package es.upv.pin.backend.dto;

import es.upv.pin.backend.model.CategoriaTuristica;

public record RepartoCategoriaLugaresDto(CategoriaTuristica codigo, String nombre, int mostrados, int objetivo) {}
