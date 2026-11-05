package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.WorldCompetitionDtos.*;
import br.com.manager.repository.*;
import br.com.manager.utils.BackgroundMatchRules;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorldCompetitionService {

    private final WorldCompetitionRepository repository;
    private final NationalSeasonRepository nationals;
    private final NationalSeasonService seasons;
    private final SaveRepository leagues;
    private final TournamentService tournaments;
    private final CompetitionHistoryRepository history;

    public WorldCompetitionService(
        WorldCompetitionRepository repository,
        NationalSeasonRepository nationals,
        NationalSeasonService seasons,
        SaveRepository leagues,
        TournamentService tournaments,
        CompetitionHistoryRepository history
    ) {
        this.repository = repository;
        this.nationals = nationals;
        this.seasons = seasons;
        this.leagues = leagues;
        this.tournaments = tournaments;
        this.history = history;
    }

    @Transactional
    public void synchronize(String id) {
        if (!leagues.lock(id)) throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Liga não encontrada.");
        leagues.expandCatalog(id);
        history.snapshot(id);
        var date = repository.date(id);
        seasons.ensureWorldForDate(id, date);
        if (!leagues.full(id)) return;
        var results = new ArrayList<MapSqlParameterSource>();
        for (var fixture : repository.due(id, date)) {
            var score = BackgroundMatchRules.play(id, fixture.id(), fixture.homeRating(), fixture.awayRating());
            results.add(
                new MapSqlParameterSource("id", id)
                    .addValue("fixture", fixture.id())
                    .addValue("home", score.home())
                    .addValue("away", score.away())
            );
        }
        repository.finish(id, results);
        tournaments.synchronize(id, date);
    }

    @Transactional
    public Overview overview(String id) {
        synchronize(id);
        return new Overview(repository.date(id), competitions(id));
    }

    private List<Competition> competitions(String id) {
        var all = new ArrayList<>(repository.competitions(id));
        all.addAll(history.competitions(id));
        return all;
    }

    @Transactional
    public Championship championship(String id, long season) {
        synchronize(id);
        var competition = competitions(id)
            .stream()
            .filter(c -> c.id() == season)
            .findFirst()
            .orElseThrow(() ->
                new DefaultErrorException(HttpStatus.NOT_FOUND, "Campeonato não encontrado nesta liga.")
            );
        if (competition.historical()) return new Championship(competition, history.standings(id, -season), List.of());
        var table = new ArrayList<>(repository.standings(id, season));
        table.sort(
            Comparator.comparing((Standing t) -> Objects.toString(t.groupName(), ""))
                .thenComparing(Comparator.comparingInt(Standing::points).reversed())
                .thenComparing(Comparator.comparingInt((Standing t) -> t.goalsFor() - t.goalsAgainst()).reversed())
                .thenComparing(Comparator.comparingInt(Standing::goalsFor).reversed())
                .thenComparingLong(Standing::teamId)
        );
        return new Championship(competition, table, nationals.fixtures(season));
    }
}
