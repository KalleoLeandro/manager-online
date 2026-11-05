package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.LeagueMemberDto;
import br.com.manager.models.dto.LeaguePlayerSearchDto;
import br.com.manager.models.dto.ManagedPlayerDto;
import br.com.manager.models.dto.TeamSettingsDto;
import br.com.manager.models.events.TeamUpdatedEvent;
import br.com.manager.models.requests.UpdateTeamRequest;
import br.com.manager.models.responses.TeamManagementResponse;
import br.com.manager.repository.ClubPaletteRepository;
import br.com.manager.repository.ManagementRepository;
import br.com.manager.repository.SaveRepository;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagementService {

    public static final Map<String, List<String>> FORMATIONS = br.com.manager.utils.FormationRules.ROLES.entrySet()
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(Map.Entry::getKey, e ->
                e.getValue().stream().map(br.com.manager.utils.FormationRules::family).toList()
            )
        );
    private final ManagementRepository repository;
    private final SaveRepository leagues;
    private final SaveService saves;
    private final ClubPaletteRepository palettes;
    private final ApplicationEventPublisher events;

    public ManagementService(
        ManagementRepository repository,
        SaveRepository leagues,
        SaveService saves,
        ClubPaletteRepository palettes,
        ApplicationEventPublisher events
    ) {
        this.repository = repository;
        this.leagues = leagues;
        this.saves = saves;
        this.palettes = palettes;
        this.events = events;
    }

    private LeagueMemberDto member(String id, String header) {
        saves.authorizedRoom(id, header);
        return saves.realtimeMember(id, header.substring(7));
    }

    @Transactional
    public TeamManagementResponse state(String id, String header) {
        return state(id, member(id, header));
    }

    @Transactional
    public List<LeaguePlayerSearchDto> search(String id, String header, String query) {
        member(id, header);
        if (query == null || query.strip().length() < 2 || query.strip().length() > 80) bad(
            "Informe entre 2 e 80 caracteres para buscar jogadores."
        );
        return repository.search(id, query.strip());
    }

    private TeamManagementResponse state(String id, LeagueMemberDto member) {
        var team = repository.team(id, member.teamId());
        var players = repository.players(id, member.teamId());
        var settings = repository.settings(id, member.teamId()).orElse(new TeamSettingsDto("4-3-3", "BALANCED", 0));
        var lineup = repository.lineup(id, member.teamId());
        if (lineup.isEmpty()) lineup = suggest(players, settings.formation());
        return new TeamManagementResponse(
            saves.room(id),
            member.id(),
            member.teamId(),
            member.teamName(),
            member.crestUrl(),
            repository.leagueName(team.leagueId()).orElse("Campeonato nacional"),
            team.rating(),
            team.budget(),
            team.points(),
            palettes.forTeam(member.teamId()),
            players,
            settings.formation(),
            settings.mentality(),
            lineup,
            settings.version(),
            repository.introductionSeen(id, member.id()),
            settings.tacticalPlan()
        );
    }

    private List<Long> suggest(List<ManagedPlayerDto> players, String formation) {
        var chosen = new ArrayList<Long>();
        for (var position : br.com.manager.utils.FormationRules.ROLES.get(formation)) {
            var player = players
                .stream()
                .filter(p -> p.condition().available() && !chosen.contains(p.id()))
                .sorted(
                    Comparator.comparingDouble(
                        (ManagedPlayerDto p) ->
                            p.condition().effectiveAbility() *
                            br.com.manager.utils.PlayerPositionRules.fit(p.position(), p.positions(), position)
                    ).reversed()
                )
                .findFirst();
            chosen.add(player.map(ManagedPlayerDto::id).orElse(0L));
        }
        return chosen;
    }

    @Transactional
    public void acknowledgeIntroduction(String id, String header) {
        var current = member(id, header);
        repository.acknowledgeIntroduction(id, current.id());
    }

    @Transactional
    public TeamManagementResponse update(String id, String header, UpdateTeamRequest input) {
        var member = member(id, header);
        leagues.lock(id);
        member = member(id, header);
        if (saves.room(id).status().equals("LIVE")) conflict(
            "Use a tela da partida para alterar o time durante o jogo."
        );
        if (member.ready()) conflict("Volte à preparação antes de alterar sua escalação.");
        var current = state(id, member);
        if (current.version() != input.version()) conflict(
            "Sua equipe foi alterada em outra aba. Recarregue antes de salvar."
        );
        var roles = FORMATIONS.get(input.formation());
        if (roles == null || !Set.of("BALANCED", "ATTACKING", "DEFENSIVE").contains(input.mentality())) bad(
            "Formação ou postura inválida."
        );
        if (input.lineup() == null || input.lineup().size() != 11 || new HashSet<>(input.lineup()).size() != 11) bad(
            "Escolha 11 jogadores diferentes para o time titular."
        );
        for (int i = 0; i < 11; i++) {
            final var playerId = input.lineup().get(i);
            final var role = roles.get(i);
            if (
                current
                    .players()
                    .stream()
                    .noneMatch(p -> p.id().equals(playerId) && p.condition().available())
            ) bad("Escolha um jogador disponível do seu elenco na posição indicada para cada vaga.");
        }
        var starters = new LinkedHashMap<Long, String>();
        for (int i = 0; i < input.lineup().size(); i++) starters.put(input.lineup().get(i), roles.get(i));
        var plan = br.com.manager.utils.TacticalPlanRules.validate(
            input.tacticalPlan() == null ? current.tacticalPlan() : input.tacticalPlan(),
            starters
        );
        if (current.version() == 0) repository.createSettings(
            id,
            member.teamId(),
            input.formation(),
            input.mentality()
        );
        else repository.updateSettings(id, member.teamId(), input.formation(), input.mentality());
        repository.replaceLineup(id, member.teamId(), input.lineup());
        repository.savePlan(id, member.teamId(), plan);
        events.publishEvent(new TeamUpdatedEvent(id, member.teamId(), current.version() + 1));
        return state(id, member);
    }

    private void bad(String message) {
        throw new DefaultErrorException(HttpStatus.BAD_REQUEST, message);
    }

    private void conflict(String message) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, message);
    }
}
