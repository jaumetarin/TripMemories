package es.upv.pin.backend.dto;

import jakarta.validation.constraints.*;

public record LoginDto(@NotBlank @Size(max = 255) String correo,
        @NotBlank @Size(max = 128) String contrasena) {
    @Override public String toString() { return "LoginDto[datos reservados]"; }
}
