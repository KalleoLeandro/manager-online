package br.com.manager.service;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.repository.TournamentRepository;
import br.com.manager.utils.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Group phases share fixture storage with the domestic calendar, without knockout tie semantics. */
@Service
public class TournamentGroupService {

    private final TournamentRepository repository;

    public TournamentGroupService(TournamentRepository repository) {
        this.repository = repository;
    }

    public boolean isGroup(Edition edition, int index) {
        return !edition.rules().stages().get(index).format().equals("KNOCKOUT");
    }

    public void create(String id, Edition edition, int index, List<Entrant> entrants, long seed) {
        var stage = edition.rules().stages().get(index);
        var fixtures = new ArrayList<NationalLeagueDraw.Fixture>();
        var groups = new HashMap<Long, String>();
        if (stage.format().equals("UEFA_LEAGUE")) {
            var sorted = entrants.stream().sorted(Comparator.comparingInt(Entrant::rank)).toList();
            var clubs = new ArrayList<ChampionsLeagueDraw.Club>();
            for (int i = 0; i < sorted.size(); i++) {
                var team = sorted.get(i);
                clubs.add(new ChampionsLeagueDraw.Club(team.id(), team.association(), i / 9 + 1));
            }
            ChampionsLeagueDraw.draw(clubs, stage.matchDates(), seed).forEach(f ->
                fixtures.add(new NationalLeagueDraw.Fixture(f.round(), f.date(), f.home(), f.away()))
            );
        } else {
            var drawn = LibertadoresGroupDraw.draw(entrants, seed);
            for (var group : drawn.entrySet()) {
                group.getValue().forEach(t -> groups.put(t.id(), group.getKey()));
                var schedule = NationalLeagueDraw.draw(
                    group.getValue().stream().map(Entrant::id).toList(),
                    stage.matchDates().getFirst(),
                    stage.matchDates().getLast(),
                    seed ^ group.getKey().hashCode()
                );
                schedule.forEach(f ->
                    fixtures.add(
                        new NationalLeagueDraw.Fixture(
                            f.round(),
                            stage.matchDates().get(f.round() - 1),
                            f.homeTeamId(),
                            f.awayTeamId()
                        )
                    )
                );
            }
        }
        repository.leagueStage(id, edition, index, entrants, fixtures, groups);
    }

    public boolean advance(String id, Edition edition, int index, List<Game> games) {
        var played = games
            .stream()
            .filter(g -> g.stage() == index)
            .toList();
        if (played.isEmpty() || played.stream().anyMatch(g -> !g.status().equals("PLAYED"))) return false;
        var entrants = repository.stageTeams(id, edition, index);
        if (edition.rules().stages().get(index).format().equals("UEFA_LEAGUE")) {
            var ranking = TournamentStandings.rank(entrants, played, true);
            for (int i = 0; i < 24; i++) repository.advanceTeam(
                edition.id(),
                index + (i < 8 ? 2 : 1),
                ranking.get(i).team().id(),
                i + 1,
                null
            );
        } else {
            var groups = repository.stageGroups(edition.id(), index);
            for (String group : new TreeSet<>(groups.values())) {
                var members = entrants
                    .stream()
                    .filter(t -> group.equals(groups.get(t.id())))
                    .toList();
                var ranking = TournamentStandings.rank(members, played, false);
                for (int i = 0; i < 2; i++) repository.advanceTeam(
                    edition.id(),
                    index + 1,
                    ranking.get(i).team().id(),
                    i + 1,
                    group
                );
            }
        }
        repository.nextStage(edition.id(), index + 1);
        return true;
    }
}
