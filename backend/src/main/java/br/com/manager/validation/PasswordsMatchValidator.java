package br.com.manager.validation;

import br.com.manager.models.requests.RegistrationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, RegistrationRequest> {

    @Override
    public boolean isValid(RegistrationRequest request, ConstraintValidatorContext context) {
        return request == null || Objects.equals(request.password(), request.passwordConfirmation());
    }
}
