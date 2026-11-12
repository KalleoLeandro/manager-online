package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.repository.TournamentRepository;
import br.com.manager.utils.FifaGroupStandings;
import br.com.manager.utils.TournamentStandings;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TournamentQueryService {

    private final SaveService saves;
    private final TournamentRepository repository;
    private final WorldCompetitionService world;

    public TournamentQueryService(SaveService saves, TournamentRepository repository, WorldCompetitionService world) {
        this.saves = saves;
        this.repository = repository;
        this.world = world;
    }

    public List<Edition> list(String id, String header) {
        saves.summary(id, header);
        var editions = repository.editions(id);
        // Time advancement synchronizes existing editions; a read must not rebuild calendars.
        if (!editions.isEmpty()) return editions;
        world.synchronize(id);
        return repository.editions(id);
    }

    public List<Title> titles(String id, long team, String header) {
        saves.summary(id, header);
        if (team < 1) throw new DefaultErrorException(HttpStatus.BAD_REQUEST, "Clube inválido.");
        return repository.awards(id, team);
    }

    public Summary detail(String id, long edition, String header) {
        saves.summary(id, header);
        var e = repository
            .editions(id)
            .stream()
            .filter(t -> t.id() == edition)
            .findFirst()
            .orElseThrow(() ->
                new DefaultErrorException(HttpStatus.NOT_FOUND, "Competição não encontrada nesta liga.")
            );
        var ties = new ArrayList<Tie>();
        for (int s = 0; s < e.rules().stages().size(); s++) ties.addAll(repository.ties(e.id(), s));
        var groups = new HashMap<Long, String>();
        var games = repository.games(id, e.id());
        var standings = new ArrayList<Standing>();
        for (int stage = 0; stage < e.rules().stages().size(); stage++) {
            var grouped = repository.stageGroups(e.id(), stage);
            grouped.forEach((club, group) -> {
                if (group != null) groups.put(club, group);
            });
            var rule = e.rules().stages().get(stage);
            if (rule.format().equals("KNOCKOUT")) continue;
            var entrants = repository.stageTeams(id, e, stage);
            int stageIndex = stage;
            var fixtures = games
                .stream()
                .filter(g -> g.stage() == stageIndex)
                .toList();
            var names = rule.format().equals("UEFA_LEAGUE")
                ? Set.of("Fase de liga")
                : new TreeSet<>(grouped.values().stream().filter(Objects::nonNull).toList());
            for (String name : names) {
                var members = rule.format().equals("UEFA_LEAGUE")
                    ? entrants
                    : entrants
                          .stream()
                          .filter(t -> name.equals(grouped.get(t.id())))
                          .toList();
                var ranked = e.key().equals("CLUB_WORLD_CUP")
                    ? FifaGroupStandings.rank(members, fixtures, repository.fairPlay(e.id()), e.id() ^ name.hashCode())
                    : TournamentStandings.rank(members, fixtures, rule.format().equals("UEFA_LEAGUE"));
                for (int position = 0; position < ranked.size(); position++) {
                    var row = ranked.get(position);
                    standings.add(
                        new Standing(
                            stage,
                            name,
                            position + 1,
                            row.team().id(),
                            row.team().name(),
                            row.played(),
                            row.wins(),
                            row.draws(),
                            row.losses(),
                            row.gf(),
                            row.ga(),
                            row.points()
                        )
                    );
                }
            }
        }
        return new Summary(e, repository.teams(id, e.id()), ties, games, groups, standings);
    }
}
