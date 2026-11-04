package br.com.manager.models.requests;

import br.com.manager.validation.PasswordsMatch;
import jakarta.validation.constraints.*;

@PasswordsMatch
public record RegistrationRequest(
    @NotBlank @Size(min = 2, max = 40) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @Size(min = 8, max = 128) String password,
    @NotNull @Size(min = 8, max = 128) String passwordConfirmation,
    @Size(max = 350000) String photoData,
    @NotBlank @Size(max = 40) String lastName
) {}
