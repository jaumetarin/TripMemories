package es.upv.pin.backend.dto;

import jakarta.validation.constraints.*;

public record RegistroDto(
        @NotBlank(message = "Introduce tu nombre.") @Size(max = 120, message = "El nombre admite hasta 120 caracteres.") String nombre,
        @NotBlank(message = "Introduce tu correo.") @Email(message = "Introduce un correo válido.") @Size(max = 255, message = "El correo es demasiado largo.") String correo,
        @NotBlank(message = "Introduce una contraseña.")
        @Size(min = 8, max = 128, message = "Usa una contraseña de 8 a 128 caracteres.")
        @Pattern(regexp = "(?s)(?=.*\\p{L})(?=.*\\p{Nd})(?=.*[\\p{P}\\p{S}]).*",
                message = "Incluye al menos una letra, un número y un símbolo, por ejemplo !, @ o #.") String contrasena,
        @NotNull(message = "Repite la contraseña.") @Size(max = 128, message = "La confirmación es demasiado larga.") String confirmacionContrasena) {
    @Override public String toString() { return "RegistroDto[datos reservados]"; }
}
