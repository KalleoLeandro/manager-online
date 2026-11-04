package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.RegistrationRequest;
import br.com.manager.models.responses.RegistrationResponse;
import br.com.manager.repository.ManagerRepository;
import br.com.manager.utils.PasswordHashing;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final ManagerRepository repository;
    private final ManagerPhotoService photos;

    public RegistrationService(ManagerRepository repository, ManagerPhotoService photos) {
        this.repository = repository;
        this.photos = photos;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        var photo = photos.validate(request.photoData());
        var firstName = request.name() == null ? "" : request.name().strip().replaceAll("\\s+", " ");
        var lastName = request.lastName() == null ? "" : request.lastName().strip().replaceAll("\\s+", " ");
        var name = firstName + " " + lastName;
        if (firstName.length() < 2 || lastName.isBlank() || name.length() > 40) throw new DefaultErrorException(
            HttpStatus.BAD_REQUEST,
            "Informe nome e sobrenome. O nome deve ter pelo menos 2 caracteres e o nome completo, até 40."
        );
        if (!request.password().equals(request.passwordConfirmation())) {
            throw new DefaultErrorException(HttpStatus.BAD_REQUEST, "As senhas precisam ser iguais.");
        }
        var id = UUID.randomUUID().toString();
        try {
            repository.create(
                id,
                name,
                request.email().strip().toLowerCase(Locale.ROOT),
                PasswordHashing.hash(request.password()),
                firstName,
                lastName
            );
        } catch (DuplicateKeyException exception) {
            throw new DefaultErrorException(HttpStatus.CONFLICT, "Já existe uma conta com esse e-mail.");
        }
        photos.save(id, photo);
        return new RegistrationResponse(id, name, firstName, lastName);
    }
}
