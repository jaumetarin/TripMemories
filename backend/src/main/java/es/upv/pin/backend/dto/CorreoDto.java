package es.upv.pin.backend.dto;

import jakarta.validation.constraints.*;

public record CorreoDto(@NotBlank(message = "Introduce tu correo.") @Email(message = "Introduce un correo válido.")
        @Size(max = 255, message = "El correo es demasiado largo.") String correo) {}
