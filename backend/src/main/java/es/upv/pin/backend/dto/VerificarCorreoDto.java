package es.upv.pin.backend.dto;

import jakarta.validation.constraints.*;

public record VerificarCorreoDto(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(max = 128) String contrasena) {
    @Override public String toString() { return "VerificarCorreoDto[datos reservados]"; }
}
