package br.com.manager.service;

import br.com.manager.repository.*;
import br.com.manager.utils.NationalLeagueDraw;
import br.com.manager.utils.NationalSeasonCalendar;
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

    /** Materialize unattended calendars when the world is first queried or time moves. */
    @Transactional
    public void ensureWorldForDate(String saveId, LocalDate date) {
        if (!saves.lock(saveId)) throw new br.com.manager.exception.DefaultErrorException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Liga não encontrada."
        );
        var initial = repository.start(saveId);
        for (var team : repository.competitionTeams(saveId)) {
            var competition = repository.competition(saveId, team);
            boolean brazil =
                competition.country().equalsIgnoreCase("Brasil") || competition.country().equalsIgnoreCase("Brazil");
            int firstYear =
                initial.getYear() -
                (!brazil && saves.full(saveId) && !repository.hasHuman(saveId, competition.leagueId()) ? 1 : 0);
            for (int year = firstYear; year <= date.getYear(); year++) ensure(saveId, team, year, initial);
        }
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
        int groupCount =
            competition.division() == 3 && competition.country().equals("Itália") && teams.size() > 24
                ? 3
                : competition.division() == 3 && competition.country().equals("Espanha") && teams.size() > 24
                  ? 2
                  : 1;
        var groups = repository.groups(saveId, competition.leagueId(), groupCount);
        boolean brazil =
            competition.country().equalsIgnoreCase("Brazil") || competition.country().equalsIgnoreCase("Brasil");
        var window = NationalSeasonCalendar.window(competition.country(), competition.division(), year);
        LocalDate first = window.first(),
            last = window.last();
        int largestGroup = groups.values().stream().mapToInt(List::size).max().orElseThrow();
        int rounds = (largestGroup % 2 == 0 ? largestGroup - 1 : largestGroup) * 2;
        if (java.time.temporal.ChronoUnit.DAYS.between(first, last) < (rounds - 1) * 3L) first = last.minusDays(
            (rounds - 1) * 3L
        );
        long seed = new Random().nextLong();
        var fixtures = new ArrayList<NationalLeagueDraw.Fixture>();
        for (var group : groups.values())
            fixtures.addAll(NationalLeagueDraw.draw(group, first, last, seed ^ group.getFirst()));
        String label = brazil ? String.valueOf(year) : year + "/" + (year + 1);
        var season = repository.create(saveId, competition, year, label, teams.size(), rounds, first, last, seed);
        if (groupCount > 1) repository.assignGroups(saveId, season, groups);
        repository.fixtures(saveId, season, fixtures);
    }
}
