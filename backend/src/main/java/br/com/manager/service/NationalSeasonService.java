package br.com.manager.service;

import br.com.manager.repository.*;
import br.com.manager.utils.NationalLeagueDraw;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NationalSeasonService {

    private final NationalSeasonRepository repository;
    private final SaveRepository saves;

    public NationalSeasonService(NationalSeasonRepository repository, SaveRepository saves) {
        this.repository = repository;
        this.saves = saves;
    }

    @Transactional
    public void ensureForDate(String saveId, LocalDate date) {
        if (!saves.lock(saveId)) throw new br.com.manager.exception.DefaultErrorException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Liga não encontrada."
        );
        var initial = repository.start(saveId);
        for (var team : repository.memberTeams(saveId))
            for (int year = initial.getYear(); year <= date.getYear(); year++) ensure(saveId, team, year, initial);
    }

    @Transactional
    public void initialize(String saveId) {
        ensureForDate(saveId, repository.start(saveId));
    }

    private void ensure(String saveId, Long teamId, int year, LocalDate initial) {
        if (repository.participatesInYear(saveId, teamId, year)) return;
        var competition = repository.competition(saveId, teamId);
        if (repository.exists(saveId, competition.leagueId(), year)) return;
        var teams = repository.teams(saveId, competition.leagueId());
        if (teams.size() < 2) return;
        boolean brazil =
            competition.country().equalsIgnoreCase("Brazil") || competition.country().equalsIgnoreCase("Brasil");
        LocalDate first = brazil ? LocalDate.of(year, 2, 1) : LocalDate.of(year, 8, 1),
            last = brazil ? LocalDate.of(year, 12, 6) : LocalDate.of(year + 1, 5, 30);
        if (year == initial.getYear() && first.isBefore(initial)) first = initial.plusDays(7);
        int rounds = (teams.size() % 2 == 0 ? teams.size() - 1 : teams.size()) * 2;
        if (java.time.temporal.ChronoUnit.DAYS.between(first, last) < (rounds - 1) * 3L) first = last.minusDays(
            (rounds - 1) * 3L
        );
        long seed = new Random().nextLong();
        var fixtures = NationalLeagueDraw.draw(teams, first, last, seed);
        String label = brazil ? String.valueOf(year) : year + "/" + (year + 1);
        var season = repository.create(saveId, competition, year, label, teams.size(), rounds, first, last, seed);
        repository.fixtures(saveId, season, fixtures);
    }
}
