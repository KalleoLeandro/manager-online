package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.*;
import br.com.manager.models.responses.*;
import br.com.manager.repository.MatchRepository;
import br.com.manager.service.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MatchdayTests {

    @Test
    void touchlineActionsPersistEventsRejectStaleRevisionsAndCannotRunWhilePaused() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var state = own(a);
        matches.action(
            id,
            state.fixtureId(),
            header(a),
            new MatchActionRequest("COMPLAIN", null, null, null, null, state.revision())
        );
        var current = own(a);
        var team = current.home().human ? current.home() : current.away();
        assertThat(team.touchline.refereeConfidence).isEqualTo(70);
        assertThat(team.touchline.command).isEqualTo("COMPLAIN");
        assertThat(team.touchline.commandDuration).isEqualTo(5);
        assertThat(current.events()).anyMatch(e -> e.type().equals("REFEREE_NOTICE"));
        assertThatThrownBy(() ->
            matches.action(
                id,
                state.fixtureId(),
                header(a),
                new MatchActionRequest("ENCOURAGE", null, null, null, null, state.revision())
            )
        ).isInstanceOf(DefaultErrorException.class);
        matches.action(
            id,
            state.fixtureId(),
            header(a),
            new MatchActionRequest("OPEN_TACTICS", null, null, null, null, current.revision())
        );
        current = own(a);
        long revision = current.revision();
        assertThatThrownBy(() ->
            matches.action(
                id,
                state.fixtureId(),
                header(a),
                new MatchActionRequest("COMPLAIN", null, null, null, null, revision)
            )
        ).isInstanceOf(DefaultErrorException.class);
        var persisted = repository.session(id, state.fixtureId()).orElseThrow().state();
        assertThat((persisted.home.human ? persisted.home : persisted.away).touchline.complaints).isEqualTo(1);
    }

    @Test
    void customPositioningIsValidatedAndStoredOnlyWhileItsCoachIsAdjusting() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var state = own(a);
        var team = state.home().human ? state.home() : state.away();
        var defender = team.players
            .stream()
            .filter(p -> p.onPitch && p.position.equals("DEF"))
            .findFirst()
            .orElseThrow();
        var plan = new br.com.manager.models.dto.TacticalPlanDto();
        plan.passingRisk = 75;
        plan.positions.put(defender.id, new br.com.manager.models.dto.PitchPositionDto(20.0, 40.0));
        assertThatThrownBy(() ->
            matches.action(
                id,
                state.fixtureId(),
                header(a),
                new MatchActionRequest("TACTICS", null, null, "BALANCED", "4-3-3", state.revision(), plan)
            )
        ).isInstanceOf(DefaultErrorException.class);
        matches.action(
            id,
            state.fixtureId(),
            header(a),
            new MatchActionRequest("OPEN_TACTICS", null, null, null, null, state.revision())
        );
        matches.action(
            id,
            state.fixtureId(),
            header(a),
            new MatchActionRequest("TACTICS", null, null, "BALANCED", "4-3-3", own(a).revision(), plan)
        );
        var current = repository.session(id, state.fixtureId()).orElseThrow().state();
        var changed = current.home.human ? current.home : current.away;
        assertThat(changed.tacticalPlan.positions.get(defender.id)).isEqualTo(
            new br.com.manager.models.dto.PitchPositionDto(20.0, 40.0)
        );
        assertThat(changed.tacticalPlan.passingRisk).isEqualTo(75);
        assertThat(current.status).isEqualTo("PAUSED");
        var foreign = new br.com.manager.models.dto.TacticalPlanDto();
        foreign.positions.put(Long.MAX_VALUE, new br.com.manager.models.dto.PitchPositionDto(50.0, 50.0));
        assertThatThrownBy(() ->
            matches.action(
                id,
                state.fixtureId(),
                header(a),
                new MatchActionRequest("TACTICS", null, null, "BALANCED", "4-3-3", own(a).revision(), foreign)
            )
        ).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void openingTacticsAgainstAiStopsOnlyThatClockUntilTheCoachFinishes() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var state = own(a);
        assertThat(repository.session(id, state.fixtureId()).orElseThrow().state().engineVersion).isEqualTo(
            br.com.manager.utils.MatchEngine.CURRENT_VERSION
        );
        var paused = matches
            .action(id, state.fixtureId(), header(a), new MatchActionRequest("OPEN_TACTICS", null, null, null, null, 0))
            .selected();
        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(paused.awaitingCoachDecision()).isTrue();
        assertThat(paused.pausedUntil()).isZero();
        var again = matches
            .action(id, state.fixtureId(), header(a), new MatchActionRequest("OPEN_TACTICS", null, null, null, null, 0))
            .selected();
        assertThat(again.revision()).isEqualTo(paused.revision());
        now.updateAndGet(t -> t.plusSeconds(180));
        matches.tick(id);
        assertThat(own(a).minute()).isEqualTo(state.minute());
        assertThat(own(a).status()).isEqualTo("PAUSED");
        assertThat(repository.currentDay(id)).anyMatch(
            r -> r.state().fixtureId != state.fixtureId() && r.state().minute > 0
        );
        matches.action(
            id,
            state.fixtureId(),
            header(a),
            new MatchActionRequest("TACTICS", null, null, "ATTACKING", "4-4-2", own(a).revision())
        );
        assertThat(own(a).status()).isEqualTo("PAUSED");
        assertThat((own(a).home().human ? own(a).home() : own(a).away()).formation).isEqualTo("4-4-2");
        acknowledgeCoach(a);
        assertThat(own(a).status()).isEqualTo("LIVE");
        tick(id);
        assertThat(own(a).minute()).isGreaterThan(state.minute());
    }

    @Test
    void openingTacticsInAHumanDuelRequiresBothConfirmationsAndSupportsReopening() {
        var a = create(2);
        var id = a.room().id();
        long teamId = a.room().members().getFirst().teamId();
        var fixture = db.queryForMap(
            "SELECT home_team_id,away_team_id FROM national_fixtures WHERE save_id=? AND (home_team_id=? OR away_team_id=?) ORDER BY match_date,id LIMIT 1",
            id,
            teamId,
            teamId
        );
        long home = ((Number) fixture.get("home_team_id")).longValue(),
            away = ((Number) fixture.get("away_team_id")).longValue();
        var b = saves.join(a.room().inviteCode(), new JoinLeagueRequest("Outro técnico", home == teamId ? away : home));
        ready(a);
        ready(b);
        startOnMatchDay(id, header(a));
        matches.action(
            id,
            own(a).fixtureId(),
            header(a),
            new MatchActionRequest("OPEN_TACTICS", null, null, null, null, 0)
        );
        assertThat(own(a).pendingCoachTeams()).hasSize(2);
        acknowledgeCoach(a);
        assertThatThrownBy(() ->
            matches.action(
                id,
                own(a).fixtureId(),
                header(a),
                new MatchActionRequest("TACTICS", null, null, "BALANCED", "4-4-2", own(a).revision())
            )
        ).isInstanceOf(DefaultErrorException.class);
        matches.action(
            id,
            own(a).fixtureId(),
            header(a),
            new MatchActionRequest("OPEN_TACTICS", null, null, null, null, 0)
        );
        assertThat(own(a).pendingCoachTeams()).hasSize(2);
        acknowledgeCoach(b);
        assertThat(own(a).status()).isEqualTo("PAUSED");
        now.updateAndGet(t -> t.plusSeconds(180));
        matches.tick(id);
        assertThat(own(a).minute()).isZero();
        acknowledgeCoach(a);
        assertThat(own(a).status()).isEqualTo("LIVE");
    }

    @Test
    void tacticsPauseRejectsOtherClubsAndWaitsForGoalCelebration() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var other = repository
            .currentDay(id)
            .stream()
            .filter(r -> r.state().fixtureId != own(a).fixtureId())
            .findFirst()
            .orElseThrow();
        assertThatThrownBy(() ->
            matches.action(
                id,
                other.state().fixtureId,
                header(a),
                new MatchActionRequest("OPEN_TACTICS", null, null, null, null, 0)
            )
        ).isInstanceOf(DefaultErrorException.class);
        var row = repository.session(id, own(a).fixtureId()).orElseThrow();
        var state = row.state();
        state.goalCelebrationUntil = now.get().plusSeconds(5).toEpochMilli();
        repository.save(id, state, row.nextTick());
        assertThatThrownBy(() ->
            matches.action(
                id,
                state.fixtureId,
                header(a),
                new MatchActionRequest("OPEN_TACTICS", null, null, null, null, 0)
            )
        ).isInstanceOf(DefaultErrorException.class);
        assertThat(own(a).status()).isEqualTo("LIVE");
    }

    @Test
    void soloSpeedPersistsAcrossRoundsAndNeverShortensCelebrationsOrCoachDecisions() {
        var a = create(1);
        var id = a.room().id();
        assertThat(matches.view(id, header(a), null).canChangeSpeed()).isTrue();
        assertThat(matches.speed(id, header(a), 400, null).speedPercent()).isEqualTo(400);
        ready(a);
        startOnMatchDay(id, header(a));
        var row = repository.session(id, own(a).fixtureId()).orElseThrow();
        assertThat(row.nextTick()).isEqualTo(now.get().plusMillis(375));
        var state = row.state();
        var eventsBefore = java.util.List.copyOf(state.events);
        state.goalCelebrationUntil = now.get().plusSeconds(5).toEpochMilli();
        repository.save(id, state, now.get().plusSeconds(5));
        matches.speed(id, header(a), 50, state.fixtureId);
        var held = repository.session(id, state.fixtureId).orElseThrow();
        assertThat(held.nextTick()).isEqualTo(now.get().plusSeconds(5));
        assertThat(held.state().events).isEqualTo(eventsBefore);
        assertThat(held.state().minute).isEqualTo(state.minute);
        state = held.state();
        state.status = "PAUSED";
        state.awaitingCoachDecision = true;
        state.pausedUntil = 0;
        state.goalCelebrationUntil = 0;
        repository.save(id, state, now.get().plusSeconds(30));
        matches.speed(id, header(a), 200, null);
        assertThat(repository.session(id, state.fixtureId).orElseThrow().nextTick()).isEqualTo(
            now.get().plusSeconds(30)
        );
        assertThat(matches.view(id, header(a), null).speedPercent()).isEqualTo(200);
        assertThatThrownBy(() -> matches.speed(id, header(a), 300, null)).isInstanceOf(DefaultErrorException.class);
        assertThat(matches.view(id, header(a), null).speedPercent()).isEqualTo(200);
    }

    @Test
    void onlineRoomsRejectSpeedChangesAndLocalRoomsShareOnlyTheOwnersChoice() {
        var online = create(2);
        assertThat(matches.view(online.room().id(), header(online), null).canChangeSpeed()).isFalse();
        assertThatThrownBy(() -> matches.speed(online.room().id(), header(online), 400, null)).isInstanceOf(
            DefaultErrorException.class
        );
        var teams = catalog.teams(null);
        var a = saves.create(new CreateLeagueRequest("Multiplayer local", 2, "Criador", teams.get(0).id(), true));
        var b = saves.addLocal(a.room().id(), header(a), new JoinLeagueRequest("Amigo local", teams.get(1).id()));
        var id = a.room().id();
        assertThat(saves.room(id).localMultiplayer()).isTrue();
        assertThat(matches.view(id, header(b), null).canChangeSpeed()).isFalse();
        assertThatThrownBy(() -> matches.speed(id, header(b), 200, null)).isInstanceOf(DefaultErrorException.class);
        matches.speed(id, header(a), 200, null);
        assertThat(matches.view(id, header(b), null).speedPercent()).isEqualTo(200);
        ready(a);
        ready(b);
        startOnMatchDay(id, header(a));
        assertThat(repository.currentDay(id)).allMatch(row -> row.nextTick().equals(now.get().plusMillis(750)));
        matches.speed(id, header(a), 400, null);
        assertThat(repository.currentDay(id)).allMatch(row -> row.nextTick().equals(now.get().plusMillis(375)));
        assertThat(matches.view(id, header(b), null).speedPercent()).isEqualTo(400);
        assertThatThrownBy(() -> matches.speed(id, "Bearer invalid", 100, null)).isInstanceOf(
            DefaultErrorException.class
        );
    }

    private void unreviewedHalftime(br.com.manager.models.dto.MatchStateDto state) {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        for (long seed = 1; seed <= 100; seed++) {
            state.seed = seed;
            var preview = mapper.readValue(
                mapper.writeValueAsString(state),
                br.com.manager.models.dto.MatchStateDto.class
            );
            br.com.manager.utils.MatchEngine.step(preview);
            if (
                preview.varReview == null && preview.events.stream().anyMatch(e -> e.type().equals("HALF_TIME"))
            ) return;
        }
        throw new AssertionError("No halftime fixture without VAR found");
    }

    private void verifyVarFlow(boolean confirmed) {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var row = repository.session(id, own(a).fixtureId()).orElseThrow();
        var state = VarRulesTests.reviewed(confirmed, false);
        state.fixtureId = row.state().fixtureId;
        state.seasonId = row.state().seasonId;
        state.date = row.state().date;
        // Retain real fixture/player identities while setting up a reviewed final-minute incident.
        var actual = row.state();
        var team = actual.home;
        var scorer = team.players.getFirst();
        var assist = team.players.get(1);
        team.goals = 1;
        scorer.goals = 1;
        scorer.shots = 1;
        assist.assists = 1;
        team.stats.shots = 1;
        team.stats.shotsOnTarget = 1;
        team.stats.expectedGoals = .25;
        actual.events.clear();
        br.com.manager.utils.MatchEngine.event(actual, "GOAL_PENDING", team, scorer, assist, "Gol em revisão");
        br.com.manager.utils.MatchEngine.event(actual, "VAR_REVIEW", team, scorer, null, "VAR");
        actual.seed = state.seed;
        actual.varReview = state.varReview;
        actual.varReview.teamId = team.id;
        actual.varReview.playerId = scorer.id;
        actual.varReview.assistId = assist.id;
        actual.varReview.phaseUntil = now.get().plusSeconds(4).toEpochMilli();
        actual.finishAfterCelebration = true;
        actual.period = 2;
        actual.minute = 94;
        repository.save(id, actual, now.get().plusSeconds(4));
        assertThat(own(a).varReview().phase()).isEqualTo("REVIEW");
        assertThat(own(a).varReview().confirmed()).isNull();
        assertThat(own(a).varReview().reason()).isNull();
        now.updateAndGet(t -> t.plusSeconds(4));
        matches.tick(id);
        assertThat(own(a).varReview().phase()).isEqualTo("DECISION");
        assertThat(own(a).varReview().confirmed()).isEqualTo(confirmed);
        assertThat(own(a).goalCelebrationRemainingMillis()).isZero();
        assertThat(own(a).minute()).isEqualTo(94);
        assertThat(repository.currentDay(id)).anyMatch(
            r -> r.state().fixtureId != actual.fixtureId && r.state().minute > 0
        );
        now.updateAndGet(t -> t.plusSeconds(3));
        matches.tick(id);
        assertThat(own(a).varReview()).isNull();
        if (confirmed) {
            assertThat(own(a).goalCelebrationRemainingMillis()).isEqualTo(5000);
            assertThat(own(a).status()).isEqualTo("LIVE");
            now.updateAndGet(t -> t.plusSeconds(5));
            matches.tick(id);
        } else {
            assertThat(own(a).goalCelebrationRemainingMillis()).isZero();
            assertThat(own(a).home().goals).isZero();
        }
        assertThat(own(a).status()).isEqualTo("FINISHED");
        assertThat(
            db.queryForObject("SELECT home_goals FROM national_fixtures WHERE id=?", Integer.class, actual.fixtureId)
        ).isEqualTo(confirmed ? 1 : 0);
    }

    @Test
    void confirmedVarCompletesReviewThenDecisionThenGoalCelebrationBeforeFinalizing() {
        verifyVarFlow(true);
    }

    @Test
    void disallowedVarFinalizesTheCorrectScoreWithoutGoalCelebration() {
        verifyVarFlow(false);
    }

    private void acknowledgeCoach(LeagueAccessResponse a) {
        var m = own(a);
        if (m.awaitingCoachDecision() && m.canResume()) matches.action(
            a.room().id(),
            m.fixtureId(),
            header(a),
            new MatchActionRequest("RESUME", null, null, null, null, m.revision())
        );
    }

    @Test
    void humanHalftimeWaitsIndefinitelyAndSubstitutionsRemainManualEvenAgainstAi() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var state = repository.session(id, own(a).fixtureId()).orElseThrow().state();
        var human = state.home.human ? state.home : state.away;
        human.substitutionWindows = 3;
        state.minute = 44;
        state.period = 1;
        state.addedFirst = 0;
        unreviewedHalftime(state);
        repository.save(id, state, now.get());
        tick(id);
        var paused = own(a);
        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(paused.awaitingCoachDecision()).isTrue();
        assertThat(paused.canResume()).isTrue();
        assertThat(paused.pendingCoachTeams()).containsExactly(human.id);
        var before = repository.session(id, state.fixtureId).orElseThrow().state();
        assertThat(before.halfTimeBreak).isTrue();
        now.updateAndGet(t -> t.plusSeconds(180));
        matches.tick(id);
        assertThat(own(a).status()).isEqualTo("PAUSED");
        assertThat(own(a).minute()).isEqualTo(45);
        assertThat(repository.currentDay(id)).anyMatch(
            r -> r.state().fixtureId != state.fixtureId && r.state().minute >= 2
        );
        var team = own(a).home().human ? own(a).home() : own(a).away();
        assertThat(team.substitutions).isZero();
        var out = team.players
            .stream()
            .filter(p -> p.onPitch)
            .findFirst()
            .orElseThrow();
        var in = team.players
            .stream()
            .filter(p -> !p.entered)
            .findFirst()
            .orElseThrow();
        matches.action(
            id,
            state.fixtureId,
            header(a),
            new MatchActionRequest("SUBSTITUTE", out.id, in.id, null, null, own(a).revision())
        );
        team = own(a).home().human ? own(a).home() : own(a).away();
        assertThat(team.substitutions).isEqualTo(1);
        assertThat(team.substitutionWindows).isEqualTo(3);
        acknowledgeCoach(a);
        tick(id);
        assertThat(own(a).minute()).isGreaterThan(45);
    }

    @Test
    void bothHumanCoachesMustConfirmHalftimeBeforeTheDuelResumes() {
        var a = create(2);
        var id = a.room().id();
        long ownTeam = a.room().members().getFirst().teamId();
        var fixture = db.queryForMap(
            "SELECT home_team_id,away_team_id FROM national_fixtures WHERE save_id=? AND (home_team_id=? OR away_team_id=?) ORDER BY match_date,id LIMIT 1",
            id,
            ownTeam,
            ownTeam
        );
        long home = ((Number) fixture.get("home_team_id")).longValue(),
            away = ((Number) fixture.get("away_team_id")).longValue();
        var b = saves.join(
            a.room().inviteCode(),
            new JoinLeagueRequest("Outro técnico", home == ownTeam ? away : home)
        );
        ready(a);
        ready(b);
        startOnMatchDay(id, header(a));
        var state = repository.session(id, own(a).fixtureId()).orElseThrow().state();
        state.minute = 44;
        state.period = 1;
        state.addedFirst = 0;
        unreviewedHalftime(state);
        repository.save(id, state, now.get());
        tick(id);
        assertThat(own(a).pendingCoachTeams()).hasSize(2);
        acknowledgeCoach(a);
        assertThat(own(a).status()).isEqualTo("PAUSED");
        assertThat(own(a).canResume()).isFalse();
        assertThat(own(b).canResume()).isTrue();
        now.updateAndGet(t -> t.plusSeconds(120));
        matches.tick(id);
        assertThat(own(a).status()).isEqualTo("PAUSED");
        acknowledgeCoach(b);
        assertThat(own(a).status()).isEqualTo("LIVE");
        assertThat(own(a).pendingCoachTeams()).isEmpty();
    }

    private void humanIncident(String type) {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var state = repository.session(id, own(a).fixtureId()).orElseThrow().state();
        long teamId = state.home.human ? state.home.id : state.away.id;
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String original = mapper.writeValueAsString(state);
        boolean found = false;
        for (long seed = 1; seed <= 20000; seed++) {
            var candidate = mapper.readValue(original, br.com.manager.models.dto.MatchStateDto.class);
            candidate.seed = seed;
            br.com.manager.utils.MatchEngine.step(candidate);
            if (candidate.events.stream().anyMatch(e -> e.type().equals(type) && e.teamId() == teamId)) {
                state.seed = seed;
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
        repository.save(id, state, now.get());
        tick(id);
        assertThat(own(a).awaitingCoachDecision()).isTrue();
        assertThat(own(a).canResume()).isTrue();
        int minute = own(a).minute();
        now.updateAndGet(t -> t.plusSeconds(120));
        matches.tick(id);
        assertThat(own(a).minute()).isEqualTo(minute);
        assertThat(own(a).status()).isEqualTo("PAUSED");
        var team = own(a).home().human ? own(a).home() : own(a).away();
        assertThat(team.substitutions).isZero();
        var injured = team.players
            .stream()
            .filter(p -> type.equals("RED") ? p.sentOff : p.injured)
            .findFirst()
            .orElseThrow();
        var reserve = team.players
            .stream()
            .filter(p -> !p.entered)
            .findFirst()
            .orElseThrow();
        var request = new MatchActionRequest("SUBSTITUTE", injured.id, reserve.id, null, null, own(a).revision());
        if (type.equals("RED")) assertThatThrownBy(() ->
            matches.action(id, state.fixtureId, header(a), request)
        ).isInstanceOf(DefaultErrorException.class);
        else {
            matches.action(id, state.fixtureId, header(a), request);
            assertThat((own(a).home().human ? own(a).home() : own(a).away()).substitutions).isEqualTo(1);
        }
        acknowledgeCoach(a);
        assertThat(own(a).status()).isEqualTo("LIVE");
    }

    @Test
    void injuryWaitsForTheHumanCoachWithoutAnAutomaticReplacement() {
        humanIncident("INJURY");
    }

    @Test
    void sendingOffWaitsForTheHumanCoachAndCannotBeReplaced() {
        humanIncident("RED");
    }

    @Test
    void goalCelebrationHoldsOnlyTheScoringMatchForFiveSecondsIncludingTheFinalGoal() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var first = own(a);
        var state = repository.session(id, first.fixtureId()).orElseThrow().state();
        state.period = 2;
        state.minute = 96;
        state.addedSecond = 3;
        state.penaltyTeam = state.home.id;
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        boolean found = false;
        for (long seed = 1; seed <= 100; seed++) {
            state.seed = seed;
            var candidate = mapper.readValue(
                mapper.writeValueAsString(state),
                br.com.manager.models.dto.MatchStateDto.class
            );
            br.com.manager.utils.MatchEngine.step(candidate);
            if (candidate.events.stream().anyMatch(e -> e.type().equals("GOAL"))) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
        repository.save(id, state, now.get());
        tick(id);
        var celebration = own(a);
        int minute = celebration.minute();
        assertThat(celebration.goalCelebrationRemainingMillis()).isEqualTo(5000);
        assertThat(celebration.celebratingGoalSequence()).isPositive();
        assertThat(celebration.status()).isEqualTo("LIVE");
        assertThat(
            db.queryForObject("SELECT status FROM national_fixtures WHERE id=?", String.class, state.fixtureId)
        ).isEqualTo("SCHEDULED");
        now.updateAndGet(t -> t.plusSeconds(4));
        matches.tick(id);
        assertThat(own(a).minute()).isEqualTo(minute);
        assertThat(own(a).goalCelebrationRemainingMillis()).isEqualTo(1000);
        assertThat(repository.currentDay(id)).anyMatch(
            r -> r.state().fixtureId != state.fixtureId && r.state().minute >= 2
        );
        now.updateAndGet(t -> t.plusSeconds(1));
        matches.tick(id);
        assertThat(own(a).status()).isEqualTo("FINISHED");
        assertThat(own(a).goalCelebrationRemainingMillis()).isZero();
        assertThat(
            db.queryForObject("SELECT status FROM national_fixtures WHERE id=?", String.class, state.fixtureId)
        ).isEqualTo("PLAYED");
    }

    @Test
    void halfTimeTalkAndPositionSwapAreAuthorizedAndRatingsArePersisted(){
        var access=create(1);var id=access.room().id();ready(access);startOnMatchDay(id,header(access));
        var view=own(access);var row=repository.session(id,view.fixtureId()).orElseThrow();var state=row.state();
        var team=state.home.human?state.home:state.away;var opponent=team==state.home?state.away:state.home;
        state.status="PAUSED";state.awaitingCoachDecision=true;state.halfTimeBreak=true;state.period=2;state.minute=45;state.pendingCoachTeams.clear();state.pendingCoachTeams.add(team.id);
        var first=team.players.stream().filter(p2->p2.onPitch&&p2.slot>0).findFirst().orElseThrow();
        var second=team.players.stream().filter(p2->p2.onPitch&&p2.slot>first.slot).findFirst().orElseThrow();
        int a=first.slot,b=second.slot;first.minutes=45;first.morale=60;first.sharpness=60;first.goals=1;
        repository.save(id,state,row.nextTick());
        matches.action(id,state.fixtureId,header(access),new MatchActionRequest("TALK_ENCOURAGE",null,first.id,null,null,state.revision));
        var updated=repository.session(id,state.fixtureId).orElseThrow().state();
        long revision=updated.revision;assertThat((updated.home.human?updated.home:updated.away).players.stream().filter(p2->p2.id==first.id).findFirst().orElseThrow().lastTalkPeriod).isEqualTo(2);
        assertThatThrownBy(()->matches.action(id,state.fixtureId,header(access),new MatchActionRequest("TALK_PRAISE",null,opponent.players.getFirst().id,null,null,revision))).isInstanceOf(DefaultErrorException.class);
        matches.action(id,state.fixtureId,header(access),new MatchActionRequest("SWAP_POSITIONS",first.id,second.id,null,null,revision));
        var swapped=repository.session(id,state.fixtureId).orElseThrow().state();var ownTeam=swapped.home.human?swapped.home:swapped.away;
        assertThat(ownTeam.players.stream().filter(p2->p2.id==first.id).findFirst().orElseThrow().slot).isEqualTo(b);
        assertThat(ownTeam.players.stream().filter(p2->p2.id==second.id).findFirst().orElseThrow().slot).isEqualTo(a);
        var unused=ownTeam.players.stream().filter(p2->!p2.entered).findFirst().orElseThrow();
        int oldMorale=db.queryForObject("SELECT morale FROM save_player_profiles WHERE save_id=? AND player_id=?",Integer.class,id,unused.id);
        unused.talkMoraleDelta=-5;unused.talkSharpnessDelta=-3;
        repository.finish(id,swapped,access.room().roundNumber());
        assertThat(db.queryForObject("SELECT morale FROM save_player_profiles WHERE save_id=? AND player_id=?",Integer.class,id,unused.id)).isEqualTo(Math.max(1,oldMorale-5));
        assertThat(db.queryForObject("SELECT rating FROM match_player_ratings WHERE save_id=? AND fixture_id=? AND player_id=?",Double.class,id,state.fixtureId,first.id)).isGreaterThan(6.0);
        assertThat(db.queryForObject("SELECT rated_appearances FROM match_player_seasons WHERE save_id=? AND player_id=?",Integer.class,id,first.id)).isEqualTo(1);
    }

    @Test
    void soloAndLocalMultiplayerStartWithoutReadinessButNetworkRequiresEveryone(){
        var solo=create(1);startOnMatchDay(solo.room().id(),header(solo));assertThat(own(solo)).isNotNull();
        var teams=catalog.teams(null);var local=saves.create(new CreateLeagueRequest("Local manual",2,"Coach",teams.get(0).id(),true));
        assertThatThrownBy(()->startOnMatchDay(local.room().id(),header(local))).isInstanceOf(DefaultErrorException.class);
        saves.addLocal(local.room().id(),header(local),new JoinLeagueRequest("Second coach",teams.get(1).id()));
        startOnMatchDay(local.room().id(),header(local));assertThat(own(local)).isNotNull();
        var network=create(2);var friend=saves.join(network.room().inviteCode(),new JoinLeagueRequest("Friend",teams.get(1).id()));
        assertThatThrownBy(()->startOnMatchDay(network.room().id(),header(network))).isInstanceOf(DefaultErrorException.class);
        ready(network);ready(friend);var date=repository.date(network.room().id());matches.tick(network.room().id());assertThat(repository.currentDay(network.room().id())).isEmpty();assertThat(repository.date(network.room().id())).isEqualTo(date);
        startOnMatchDay(network.room().id(),header(network));assertThat(own(network)).isNotNull();
    }

    @Autowired
    LeagueCalendarService calendar;

    @Test
    void sameDayMatchesWaitForEveryHumanEvenInDifferentCountries() {
        var brazil = catalog
            .teams(null)
            .stream()
            .filter(t -> t.leagueId().equals(6L))
            .findFirst()
            .orElseThrow();
        var english = catalog
            .teams(null)
            .stream()
            .filter(t -> t.leagueId().equals(1L))
            .findFirst()
            .orElseThrow();
        var a = saves.create(new CreateLeagueRequest("Países diferentes", 2, "Brasileiro", brazil.id()));
        var b = saves.join(a.room().inviteCode(), new JoinLeagueRequest("Inglês", english.id()));
        var id = a.room().id();
        db.update(
            "UPDATE national_fixtures SET match_date=DATE '2026-01-28' WHERE save_id=? AND season_id IN(SELECT id FROM national_seasons WHERE save_id=? AND league_id=1) AND round_number=1",
            id,
            id
        );
        ready(a);
        ready(b);
        startOnMatchDay(id, header(a));
        assertThat(repository.currentDay(id)).hasSize(20);
        assertThat(matches.view(id, header(a), null).selected().fixtureId()).isNotEqualTo(
            matches.view(id, header(b), null).selected().fixtureId()
        );
        for (var row : repository.currentDay(id)) {
            var state = row.state();
            state.period = 2;
            state.minute = 96;
            state.addedSecond = 3;
            // This test isolates the round barrier from random last-minute VAR/coach decisions.
            var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
            for (long seed = 1; seed < 1000; seed++) {
                state.seed = seed;
                var preview = mapper.readValue(
                    mapper.writeValueAsString(state),
                    br.com.manager.models.dto.MatchStateDto.class
                );
                int firstEvent = preview.events.size();
                br.com.manager.utils.MatchEngine.step(preview);
                if (
                    preview.status.equals("FINISHED") &&
                    preview.varReview == null &&
                    preview.events
                        .subList(firstEvent, preview.events.size())
                        .stream()
                        .noneMatch(e -> java.util.List.of("GOAL", "RED", "INJURY").contains(e.type()))
                ) break;
                if (seed == 999) throw new AssertionError("No uneventful final minute found");
            }
            if (state.home.id == english.id() || state.away.id == english.id()) {
                state.status = "PAUSED";
                state.pausedBy = english.id();
                state.pausedUntil = now.get().plusSeconds(60).toEpochMilli();
            }
            repository.save(id, state, now.get());
        }
        for (int grace = 0; grace < 4; grace++) tick(id);
        assertThat(saves.room(id).status()).isEqualTo("LIVE");
        assertThat(saves.room(id).roundNumber()).isEqualTo(1);
        assertThat(saves.room(id).members()).allMatch(m -> m.ready());
        assertThatCode(() -> startOnMatchDay(id, header(a))).doesNotThrowAnyException();
        assertThat(repository.currentDay(id)).hasSize(20);
        var progress = matches.view(id, header(a), null).participants();
        assertThat(progress).anyMatch(p -> p.teamId() == brazil.id() && p.status().equals("FINISHED"));
        assertThat(progress).anyMatch(p -> p.teamId() == english.id() && p.status().equals("PLAYING"));
        var waiting = matches.view(id, header(b), null).selected();
        matches.action(
            id,
            waiting.fixtureId(),
            header(b),
            new MatchActionRequest("RESUME", null, null, null, null, waiting.revision())
        );
        for (int grace = 0; grace < 4; grace++) tick(id);
        assertThat(saves.room(id).status()).isEqualTo("LOBBY");
        assertThat(saves.room(id).members()).allMatch(m -> !m.ready());
        assertThat(calendar.week(id, header(a)).currentDate()).isEqualTo(LocalDate.of(2026, 1, 29));
        assertThat(calendar.week(id, header(b)).currentDate()).isEqualTo(LocalDate.of(2026, 1, 29));
    }

    @Test
    void readinessNeverStartsMatchesOrChangesCalendarEvenWithLegacyPendingFlag() {
        var a = create(1);
        var id = a.room().id();
        db.update("UPDATE save_members SET ready=TRUE WHERE save_id=?", id);
        db.update("UPDATE game_saves SET status='READY',matchday_pending=FALSE WHERE id=?", id);
        assertThat(matches.candidates()).doesNotContain(id);
        matches.tick(id);
        assertThat(repository.currentDay(id)).isEmpty();
        ready(a);
        db.update("UPDATE game_saves SET matchday_pending=TRUE WHERE id=?",id);
        var date=repository.date(id);
        assertThat(matches.candidates()).doesNotContain(id);
        matches.tick(id);
        assertThat(repository.currentDay(id)).isEmpty();
        assertThat(repository.date(id)).isEqualTo(date);
        startOnMatchDay(id,header(a));
        assertThat(repository.currentDay(id)).isNotEmpty();
    }

    @Autowired
    MatchdayService matches;

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    ManagementService management;

    @Autowired
    MatchRepository repository;

    @Autowired
    JdbcTemplate db;

    @MockitoBean
    Clock clock;

    final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach
    void time() {
        now.set(Instant.parse("2026-11-04T12:00:00Z"));
        when(clock.instant()).thenAnswer(i -> now.get());
    }

    private void startOnMatchDay(String id, String authorization) {
        if (!saves.room(id).status().equals("LIVE")) {
            var date = repository.nextHumanDate(id).orElseThrow();
            db.update("UPDATE save_teams SET calendar_day=(SELECT DATEDIFF('DAY',calendar_start_date,?) + 1 FROM game_saves WHERE id=?) WHERE save_id=?",date,id,id);
        }
        matches.start(id, authorization);
    }

    @Test
    void matchCannotStartBeforeItsScheduledDay() {
        var access = create(1);
        var id = access.room().id();
        var next = repository.nextHumanDate(id).orElseThrow();
        var ticket = matches.view(id,header(access),null).ticket();
        assertThat(ticket).isNotNull();
        assertThat(ticket.date()).isEqualTo(next);
        assertThat(java.util.List.of(ticket.homeId(),ticket.awayId())).contains(access.room().members().getFirst().teamId());
        assertThat(ticket.homeName()).isNotBlank();
        assertThat(ticket.awayName()).isNotBlank();
        assertThat(ticket.homeColor()).matches("#[0-9a-fA-F]{6}");
        assertThat(ticket.awayColor()).matches("#[0-9a-fA-F]{6}");
        db.update("UPDATE save_teams SET calendar_day=(SELECT DATEDIFF('DAY',calendar_start_date,?) FROM game_saves WHERE id=?) WHERE save_id=?",next,id,id);
        assertThatThrownBy(() -> matches.start(id, header(access))).isInstanceOf(DefaultErrorException.class).hasMessageContaining("dia do jogo");
        assertThat(repository.currentDay(id)).isEmpty();
        startOnMatchDay(id, header(access));
        assertThat(own(access)).isNotNull();
    }

    private LeagueAccessResponse create(int capacity) {
        return saves.create(
            new CreateLeagueRequest("Jornada teste", capacity, "Treinador", catalog.teams(null).getFirst().id())
        );
    }

    private String header(LeagueAccessResponse a) {
        return "Bearer " + a.accessToken();
    }

    private void tick(String id) {
        now.updateAndGet(t -> t.plusSeconds(2));
        matches.tick(id);
    }

    private void ready(LeagueAccessResponse a) {
        saves.ready(a.room().id(), header(a), true);
    }

    private MatchdayResponse.MatchView own(LeagueAccessResponse a) {
        return matches.view(a.room().id(), header(a), null).selected();
    }

    @Test
    void readinessStartsAPersistentDayAndFinalizationIsExactlyOnce() {
        var a = create(1);
        var id = a.room().id();
        assertThat(repository.currentDay(id)).isEmpty();
        ready(a);
        startOnMatchDay(id, header(a));
        var first = own(a);
        assertThat(first).isNotNull();
        assertThat(first.status()).isEqualTo("LIVE");
        int size = repository.currentDay(id).size();
        startOnMatchDay(id, header(a));
        assertThat(repository.currentDay(id)).hasSize(size);
        for (int i = 0; i < 160; i++) {
            tick(id);
            acknowledgeCoach(a);
        }
        var end = matches.view(id, header(a), first.fixtureId());
        assertThat(end.selected().status()).isEqualTo("FINISHED");
        assertThat(end.leagueStatus()).isEqualTo("LOBBY");
        assertThat(saves.room(id).members()).allMatch(m -> !m.ready());
        assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM national_fixtures WHERE save_id=? AND season_id=(SELECT season_id FROM national_fixtures WHERE id=?) AND status='PLAYED'",
                Integer.class,
                id,
                first.fixtureId()
            )
        ).isEqualTo(size);
        var appearance = db.queryForObject(
            "SELECT SUM(appearances) FROM match_player_seasons WHERE save_id=?",
            Integer.class,
            id
        );
        int day = saves.room(id).roundNumber();
        tick(id);
        assertThat(saves.room(id).roundNumber()).isEqualTo(day);
        assertThat(
            db.queryForObject("SELECT SUM(appearances) FROM match_player_seasons WHERE save_id=?", Integer.class, id)
        ).isEqualTo(appearance);
        var restored = repository.session(id, first.fixtureId()).orElseThrow().state();
        assertThat(restored.events).containsExactlyElementsOf(end.selected().events());
        assertThat(restored.home.stats.passesCompleted).isEqualTo(end.selected().home().stats.passesCompleted);
        assertThatThrownBy(() -> matches.view(id, "Bearer wrong", first.fixtureId())).isInstanceOf(
            DefaultErrorException.class
        );
    }

    @Test
    void decisionsAgainstAiDoNotPauseAndOtherClubsAndStaleCommandsAreRejected() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var state = own(a);
        var t = state.home().id == a.room().members().getFirst().teamId() ? state.home() : state.away();
        assertThatThrownBy(() ->
            matches.action(
                id,
                state.fixtureId(),
                header(a),
                new MatchActionRequest("PAUSE", null, null, null, null, state.revision())
            )
        )
            .isInstanceOf(DefaultErrorException.class)
            .hasMessageContaining("relógio");
        var out = t.players
            .stream()
            .filter(p -> p.onPitch)
            .findFirst()
            .orElseThrow();
        var in = t.players
            .stream()
            .filter(p -> !p.entered)
            .findFirst()
            .orElseThrow();
        matches.action(
            id,
            state.fixtureId(),
            header(a),
            new MatchActionRequest("SUBSTITUTE", out.id, in.id, null, null, state.revision())
        );
        assertThat(own(a).status()).isEqualTo("LIVE");
        tick(id);
        assertThat(own(a).minute()).isPositive();
        assertThatThrownBy(() ->
            matches.action(
                id,
                state.fixtureId(),
                header(a),
                new MatchActionRequest("TACTICS", null, null, "ATTACKING", "4-3-3", state.revision())
            )
        )
            .isInstanceOf(DefaultErrorException.class)
            .hasMessageContaining("avançou");
        long other = repository
            .currentDay(id)
            .stream()
            .map(r -> r.state())
            .filter(s -> s.fixtureId != state.fixtureId())
            .findFirst()
            .orElseThrow()
            .fixtureId;
        assertThatThrownBy(() ->
            matches.action(
                id,
                other,
                header(a),
                new MatchActionRequest(
                    "PAUSE",
                    null,
                    null,
                    null,
                    null,
                    repository.session(id, other).orElseThrow().state().revision
                )
            )
        )
            .isInstanceOf(DefaultErrorException.class)
            .hasMessageContaining("próprio");
        assertThatThrownBy(() -> saves.ready(id, header(a), false)).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void aHumanDuelPauseIsLocalAndExpiresWithoutAConnectedBrowser() {
        var a = create(2);
        var id = a.room().id();
        long ownTeam = a.room().members().getFirst().teamId();
        var f = db.queryForMap(
            "SELECT id,home_team_id,away_team_id FROM national_fixtures WHERE save_id=? AND (home_team_id=? OR away_team_id=?) ORDER BY match_date,id LIMIT 1",
            id,
            ownTeam,
            ownTeam
        );
        long home = ((Number) f.get("home_team_id")).longValue(),
            away = ((Number) f.get("away_team_id")).longValue();
        var b = saves.join(a.room().inviteCode(), new JoinLeagueRequest("Amigo", home == ownTeam ? away : home));
        ready(a);
        ready(b);
        startOnMatchDay(id, header(a));
        var m = own(a);
        assertThat(m.canPause()).isTrue();
        matches.action(
            id,
            m.fixtureId(),
            header(a),
            new MatchActionRequest("PAUSE", null, null, null, null, m.revision())
        );
        tick(id);
        assertThat(own(a).minute()).isZero();
        assertThat(
            repository
                .currentDay(id)
                .stream()
                .filter(r -> r.state().fixtureId != m.fixtureId())
                .anyMatch(r -> r.state().minute > 0)
        ).isTrue();
        assertThat(matches.view(id, header(b), m.fixtureId()).selected().status()).isEqualTo("PAUSED");
        now.updateAndGet(t -> t.plusSeconds(31));
        matches.tick(id);
        assertThat(own(a).status()).isEqualTo("LIVE");
        assertThat(own(a).minute()).isPositive();
    }

    @Test
    void cardsInjuriesAndSeasonStatisticsSurviveFinalizationAndSuspensionIsServedNextMatch() {
        var a = create(1);
        var id = a.room().id();
        ready(a);
        startOnMatchDay(id, header(a));
        var m = own(a);
        var row = repository.session(id, m.fixtureId()).orElseThrow();
        var state = row.state();
        var team = state.home.id == a.room().members().getFirst().teamId() ? state.home : state.away;
        var booked = team.players
            .stream()
            .filter(p -> p.onPitch)
            .findFirst()
            .orElseThrow();
        booked.yellow = 2;
        booked.sentOff = true;
        booked.onPitch = false;
        var injured = team.players
            .stream()
            .filter(p -> p.onPitch)
            .findFirst()
            .orElseThrow();
        injured.injured = true;
        injured.onPitch = false;
        injured.injuryDays = 14;
        state.period = 2;
        state.minute = 96;
        state.addedSecond = 3;
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        for (long seed = 1; seed < 1000; seed++) {
            state.seed = seed;
            var preview = mapper.readValue(
                mapper.writeValueAsString(state),
                br.com.manager.models.dto.MatchStateDto.class
            );
            int firstEvent = preview.events.size();
            br.com.manager.utils.MatchEngine.step(preview);
            if (
                preview.status.equals("FINISHED") &&
                preview.varReview == null &&
                preview.events
                    .subList(firstEvent, preview.events.size())
                    .stream()
                    .noneMatch(e -> java.util.List.of("GOAL", "RED", "INJURY").contains(e.type()))
            ) break;
            if (seed == 999) throw new AssertionError("No uneventful final minute found");
        }
        repository.save(id, state, now.get());
        for (int grace = 0; grace < 4; grace++) tick(id);
        assertThat(
            db.queryForObject(
                "SELECT suspension_remaining FROM match_player_seasons WHERE save_id=? AND player_id=?",
                Integer.class,
                id,
                booked.id
            )
        ).isEqualTo(1);
        assertThat(
            db.queryForObject(
                "SELECT injury_name FROM save_player_profiles WHERE save_id=? AND player_id=?",
                String.class,
                id,
                injured.id
            )
        ).contains("Lesão");
        var condition = management
            .state(id, header(a))
            .players()
            .stream()
            .filter(p -> p.id() == booked.id)
            .findFirst()
            .orElseThrow()
            .condition();
        assertThat(condition.available()).isFalse();
        assertThat(condition.status()).isEqualTo("SUSPENDED");
        for (int i = 0; i < 160; i++) {
            tick(id);
            acknowledgeCoach(a);
        }
        ready(a);
        startOnMatchDay(id, header(a));
        var next = own(a);
        var nextTeam = next.home().id == team.id ? next.home() : next.away();
        assertThat(nextTeam.players).noneMatch(p -> p.id == booked.id);
        assertThat(
            db.queryForObject(
                "SELECT suspension_remaining FROM match_player_seasons WHERE save_id=? AND player_id=?",
                Integer.class,
                id,
                booked.id
            )
        ).isZero();
    }
}
