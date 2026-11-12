package br.com.manager.service;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.PlayerAwardDtos.*;
import br.com.manager.models.events.SaveChangedEvent;
import br.com.manager.repository.PlayerAwardRepository;
import br.com.manager.utils.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PlayerAwardService {

    private final PlayerAwardRepository repository;
    private final ApplicationEventPublisher events;

    public PlayerAwardService(PlayerAwardRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public void recordActual(String id, MatchStateDto match) {
        if (match.friendlyMatch || repository.covered(match.fixtureId)) return;
        var rows = new ArrayList<Performance>();
        var date = LocalDate.parse(match.date);
        for (var team : List.of(match.home, match.away)) {
            var other = team == match.home ? match.away : match.home;
            for (var p : team.players) {
                if (!p.entered || p.minutes < 1) continue;
                rows.add(
                    new Performance(
                        match.fixtureId,
                        p.id,
                        team.id,
                        p.name,
                        team.name,
                        p.naturalPosition == null ? p.position : p.naturalPosition,
                        p.ability,
                        date,
                        p.minutes,
                        p.goals,
                        p.assists,
                        p.passesCompleted,
                        p.tackles,
                        p.saves,
                        other.goals == 0 && p.minutes >= 60 ? 1 : 0,
                        p.minutes >= 5 ? p.rating : null,
                        false
                    )
                );
            }
        }
        repository.insert(id, rows);
        repository.cover(id, match.fixtureId, false);
    }

    private void background(String id, List<Fixture> fixtures) {
        if (fixtures.isEmpty()) return;
        var teams = new HashSet<Long>();
        fixtures.forEach(f -> {
            teams.add(f.home());
            teams.add(f.away());
        });
        var rosters = repository.rosters(id, teams);
        var rows = new ArrayList<Performance>();
        for (var fixture : fixtures) {
            rows.addAll(
                BackgroundPlayerPerformance.play(
                    id,
                    fixture,
                    rosters.getOrDefault(fixture.home(), List.of()),
                    rosters.getOrDefault(fixture.away(), List.of())
                )
            );
            repository.cover(id, fixture.id(), true);
        }
        repository.insert(id, rows);
    }

    public void synchronize(String id, LocalDate date) {
        for (var match : repository.missingSessions(id, date)) recordActual(id, match);
        for (int batch = 0; batch < 4; batch++) {
            var missing = repository.missingBackground(id, date);
            background(id, missing);
            if (missing.size() < 192) break;
        }
        boolean created = false;
        for (var competition : repository.finished(id, date)) {
            background(id, repository.competitionBackfill(id, competition));
            if (!repository.competitionCovered(id, competition)) continue;
            var players = repository.aggregate(id, LocalDate.of(1900, 1, 1), competition.finishedOn(), competition);
            var finalists = PlayerAwardRules.scorers(players);
            repository.award(
                id,
                "TOP_SCORER",
                competition.scope(),
                competition.year(),
                "Artilharia do campeonato",
                competition.name(),
                competition.finishedOn(),
                41,
                "Maior número de gols na competição inteira, somando todas as fases. Desempates: assistências, média das notas, menos minutos e identificação do atleta. Amistosos e cobranças de desempate não contam.",
                finalists,
                competition.relevant() && !competition.finishedOn().isBefore(date.minusDays(7))
            );
            created = true;
        }
        int first = repository.start(id).getYear();
        int last =
            date.getMonth() == Month.DECEMBER && date.getDayOfMonth() == 31 ? date.getYear() : date.getYear() - 1;
        for (int year = first; year <= last; year++) {
            if (
                repository.exists(id, "GOLDEN_BALL", "GLOBAL", year) &&
                repository.exists(id, "GOLDEN_BOOT", "GLOBAL", year)
            ) continue;
            if (repository.missingYear(id, year)) continue;
            var players = repository.aggregate(id, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), null);
            boolean automatic = year >= date.getYear() - 1;
            if (!repository.exists(id, "GOLDEN_BALL", "GLOBAL", year)) {
                repository.award(
                    id,
                    "GOLDEN_BALL",
                    "GLOBAL",
                    year,
                    "Bola de Ouro",
                    "Premiação mundial · " + year,
                    LocalDate.of(year, 12, 31),
                    40,
                    "Prêmio de desempenho da liga: 75% média das notas, 20% produção estatística comparada aos atletas da mesma posição e 5% habilidade geral. Exige pelo menos 10 partidas avaliadas e 600 minutos no ano civil. Só competições oficiais.",
                    PlayerAwardRules.goldenBall(players),
                    automatic
                );
                created = true;
            }
            if (!repository.exists(id, "GOLDEN_BOOT", "GLOBAL", year)) {
                repository.award(
                    id,
                    "GOLDEN_BOOT",
                    "GLOBAL",
                    year,
                    "Chuteira de Ouro",
                    "Premiação mundial · " + year,
                    LocalDate.of(year, 12, 31),
                    41,
                    "Gols em todos os torneios oficiais durante o ano civil. Desempates: assistências, média das notas, menos minutos e identificação do atleta. Amistosos e cobranças de desempate não contam.",
                    PlayerAwardRules.scorers(players),
                    automatic
                );
                created = true;
            }
        }
        if (created) events.publishEvent(new SaveChangedEvent(id));
    }
}
