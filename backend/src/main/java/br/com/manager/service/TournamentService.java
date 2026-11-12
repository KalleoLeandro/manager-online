package br.com.manager.service;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.repository.*;
import br.com.manager.utils.*;
import br.com.manager.utils.TournamentRulebooks.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentService {

    private final TournamentRepository repository;
    private final NationalSeasonRepository dates;
    private final TournamentGroupService groups;
    private final TournamentDrawService draws;
    private final TournamentParticipationService participation;
    private final TournamentCalendarService calendar;

    public TournamentService(
        TournamentRepository repository,
        NationalSeasonRepository dates,
        TournamentGroupService groups,
        TournamentDrawService draws,
        TournamentParticipationService participation,
        TournamentCalendarService calendar
    ) {
        this.repository = repository;
        this.dates = dates;
        this.groups = groups;
        this.draws = draws;
        this.participation = participation;
        this.calendar = calendar;
    }

    public List<Rulebook> rules(int year) {
        var result = new ArrayList<Rulebook>();
        result.add(TournamentRulebooks.copaBrasil(year));
        result.add(TournamentRulebooks.faCup(year));
        result.add(TournamentRulebooks.coupeFrance(year));
        result.add(TournamentRulebooks.copaRey(year));
        result.add(TournamentRulebooks.coppaItalia(year));
        result.add(TournamentRulebooks.dfbPokal(year));
        result.add(TournamentRulebooks.championsLeague(year));
        result.add(TournamentRulebooks.libertadores(year));
        result.add(TournamentRulebooks.intercontinental(year));
        if ((year - 2025) % 4 == 0) result.add(TournamentRulebooks.clubWorldCup(year));
        if (year != 2026) return result
            .stream()
            .map(r ->
                new Rulebook(
                    r.key(),
                    r.name(),
                    r.country(),
                    r.startYear(),
                    r.version().contains("PROJECTED") ? r.version() : r.version() + "-PROJECTED",
                    r.source(),
                    r.participants(),
                    r.stages()
                )
            )
            .toList();
        return result;
    }

    public LocalDate nextDraw(String id, LocalDate from) {
        var next = repository
            .editions(id)
            .stream()
            .filter(e -> e.status().equals("ACTIVE") && !repository.stageExists(e.id(), e.currentStage()))
            .filter(e -> TournamentDrawSchedule.ceremony(e, e.currentStage()))
            .map(e -> TournamentDrawSchedule.date(e, e.currentStage()))
            .filter(d -> !d.isBefore(from))
            .min(LocalDate::compareTo)
            .orElse(null);
        int nextWorld = 2025 + 4 * Math.floorDiv(from.getYear() - 2025, 4) + 4;
        var worldDraw = LocalDate.of(nextWorld - 1, 12, 5);
        if (!worldDraw.isBefore(from) && (next == null || worldDraw.isBefore(next))) next = worldDraw;
        return next;
    }

    public List<br.com.manager.models.dto.CalendarEventDto> drawEvents(String id, LocalDate first, LocalDate last) {
        var result = new ArrayList<br.com.manager.models.dto.CalendarEventDto>();
        for (var edition : repository.editions(id))
            for (int stage = 0; stage < edition.rules().stages().size(); stage++) {
                if (!TournamentDrawSchedule.ceremony(edition, stage)) continue;
                var date = TournamentDrawSchedule.date(edition, stage);
                if (date.isBefore(first) || date.isAfter(last)) continue;
                result.add(
                    new br.com.manager.models.dto.CalendarEventDto(
                        "draw-" + edition.id() + "-" + stage,
                        date,
                        "DRAW",
                        "Sorteio: " + edition.name(),
                        edition.rules().stages().get(stage).label(),
                        repository.stageExists(edition.id(), stage) ? "COMPLETED" : "SCHEDULED"
                    )
                );
            }
        return result;
    }

    @Transactional
    public void synchronize(String id, LocalDate date) {
        participation.historical(id);
        int initialYear = dates.start(id).getYear();
        for (int year = initialYear - 1; year <= date.getYear() + 1; year++) for (var rules : rules(year)) {
            if (
                year > date.getYear() &&
                !(rules.key().equals("CLUB_WORLD_CUP") && !date.isBefore(LocalDate.of(year - 1, 12, 1)))
            ) continue;
            if (
                year < initialYear &&
                !(
                    rules.key().equals("CHAMPIONS") ||
                    List.of("ENG_CUP", "ESP_CUP", "ITA_CUP", "GER_CUP", "FRA_CUP").contains(rules.key())
                )
            ) continue;
            if (
                repository
                    .editions(id)
                    .stream()
                    .anyMatch(e -> e.key().equals(rules.key()) && e.year() == rules.startYear())
            ) continue;
            TournamentRulebooks.validate(rules);
            var entries = participation.entries(id, rules);
            if (entries.size() != rules.participants() && !rules.key().equals("INTERCONTINENTAL")) continue;
            if (entries.isEmpty()) continue;
            boolean complete = rules
                .stages()
                .stream()
                .allMatch(
                    stage ->
                        entries
                            .stream()
                            .filter(t -> t.stage().equals(stage.key()))
                            .count() == stage.newEntrants()
                );
            if (complete || rules.key().equals("INTERCONTINENTAL")) repository.create(id, rules, entries);
        }
        var initial = dates.start(id);
        var humanTeams = repository.humanTeams(id);
        for (int pass = 0; pass < 40; pass++) {
            boolean changed = false;
            for (var edition : repository.editions(id)) {
                if (!edition.status().equals("ACTIVE")) continue;
                if (edition.key().equals("INTERCONTINENTAL")) repository.addTeams(
                    id,
                    edition.id(),
                    participation.entries(id, edition.rules())
                );
                var teams = repository.teams(id, edition.id());
                int stage = edition.currentStage();
                var rules = edition.rules().stages().get(stage);
                if (!repository.stageExists(edition.id(), stage)) {
                    if (date.isBefore(TournamentDrawSchedule.date(edition, stage))) continue;
                    var entrants = repository.stageTeams(id, edition, stage);
                    if (entrants.size() != rules.entrants()) continue;
                    if (groups.isGroup(edition, stage)) {
                        groups.create(
                            id,
                            edition,
                            stage,
                            entrants,
                            ((long) id.hashCode() << 32) ^ edition.id() ^ stage
                        );
                    } else {
                        var draw = KnockoutDraw.draw(
                            edition.id(),
                            stage,
                            rules,
                            entrants,
                            ((long) id.hashCode() << 32) ^ edition.id() ^ stage
                        );
                        var requested = new ArrayList<NationalLeagueDraw.Fixture>();
                        for (var tie : draw)
                            for (int leg = 1; leg <= rules.legs(); leg++) requested.add(
                                new NationalLeagueDraw.Fixture(
                                    leg,
                                    leg == 1 ? rules.firstDate() : rules.secondDate(),
                                    leg == 1 ? tie.home() : tie.away(),
                                    leg == 1 ? tie.away() : tie.home()
                                )
                            );
                        var planned = calendar.plan(id, requested);
                        repository.createStage(
                            id,
                            edition,
                            stage,
                            draw,
                            planned.stream().map(NationalLeagueDraw.Fixture::date).toList()
                        );
                        calendar.protectNationals(id, planned);
                    }
                    var drawDate = TournamentDrawSchedule.date(edition, stage);
                    if (
                        !drawDate.isBefore(initial) &&
                        (!drawDate.isBefore(date.minusDays(7)) ||
                            entrants.stream().anyMatch(t -> humanTeams.contains(t.id())))
                    ) draws.record(id, edition, stage, drawDate.isBefore(date) ? date : drawDate);
                    changed = true;
                }
                var games = repository.games(id, edition.id());
                for (var game : games) {
                    if (
                        game.stage() != stage || !game.status().equals("SCHEDULED") || game.date().isAfter(date)
                    ) continue;
                    if (
                        !game.date().isBefore(initial) &&
                        (humanTeams.contains(game.home()) || humanTeams.contains(game.away()))
                    ) continue;
                    var score = BackgroundMatchRules.play(
                        id,
                        game.id(),
                        game.homeRating(),
                        game.awayRating(),
                        90,
                        rules.neutral()
                    );
                    int home = score.home(),
                        away = score.away();
                    Integer hp = null,
                        ap = null;
                    if (!groups.isGroup(edition, stage) && game.leg() == rules.legs()) {
                        int aggregateHome = home,
                            aggregateAway = away;
                        if (game.leg() == 2) {
                            var first = games
                                .stream()
                                .filter(g -> g.stage() == stage && g.number() == game.number() && g.leg() == 1)
                                .findFirst()
                                .orElseThrow();
                            if (!first.status().equals("PLAYED")) continue;
                            aggregateHome += first.awayGoals();
                            aggregateAway += first.homeGoals();
                        }
                        if (aggregateHome == aggregateAway && rules.extraTime()) {
                            var extra = BackgroundMatchRules.play(
                                id,
                                game.id() ^ 0x4558545241L,
                                game.homeRating(),
                                game.awayRating(),
                                30,
                                rules.neutral()
                            );
                            int extraHome = extra.home(),
                                extraAway = extra.away();
                            home += extraHome;
                            away += extraAway;
                            aggregateHome += extraHome;
                            aggregateAway += extraAway;
                        }
                        if (aggregateHome == aggregateAway) {
                            var shootout = PenaltyShootoutRules.play(
                                .76,
                                .76,
                                new Random(game.id() ^ id.hashCode() ^ 0x50454E53)
                            );
                            hp = shootout.home();
                            ap = shootout.away();
                            aggregateHome = hp;
                            aggregateAway = ap;
                        }
                        repository.resolve(
                            edition.id(),
                            stage,
                            game.number(),
                            aggregateHome > aggregateAway ? game.home() : game.away()
                        );
                    }
                    repository.result(id, game, home, away, hp, ap);
                    if (edition.key().equals("CLUB_WORLD_CUP") && groups.isGroup(edition, stage)) {
                        repository.fairPlay(
                            id,
                            edition.id(),
                            game.id(),
                            game.home(),
                            TournamentFairPlay.background(game.id() ^ id.hashCode()),
                            true
                        );
                        repository.fairPlay(
                            id,
                            edition.id(),
                            game.id(),
                            game.away(),
                            TournamentFairPlay.background(game.id() ^ id.hashCode() ^ 0x41574159L),
                            true
                        );
                    }
                    changed = true;
                }
                if (groups.isGroup(edition, stage)) {
                    changed |= groups.advance(id, edition, stage, repository.games(id, edition.id()));
                    continue;
                }
                var ties = repository.ties(edition.id(), stage);
                if (!ties.isEmpty() && ties.stream().allMatch(t -> t.winner() != null)) {
                    var awardedOn = games
                        .stream()
                        .filter(g -> g.stage() == stage)
                        .map(Game::date)
                        .max(LocalDate::compareTo)
                        .orElse(date);
                    if (stage == edition.rules().stages().size() - 1) {
                        var finalTie = ties.getFirst();
                        long winner = finalTie.winner(),
                            runner = winner == finalTie.home() ? finalTie.away() : finalTie.home();
                        repository.champion(edition.id(), winner, runner);
                        repository.award(
                            id,
                            edition.id(),
                            stage,
                            winner,
                            TournamentTrophies.forCompetition(edition.key()),
                            awardedOn
                        );
                        qualifications(id, edition, winner, runner, awardedOn);
                    } else {
                        var entrants = repository.stageTeams(id, edition, stage);
                        for (var tie : ties) {
                            int seed = entrants
                                .stream()
                                .filter(t ->
                                    edition.key().equals("CHAMPIONS")
                                        ? t.id() == tie.home() || t.id() == tie.away()
                                        : t.id() == tie.winner()
                                )
                                .mapToInt(Entrant::rank)
                                .min()
                                .orElseThrow();
                            int target = edition.key().equals("INTERCONTINENTAL") && stage == 1 ? 3 : stage + 1;
                            repository.advanceTeam(edition.id(), target, tie.winner(), seed, null, tie.number());
                            if (edition.key().equals("INTERCONTINENTAL") && stage > 0) repository.award(
                                id,
                                edition.id(),
                                stage,
                                tie.winner(),
                                stage == 1 ? 34 : stage == 2 ? 35 : 36,
                                awardedOn
                            );
                        }
                        repository.nextStage(edition.id(), stage + 1);
                    }
                    changed = true;
                }
            }
            if (!changed) break;
        }
    }

    private void qualifications(String id, Edition edition, long winner, long runner, LocalDate date) {
        if (edition.key().equals("CHAMPIONS") || edition.key().equals("LIBERTADORES")) {
            int finalYear = edition.key().equals("CHAMPIONS") ? edition.year() + 1 : edition.year();
            repository.qualify(
                id,
                "INTERCONTINENTAL",
                finalYear,
                winner,
                edition.key(),
                edition.year(),
                "CONTINENTAL_CHAMPION",
                date
            );
            int mundial = 2025 + 4 * Math.floorDiv(finalYear - 2025, 4) + 4;
            repository.qualify(
                id,
                "CLUB_WORLD_CUP",
                mundial,
                winner,
                edition.key(),
                edition.year(),
                "CONTINENTAL_CHAMPION",
                date
            );
            repository.qualify(
                id,
                edition.key(),
                edition.year() + 1,
                winner,
                edition.key(),
                edition.year(),
                "TITLE_HOLDER",
                date
            );
            return;
        }
        if (edition.country().equals("Mundo")) return;
        if (edition.key().equals("BRA_CUP")) {
            repository.qualify(
                id,
                "LIBERTADORES",
                edition.year() + 1,
                winner,
                edition.key(),
                edition.year(),
                "CUP_WINNER_GROUP_STAGE",
                date
            );
            repository.qualify(
                id,
                "LIBERTADORES",
                edition.year() + 1,
                runner,
                edition.key(),
                edition.year(),
                "CUP_RUNNER_UP_QUALIFYING",
                date
            );
        } else repository.qualify(
            id,
            "EUROPA_LEAGUE",
            edition.year() + 1,
            winner,
            edition.key(),
            edition.year(),
            "DOMESTIC_CUP_WINNER",
            date
        );
    }

    public void configure(String id, MatchStateDto state) {
        var found = repository.forFixture(id, state.fixtureId);
        if (found.isEmpty()) return;
        var edition = found.get();
        var games = repository.games(id, edition.id());
        var game = games
            .stream()
            .filter(g -> g.id() == state.fixtureId)
            .findFirst()
            .orElseThrow();
        var stage = edition.rules().stages().get(game.stage());
        state.cupMatch = true;
        state.decisiveTie = !groups.isGroup(edition, game.stage()) && game.leg() == stage.legs();
        state.extraTimeAllowed = stage.extraTime();
        state.neutralVenue = stage.neutral();
        state.disciplineSeasonId = repository.disciplineSeason(edition.id());
        state.suspensionThreshold = edition.key().equals("GER_CUP") ? 5 : 3;
        if (state.decisiveTie && game.leg() == 2) {
            var first = games
                .stream()
                .filter(g -> g.stage() == game.stage() && g.number() == game.number() && g.leg() == 1)
                .findFirst()
                .orElseThrow();
            if (!first.status().equals("PLAYED")) throw new IllegalStateException(
                "A partida de ida ainda não terminou."
            );
            state.aggregateHomeBefore = first.awayGoals();
            state.aggregateAwayBefore = first.homeGoals();
        }
    }

    public void completed(String id, MatchStateDto state) {
        if (!state.cupMatch || state.friendlyMatch) return;
        var edition = repository.forFixture(id, state.fixtureId).orElseThrow();
        if (edition.key().equals("CLUB_WORLD_CUP") && !state.decisiveTie) {
            repository.fairPlay(
                id,
                edition.id(),
                state.fixtureId,
                state.home.id,
                TournamentFairPlay.points(state.home),
                false
            );
            repository.fairPlay(
                id,
                edition.id(),
                state.fixtureId,
                state.away.id,
                TournamentFairPlay.points(state.away),
                false
            );
        }
        if (!state.decisiveTie) return;
        var game = repository
            .games(id, edition.id())
            .stream()
            .filter(g -> g.id() == state.fixtureId)
            .findFirst()
            .orElseThrow();
        int home = state.home.goals + state.aggregateHomeBefore,
            away = state.away.goals + state.aggregateAwayBefore;
        if (home == away) {
            if (
                state.homePenalties == null ||
                state.awayPenalties == null ||
                state.homePenalties.equals(state.awayPenalties)
            ) throw new IllegalStateException("O confronto empatado precisa de decisão por pênaltis.");
            home = state.homePenalties;
            away = state.awayPenalties;
        }
        repository.resolve(edition.id(), game.stage(), game.number(), home > away ? state.home.id : state.away.id);
    }
}
