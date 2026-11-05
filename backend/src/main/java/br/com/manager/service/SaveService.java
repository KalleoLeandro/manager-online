package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.LeagueMemberDto;
import br.com.manager.models.events.SaveChangedEvent;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.JoinLeagueRequest;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.models.responses.LeagueRoomResponse;
import br.com.manager.repository.RosterPolicyRepository;
import br.com.manager.repository.SaveRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaveService {

    private final SaveRepository repository;
    private final RosterPolicyRepository policies;
    private final CatalogService catalog;
    private final ApplicationEventPublisher events;
    private final AuthService auth;
    private final LeagueLifecycleService lifecycle;
    private final PlayerProfilesService profiles;
    private final NationalSeasonService seasons;
    private final FinanceService finances;
    private final SecureRandom random = new SecureRandom();

    public SaveService(
        SaveRepository repository,
        RosterPolicyRepository policies,
        CatalogService catalog,
        ApplicationEventPublisher events,
        AuthService auth,
        LeagueLifecycleService lifecycle,
        PlayerProfilesService profiles,
        NationalSeasonService seasons,
        FinanceService finances
    ) {
        this.repository = repository;
        this.policies = policies;
        this.catalog = catalog;
        this.events = events;
        this.auth = auth;
        this.lifecycle = lifecycle;
        this.profiles = profiles;
        this.seasons = seasons;
        this.finances = finances;
    }

    @Transactional
    public LeagueAccessResponse createForAccount(CreateLeagueRequest input, String header) {
        var manager = auth.require(header);
        var access = create(
            new CreateLeagueRequest(
                input.name(),
                input.capacity(),
                manager.name(),
                input.teamId(),
                input.localMultiplayer()
            )
        );
        repository.attachAccount(access.memberId(), manager.id());
        return access;
    }

    @Transactional
    public LeagueAccessResponse joinForAccount(String code, JoinLeagueRequest input, String header) {
        var manager = auth.require(header);
        var room = byCode(code);
        lock(room.id());
        var existing = repository.memberIdsForAccount(room.id(), manager.id());
        if (!existing.isEmpty()) {
            lifecycle.touch(room.id());
            return new LeagueAccessResponse(room(room.id()), existing.getFirst(), header.substring(7));
        }
        var access = join(code, new JoinLeagueRequest(manager.name(), input.teamId()));
        repository.attachAccount(access.memberId(), manager.id());
        return access;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<LeagueAccessResponse> mine(String header) {
        var manager = auth.require(header);
        return repository
            .memberships(manager.id())
            .stream()
            .map(membership ->
                new LeagueAccessResponse(room(membership.saveId()), membership.memberId(), header.substring(7))
            )
            .toList();
    }

    @Transactional
    public LeagueAccessResponse create(CreateLeagueRequest input) {
        if (input.localMultiplayer() && input.capacity() > 8) fail(
            HttpStatus.BAD_REQUEST,
            "O multiplayer local permite no máximo 8 treinadores."
        );
        validateName(input.name(), 2);
        validateName(input.managerName(), 2);
        if (
            catalog
                .teams(null)
                .stream()
                .noneMatch(t -> t.id().equals(input.teamId()))
        ) fail(HttpStatus.BAD_REQUEST, "Escolha um clube das seis ligas disponíveis.");
        var id = UUID.randomUUID().toString();
        String code;
        do {
            code = code();
        } while (repository.codeExists(code));
        repository.create(id, input.name().strip(), code, input.capacity(), catalog.version(), lifecycle.now());
        repository.localMultiplayer(id, input.capacity() > 1 && input.localMultiplayer());
        repository.snapshotCatalog(id);
        repository.initialCalendar(id, input.teamId());
        policies.snapshot(id);
        return addMember(id, input.managerName(), input.teamId(), true);
    }

    @Transactional
    public LeagueAccessResponse join(String code, JoinLeagueRequest input) {
        validateName(input.managerName(), 2);
        var room = byCode(code);
        lock(room.id());
        room = room(room.id());
        if (room.localMultiplayer()) fail(
            HttpStatus.FORBIDDEN,
            "Os participantes locais são cadastrados pelo criador no mesmo aparelho."
        );
        return joinRoom(room, input);
    }

    @Transactional
    public LeagueAccessResponse addLocal(String id, String header, JoinLeagueRequest input) {
        lock(id);
        requireLocalOwner(id, header);
        validateName(input.managerName(), 2);
        return joinRoom(room(id), input);
    }

    @Transactional
    public LeagueAccessResponse switchLocal(String id, String header, String memberId) {
        lock(id);
        requireLocalOwner(id, header);
        var room = room(id);
        var target = room
            .members()
            .stream()
            .filter(m -> m.id().equals(memberId))
            .findFirst()
            .orElseThrow(() -> new DefaultErrorException(HttpStatus.NOT_FOUND, "Treinador não encontrado nesta liga."));
        if (target.owner()) return new LeagueAccessResponse(room, memberId, header.substring(7));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.memberToken(id, memberId, hash(token));
        lifecycle.touch(id);
        return new LeagueAccessResponse(room, memberId, token);
    }

    private void requireLocalOwner(String id, String header) {
        var member = authenticate(id, header);
        if (!room(id).localMultiplayer() || !repository.owner(member)) fail(
            HttpStatus.FORBIDDEN,
            "Somente o criador pode gerenciar os treinadores locais."
        );
    }

    private LeagueAccessResponse joinRoom(LeagueRoomResponse room, JoinLeagueRequest input) {
        if (!room.status().equals("LOBBY")) fail(HttpStatus.CONFLICT, "Esta liga não está aceitando participantes.");
        if (room.members().size() >= room.capacity()) fail(
            HttpStatus.CONFLICT,
            "Todas as vagas desta liga estão ocupadas."
        );
        if (!repository.selectableTeam(room.id(), input.teamId())) fail(
            HttpStatus.BAD_REQUEST,
            "Clube indisponível nesta liga."
        );
        if (
            room
                .members()
                .stream()
                .anyMatch(m -> m.teamId().equals(input.teamId()))
        ) fail(HttpStatus.CONFLICT, "Este clube já foi escolhido. Selecione outro.");
        return addMember(room.id(), input.managerName(), input.teamId(), false);
    }

    private LeagueAccessResponse addMember(String saveId, String name, Long teamId, boolean owner) {
        var id = UUID.randomUUID().toString();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.addMember(id, saveId, name.strip(), teamId, owner, hash(token));
        repository.includeBrazilianCalendar(saveId, teamId);
        seasons.initialize(saveId);
        lifecycle.touch(saveId);
        events.publishEvent(new SaveChangedEvent(saveId));
        return new LeagueAccessResponse(room(saveId), id, token);
    }

    public LeagueRoomResponse byCode(String code) {
        var ids = repository.idByCode(code.strip().toUpperCase(Locale.ROOT)).stream().toList();
        if (ids.isEmpty()) fail(HttpStatus.NOT_FOUND, "Código de liga não encontrado.");
        return room(ids.getFirst());
    }

    @Transactional
    public LeagueRoomResponse authorizedRoom(String id, String token) {
        lock(id);
        authenticate(id, token);
        lifecycle.touch(id);
        return room(id);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LeagueRoomResponse summary(String id, String token) {
        authenticate(id, token);
        return room(id);
    }

    @Transactional
    public void delete(String id, String token) {
        lock(id);
        var member = authenticate(id, token);
        if (!repository.owner(member)) fail(HttpStatus.FORBIDDEN, "Somente o criador pode excluir esta liga.");
        lifecycle.removeLocked(id, "DELETED_BY_OWNER");
    }

    @Transactional
    public LeagueRoomResponse ready(String id, String token, boolean ready) {
        lock(id);
        var member = authenticate(id, token);
        if (ready) {
            finances.requireApprovedForMember(id, member);
            profiles.requireAvailableStarters(id, member);
        }
        if (room(id).status().equals("LIVE")) fail(
            HttpStatus.CONFLICT,
            "A jornada está em andamento. Aguarde o término das partidas."
        );
        repository.setReady(member, ready);
        var room = room(id);
        boolean allReady =
            room.members().size() == room.capacity() && room.members().stream().allMatch(LeagueMemberDto::ready);
        repository.setStatus(id, allReady ? "READY" : "LOBBY");
        lifecycle.touch(id);
        events.publishEvent(new SaveChangedEvent(id));
        return room(id);
    }

    private String authenticate(String id, String token) {
        if (token == null || !token.startsWith("Bearer ")) fail(
            HttpStatus.UNAUTHORIZED,
            "Entre nesta liga para continuar."
        );
        var ids = repository.memberIdsByToken(id, hash(token.substring(7)));
        if (ids.isEmpty()) {
            var manager = auth.find(token.substring(7));
            if (manager != null) ids = repository.memberIdsForAccount(id, manager.id());
        }
        if (ids.isEmpty()) fail(HttpStatus.UNAUTHORIZED, "Acesso à liga inválido.");
        return ids.getFirst();
    }

    private void lock(String id) {
        if (!repository.lock(id)) fail(HttpStatus.NOT_FOUND, "Liga não encontrada.");
    }

    public LeagueRoomResponse room(String id) {
        var league = repository
            .find(id)
            .orElseThrow(() -> new DefaultErrorException(HttpStatus.NOT_FOUND, "Liga não encontrada."));
        return new LeagueRoomResponse(
            league.id(),
            league.name(),
            league.inviteCode(),
            league.capacity(),
            league.status(),
            league.catalogVersion(),
            league.roundNumber(),
            repository.members(id),
            league.lastActivityAt(),
            lifecycle.expiresAt(league.lastActivityAt()),
            league.localMultiplayer()
        );
    }

    public LeagueMemberDto realtimeMember(String id, String token) {
        var memberId = authenticate(id, "Bearer " + token);
        return room(id)
            .members()
            .stream()
            .filter(m -> m.id().equals(memberId))
            .findFirst()
            .orElseThrow();
    }

    public Map<Long, String> selectionTeams(String id) {
        return repository.selectionTeams(id);
    }

    private String code() {
        var alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        var result = new StringBuilder();
        for (int i = 0; i < 8; i++) result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return result.toString();
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void validateName(String value, int min) {
        if (value.strip().length() < min) fail(
            HttpStatus.BAD_REQUEST,
            "Informe um nome com pelo menos " + min + " caracteres."
        );
    }

    private void fail(HttpStatus status, String message) {
        throw new DefaultErrorException(status, message);
    }
}
