package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.ManagerDto;
import br.com.manager.models.responses.SessionResponse;
import br.com.manager.repository.ManagerRepository;
import br.com.manager.repository.SaveRepository;
import br.com.manager.models.events.SessionRevokedEvent;
import org.springframework.context.ApplicationEventPublisher;
import br.com.manager.utils.PasswordHashing;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final ManagerRepository repository;
    private final ManagerPhotoService photos;
    private final SaveRepository leagues;
    private final ApplicationEventPublisher events;

    public AuthService(ManagerRepository repository, ManagerPhotoService photos, SaveRepository leagues, ApplicationEventPublisher events) {
        this.repository = repository;
        this.photos = photos;
        this.leagues=leagues;
        this.events=events;
    }

    @Transactional
    public void logout(String header) {
        if(header==null||!header.startsWith("Bearer "))return;
        String token=header.substring(7);
        var manager=find(token);
        var revoked=new HashSet<String>();
        if(manager!=null)revoked.addAll(leagues.revokeAccountLeagueTokens(manager.id()));
        String tokenHash=hash(token);
        repository.revokeSession(tokenHash);
        revoked.add(tokenHash);
        events.publishEvent(new SessionRevokedEvent(Set.copyOf(revoked)));
    }

    @Transactional
    public SessionResponse login(String email, String password) {
        var credentials = repository.credentials(email.strip().toLowerCase(Locale.ROOT));
        if (credentials.isEmpty() || !PasswordHashing.matches(password, credentials.get().passwordHash())) {
            throw new DefaultErrorException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        var manager = credentials.orElseThrow();
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = Instant.now();
        repository.removeExpiredSessions(now);
        repository.createSession(hash(token), manager.id(), now.plusSeconds(14 * 86400));
        return new SessionResponse(
            manager.id(),
            manager.name(),
            token,
            photos.url(manager.id()),
            manager.firstName(),
            manager.lastName()
        );
    }

    public ManagerDto require(String header) {
        var manager = find(header != null && header.startsWith("Bearer ") ? header.substring(7) : "");
        if (manager == null) throw new DefaultErrorException(
            HttpStatus.UNAUTHORIZED,
            "Entre na sua conta para continuar."
        );
        return manager;
    }

    public ManagerDto find(String token) {
        if (token == null || token.isBlank()) return null;
        return repository.findSession(hash(token), Instant.now()).orElse(null);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
