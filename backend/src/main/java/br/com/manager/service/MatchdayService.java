package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.*;
import br.com.manager.models.dto.MatchStateDto.*;
import br.com.manager.models.events.*;
import br.com.manager.models.requests.MatchActionRequest;
import br.com.manager.models.responses.MatchdayResponse;
import br.com.manager.models.responses.VarReviewResponse;
import br.com.manager.repository.*;
import br.com.manager.utils.MatchEngine;
import br.com.manager.utils.MatchSpeed;
import br.com.manager.utils.VarRules;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchdayService {

    private final MatchRepository repository;
    private final SaveRepository leagues;
    private final ManagementRepository management;
    private final SaveService saves;
    private final NationalSeasonService seasons;
    private final PlayerProfilesService profiles;
    private final LeagueLifecycleService lifecycle;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final long tickMillis;
    private final boolean narratedPacing;
    private final FinanceService finances;
    private final FinanceDailyService financialDays;
    private final WorldCompetitionService world;
    private final TournamentService tournaments;
    private final DayProgressRepository calendar;
    private final SecureRandom random = new SecureRandom();

    public MatchdayService(
        MatchRepository repository,
        SaveRepository leagues,
        ManagementRepository management,
        SaveService saves,
        NationalSeasonService seasons,
        PlayerProfilesService profiles,
        LeagueLifecycleService lifecycle,
        ApplicationEventPublisher events,
        Clock clock,
        @Value("${app.matchday.tick-millis:6000}") long tickMillis,
        @Value("${app.matchday.narrated-pacing:true}") boolean narratedPacing,
        FinanceService finances,
        FinanceDailyService financialDays,
        WorldCompetitionService world,
        TournamentService tournaments,
        DayProgressRepository calendar
    ) {
        this.repository = repository;
        this.leagues = leagues;
        this.management = management;
        this.saves = saves;
        this.seasons = seasons;
        this.profiles = profiles;
        this.lifecycle = lifecycle;
        this.events = events;
        this.clock = clock;
        this.tickMillis = Math.max(250, tickMillis);
        this.narratedPacing = narratedPacing;
        this.finances = finances;
        this.financialDays = financialDays;
        this.world = world;
        this.tournaments = tournaments;
        this.calendar = calendar;
    }

    private LeagueMemberDto member(String id, String header) {
        saves.summary(id, header);
        return saves.realtimeMember(id, header.substring(7));
    }

    private void lock(String id) {
        if (!leagues.lock(id)) throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Liga não encontrada.");
    }

    private void conflict(String message) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, message);
    }

    @Transactional(readOnly = true)
    public MatchdayResponse view(String id, String header, Long fixture) {
        var member = member(id, header);
        var rows = repository.currentDay(id);
        MatchStateDto selected = null;
        if (fixture != null) selected = repository
            .session(id, fixture)
            .map(MatchSessionDto::state)
            .orElseThrow(() -> new DefaultErrorException(HttpStatus.NOT_FOUND, "Partida não encontrada nesta liga."));
        else if (!rows.isEmpty()) selected = rows
            .stream()
            .map(MatchSessionDto::state)
            .filter(s -> s.home.id == member.teamId() || s.away.id == member.teamId())
            .findFirst()
            .orElse(rows.getFirst().state());
        var summaries = rows
            .stream()
            .map(row -> {
                var s = row.state();
                return new MatchSummaryDto(
                    s.fixtureId,
                    s.home.id,
                    s.home.name,
                    s.away.id,
                    s.away.name,
                    s.home.goals,
                    s.away.goals,
                    s.minute,
                    s.period,
                    s.status,
                    s.home.human && s.away.human,
                    s.revision
                );
            })
            .toList();
        if(selected!=null)br.com.manager.utils.PlayerMatchRatings.refresh(selected);
        var detail =
            selected == null
                ? null
                : new MatchdayResponse.MatchView(
                      selected.fixtureId,
                      selected.revision,
                      selected.minute,
                      selected.period,
                      selected.addedFirst,
                      selected.addedSecond,
                      selected.status,
                      selected.pauseReason,
                      selected.pausedUntil,
                      selected.home.human && selected.away.human,
                      selected.penaltyTeam,
                      selected.home,
                      selected.away,
                      List.copyOf(selected.events),
                      Math.max(0, selected.goalCelebrationUntil - clock.instant().toEpochMilli()),
                      selected.celebratingGoalSequence,
                      selected.awaitingCoachDecision,
                      selected.varReview == null &&
                          selected.status.equals("PAUSED") &&
                          (selected.awaitingCoachDecision
                              ? selected.pendingCoachTeams.contains(member.teamId())
                              : Objects.equals(selected.pausedBy, member.teamId())),
                      List.copyOf(selected.pendingCoachTeams),
                      selected.varReview == null
                          ? null
                          : new VarReviewResponse(
                                selected.varReview.goalSequence,
                                selected.varReview.teamId,
                                selected.varReview.phase,
                                Math.max(0, selected.varReview.phaseUntil - clock.instant().toEpochMilli()),
                                selected.varReview.phase.equals("DECISION") ? selected.varReview.confirmed : null,
                                selected.varReview.phase.equals("DECISION") ? selected.varReview.reason : null
                            ),
                      selected.competitionName,
                      selected.homePenalties,
                      selected.awayPenalties,
                      selected.cupMatch,
                      selected.halfTimeBreak
                  );
        return new MatchdayResponse(
            selected == null ? repository.date(id).toString() : selected.date,
            member.teamId(),
            saves.room(id).status(),
            summaries,
            detail,
            saves
                .room(id)
                .members()
                .stream()
                .map(m -> {
                    var game = rows
                        .stream()
                        .map(MatchSessionDto::state)
                        .filter(s -> s.home.id == m.teamId() || s.away.id == m.teamId())
                        .findFirst();
                    return new MatchdayResponse.Participant(
                        m.name(),
                        m.teamId(),
                        m.teamName(),
                        game.map(s -> s.status.equals("FINISHED") ? "FINISHED" : "PLAYING").orElse("BYE"),
                        game.map(s -> s.fixtureId).orElse(null)
                    );
                })
                .toList(),
            repository.speed(id),
            member.owner() && (saves.room(id).capacity() == 1 || saves.room(id).localMultiplayer()),
            saves.room(id).localMultiplayer(),
            repository.nextTicket(id,member.teamId()).orElse(null)
        );
    }

    @Transactional
    public MatchdayResponse speed(String id, String header, int percent, Long fixture) {
        lock(id);
        var member = member(id, header);
        var room = saves.room(id);
        if (!member.owner() || (room.capacity() > 1 && !room.localMultiplayer())) throw new DefaultErrorException(
            HttpStatus.FORBIDDEN,
            "A velocidade só pode ser alterada pelo criador em ligas solo ou multiplayer local."
        );
        if (!MatchSpeed.valid(percent)) throw new DefaultErrorException(
            HttpStatus.BAD_REQUEST,
            "Escolha 0,5×, 1×, 2× ou 4×."
        );
        if (repository.speed(id) == percent) return view(id, header, fixture);
        repository.speed(id, percent);
        lifecycle.touch(id);
        var now = clock.instant();
        for (var row : repository.currentDay(id)) {
            var state = row.state();
            if (row.finished()) continue;
            state.revision++;
            // Never shorten VAR, celebrations or coach decision windows.
            var next =
                state.status.equals("LIVE") &&
                state.varReview == null &&
                state.goalCelebrationUntil <= now.toEpochMilli()
                    ? now.plusMillis(MatchSpeed.delay(tickMillis, percent))
                    : row.nextTick();
            repository.save(id, state, next);
            events.publishEvent(new MatchUpdatedEvent(id, state.fixtureId, state.revision));
        }
        return view(id, header, fixture);
    }

    @Transactional
    public void start(String id, String header) {
        lock(id);
        var coach = member(id, header);
        if (!saves.room(id).status().equals("LIVE")) {
            var next = calendar.nextMatch(id, coach.teamId());
            if (next == null || !next.equals(calendar.date(id, coach.teamId())))
                conflict("A partida só pode ser iniciada no dia do jogo. Avance pelo calendário.");
            var first = repository.nextHumanDate(id);
            if (first.isPresent() && !next.equals(first.get()))
                conflict("Aguarde os jogos anteriores dos outros treinadores.");
        }
        startLocked(id);
    }

    private void startLocked(String id) {
        int speed = repository.speed(id);
        var room = saves.room(id);
        if (room.status().equals("LIVE")) return;
        boolean network=room.capacity()>1&&!room.localMultiplayer();
        if(room.members().size()!=room.capacity())conflict("Cadastre todos os treinadores antes de iniciar a jornada.");
        if (network&&(!room.status().equals("READY")||room.members().stream().anyMatch(m->!m.ready()))) conflict(
            "Todos os participantes devem confirmar a preparação antes de avançar."
        );
        for(var participant:room.members())profiles.requireAvailableStarters(id,participant.id());
        var current = repository.date(id);
        seasons.ensureForDate(id, current);
        world.synchronize(id);
        var next = repository.nextHumanDate(id);
        if (next.isEmpty()) {
            seasons.ensureForDate(id, current.plusYears(1));
            world.synchronize(id);
            next = repository.nextHumanDate(id);
        }
        if (next.isEmpty()) conflict("Não há partidas agendadas para os participantes.");
        var matchDate = next.orElseThrow();
        var fixtures = repository.humanMatchday(id, matchDate);
        for (var member : room.members()) finances.requireApproved(id, member.teamId());
        for (var fixture : fixtures) {
            financialDays.processThrough(id, fixture.homeId(), fixture.date());
            financialDays.processThrough(id, fixture.awayId(), fixture.date());
        }
        repository.advance(id, 0, "LIVE");
        long batch = repository.nextBatch(id);
        profiles.initialize();
        var human = new HashSet<Long>();
        room.members().forEach(m -> human.add(m.teamId()));
        for (var f : fixtures) {
            var state = new MatchStateDto();
            state.engineVersion = MatchEngine.CURRENT_VERSION;
            state.fixtureId = f.id();
            state.seasonId = f.seasonId();
            state.date = f.date().toString();
            state.competitionName = repository.competitionName(id, f.id());
            tournaments.configure(id, state);
            state.seed = random.nextLong();
            state.addedFirst = 1 + random.nextInt(3);
            state.addedSecond = 3 + random.nextInt(4);
            repository.prepareTeam(id, f.homeId(), f.date());
            repository.prepareTeam(id, f.awayId(), f.date());
            state.home = team(
                id,
                f.homeId(),
                f.homeName(),
                state.disciplineSeasonId > 0 ? state.disciplineSeasonId : f.seasonId(),
                human.contains(f.homeId())
            );
            state.away = team(
                id,
                f.awayId(),
                f.awayName(),
                state.disciplineSeasonId > 0 ? state.disciplineSeasonId : f.seasonId(),
                human.contains(f.awayId())
            );
            repository.serveSuspensions(
                id,
                state.disciplineSeasonId > 0 ? state.disciplineSeasonId : f.seasonId(),
                state.home.id
            );
            repository.serveSuspensions(
                id,
                state.disciplineSeasonId > 0 ? state.disciplineSeasonId : f.seasonId(),
                state.away.id
            );
            MatchEngine.event(
                state,
                "KICK_OFF",
                state.home,
                null,
                null,
                "Bola rolando! " + state.home.name + " × " + state.away.name + "."
            );
            repository.create(id, state, clock.instant().plusMillis(MatchSpeed.delay(tickMillis, speed)), batch);
        }
        for (var m : room.members())
            if (fixtures.stream().noneMatch(f -> f.homeId() == m.teamId() || f.awayId() == m.teamId())) {
                repository.finishIdleTeam(id, m.teamId(), matchDate);
            }
        lifecycle.touch(id);
        events.publishEvent(new SaveChangedEvent(id));
        events.publishEvent(new MatchUpdatedEvent(id, 0, 0));
    }

    private Team team(String id, long teamId, String name, long season, boolean human) {
        var t = new Team();
        t.id = teamId;
        t.name = name;
        t.human = human;
        var settings = management.settings(id, teamId).orElse(new TeamSettingsDto("4-3-3", "BALANCED", 0));
        t.formation = settings.formation();
        t.mentality = settings.mentality();
        t.tacticalPlan = settings.tacticalPlan();
        var suspended = repository.suspended(id, season);
        var available = management
            .players(id, teamId)
            .stream()
            .filter(p -> p.condition().available() && !suspended.contains(p.id()))
            .toList();
        var lineup = new ArrayList<>(management.lineup(id, teamId));
        if (
            lineup.isEmpty() || lineup.stream().anyMatch(pid -> available.stream().noneMatch(p -> p.id().equals(pid)))
        ) {
            lineup.clear();
            for (var role : br.com.manager.utils.FormationRules.ROLES.getOrDefault(
                t.formation,
                br.com.manager.utils.FormationRules.ROLES.get("4-3-3")
            )) {
                var candidate = available
                    .stream()
                    .filter(p -> !lineup.contains(p.id()))
                    .sorted(
                        Comparator.comparingDouble(
                            (br.com.manager.models.dto.ManagedPlayerDto p) ->
                                p.condition().effectiveAbility() *
                                br.com.manager.utils.PlayerPositionRules.fit(p.position(), p.positions(), role)
                        ).reversed()
                    )
                    .findFirst()
                    .or(() ->
                        available
                            .stream()
                            .filter(p -> !lineup.contains(p.id()))
                            .findFirst()
                    );
                candidate.ifPresent(p -> lineup.add(p.id()));
            }
        }
        if (lineup.size() < 7 && human) conflict(
            "O clube " + name + " precisa de pelo menos sete atletas disponíveis."
        );
        // Eleven starters plus a maximum twelve-player bench, selected by position and condition.
        var selected = new ArrayList<>(
            lineup
                .stream()
                .map(pid ->
                    available
                        .stream()
                        .filter(p -> p.id().equals(pid))
                        .findFirst()
                        .orElseThrow()
                )
                .toList()
        );
        var reserveKeeper = available
            .stream()
            .filter(p -> !lineup.contains(p.id()) && p.position().equals("GOL"))
            .findFirst();
        reserveKeeper.ifPresent(selected::add);
        for (var p : available)
            if (!lineup.contains(p.id()) && !selected.contains(p) && selected.size() < 23) selected.add(p);
        for (var p : selected) {
            var mp = new Player();
            mp.id = p.id();
            mp.name = p.name();
            mp.number = p.number();
            mp.position = p.position();
            mp.naturalPosition = p.position();
            mp.preferredPositions = p.positions();
            mp.ability = p.ability();
            mp.fitness = p.fitness();
            mp.morale = p.condition().morale();
            mp.sharpness = p.condition().sharpness();
            mp.attributes = new LinkedHashMap<>(p.technical().attributes());
            mp.onPitch = lineup.contains(p.id());
            mp.slot = lineup.indexOf(p.id());
            mp.entered = mp.onPitch;
            if (mp.onPitch) br.com.manager.utils.PlayerPositionRules.assign(
                mp,
                br.com.manager.utils.FormationRules.ROLES.get(t.formation).get(lineup.indexOf(p.id()))
            );
            t.players.add(mp);
        }
        br.com.manager.utils.PlayerPositionRules.applyPlan(t);
        return t;
    }

    public List<String> candidates() {
        return repository.candidates();
    }

    @Transactional
    public void tick(String id) {
        if (!leagues.lock(id)) return;
        var status = saves.room(id).status();
        // Readiness never starts a game. Only the explicit matchday/start command does.
        if (status.equals("READY")) return;
        if (!status.equals("LIVE")) return;
        var now = clock.instant();
        var rows = repository.currentDay(id);
        int speed = repository.speed(id);
        boolean allFinished = true;
        for (var row : rows) {
            var s = row.state();
            if (row.finished()) continue;
            if (row.nextTick().isAfter(now)) {
                allFinished = false;
                continue;
            }
            if (s.varReview != null) {
                if (s.varReview.phase.equals("REVIEW")) {
                    VarRules.decide(s);
                    s.varReview.phaseUntil = now.plusSeconds(VarRules.DECISION_SECONDS).toEpochMilli();
                    s.revision++;
                    repository.save(id, s, Instant.ofEpochMilli(s.varReview.phaseUntil));
                    allFinished = false;
                } else {
                    boolean confirmed = s.varReview.confirmed;
                    int sequence = s.varReview.goalSequence;
                    s.varReview = null;
                    s.revision++;
                    if (confirmed) {
                        s.goalCelebrationUntil = now.plusSeconds(5).toEpochMilli();
                        s.celebratingGoalSequence = sequence;
                    } else {
                        s.goalCelebrationUntil = 0;
                        s.celebratingGoalSequence = 0;
                    }
                    if (!confirmed && s.finishAfterCelebration) {
                        s.finishAfterCelebration = false;
                        s.status = "FINISHED";
                        finishMatch(id, s);
                    }
                    if (!s.status.equals("FINISHED")) allFinished = false;
                    repository.save(
                        id,
                        s,
                        confirmed
                            ? Instant.ofEpochMilli(s.goalCelebrationUntil)
                            : now.plusMillis(MatchSpeed.delay(tickMillis, speed))
                    );
                }
                events.publishEvent(new MatchUpdatedEvent(id, s.fixtureId, s.revision));
                continue;
            }
            if (s.status.equals("PAUSED")) {
                if (s.awaitingCoachDecision || s.pausedUntil > now.toEpochMilli()) {
                    allFinished = false;
                    continue;
                }
                s.status = "LIVE";
                s.pausedBy = null;
                s.pauseReason = null;
                s.pausedUntil = 0;
                MatchEngine.event(s, "RESUMED", s.home, null, null, "Partida retomada automaticamente.");
            }
            int before = s.events.size();
            if (s.finishAfterCelebration) {
                s.status = "FINISHED";
                s.finishAfterCelebration = false;
                s.revision++;
            } else MatchEngine.step(s);
            if (s.varReview != null) {
                s.varReview.phaseUntil = now.plusSeconds(VarRules.REVIEW_SECONDS).toEpochMilli();
                if (s.status.equals("FINISHED")) {
                    s.finishAfterCelebration = true;
                    s.status = "LIVE";
                }
            }
            var goal = s.events
                .subList(before, s.events.size())
                .stream()
                .filter(e -> e.type().equals("GOAL"))
                .findFirst();
            if (goal.isPresent()) {
                s.goalCelebrationUntil = now.plusSeconds(5).toEpochMilli();
                s.celebratingGoalSequence = goal.get().sequence();
                if (s.status.equals("FINISHED")) {
                    s.finishAfterCelebration = true;
                    s.status = "LIVE";
                }
            }
            if (s.status.equals("LIVE")) {
                var mandatory = s.events
                    .subList(before, s.events.size())
                    .stream()
                    .filter(
                        e ->
                            e.type().equals("HALF_TIME") ||
                            ((e.type().equals("INJURY") || e.type().equals("RED")) &&
                                (e.teamId() == s.home.id ? s.home.human : s.away.human))
                    )
                    .toList();
                if (!mandatory.isEmpty()) {
                    var teams = new LinkedHashSet<Long>();
                    for (var e : mandatory) {
                        if (e.type().equals("HALF_TIME")) {
                            if (s.home.human) teams.add(s.home.id);
                            if (s.away.human) teams.add(s.away.id);
                        } else teams.add(e.teamId());
                    }
                    if (!teams.isEmpty()) {
                        s.status = "PAUSED";
                        s.awaitingCoachDecision = true;
                        s.pendingCoachTeams = new ArrayList<>(teams);
                        s.pausedBy = null;
                        s.pausedUntil = 0;
                        s.pauseReason = mandatory.stream().anyMatch(e -> e.type().equals("HALF_TIME"))
                            ? "Intervalo: aguardando os treinadores humanos."
                            : "Lesão ou expulsão: aguardando a decisão do treinador.";
                        MatchEngine.event(s, "PAUSED", s.home, null, null, s.pauseReason);
                    }
                }
            }
            if (s.home.human && s.away.human && s.status.equals("LIVE")) {
                var decision = s.events
                    .subList(before, s.events.size())
                    .stream()
                    .filter(e -> e.type().equals("PENALTY_AWARDED"))
                    .findFirst();
                if (decision.isPresent()) pause(
                    s,
                    decision.get().teamId(),
                    "Decisão: " + decision.get().text(),
                    now,
                    20
                );
            }
            if (s.status.equals("FINISHED")) {
                finishMatch(id, s);
            } else allFinished = false;
            repository.save(
                id,
                s,
                s.varReview != null
                    ? Instant.ofEpochMilli(s.varReview.phaseUntil)
                    : goal.isPresent()
                      ? Instant.ofEpochMilli(s.goalCelebrationUntil)
                      : now.plusMillis(
                            MatchSpeed.delay(
                                narratedPacing
                                    ? br.com.manager.utils.MatchNarration.readingDelay(
                                          tickMillis,
                                          s.events.subList(before, s.events.size())
                                      )
                                    : tickMillis,
                                speed
                            )
                        )
            );
            events.publishEvent(new MatchUpdatedEvent(id, s.fixtureId, s.revision));
        }
        if (allFinished && !rows.isEmpty()) {
            repository.closeBatch(id);
            world.synchronize(id);
            repository.resetReady(id);
            seasons.ensureForDate(id, repository.date(id));
            lifecycle.touch(id);
            events.publishEvent(new SaveChangedEvent(id));
        }
    }

    private void finishMatch(String id, MatchStateDto s) {
        int day = repository.dayNumber(id, LocalDate.parse(s.date));
        repository.finish(id, s, day);
        tournaments.completed(id, s);
        repository.finishTeam(id, s.home.id, day);
        repository.finishTeam(id, s.away.id, day);
        finances.matchBonuses(id, s);
    }

    private void pause(MatchStateDto s, long team, String reason, Instant now, int seconds) {
        s.status = "PAUSED";
        s.pausedBy = team;
        s.pauseReason = reason;
        s.pausedUntil = now.plusSeconds(seconds).toEpochMilli();
        MatchEngine.event(s, "PAUSED", s.home.id == team ? s.home : s.away, null, null, reason);
    }

    @Transactional
    public MatchdayResponse action(String id, long fixture, String header, MatchActionRequest input) {
        lock(id);
        var member = member(id, header);
        var row = repository
            .session(id, fixture)
            .orElseThrow(() -> new DefaultErrorException(HttpStatus.NOT_FOUND, "Partida não encontrada."));
        var s = row.state();
        if (s.varReview != null) conflict("Aguarde a conclusão da revisão do VAR.");
        if (s.status.equals("FINISHED")) conflict("Esta partida já terminou.");
        var team = s.home.id == member.teamId() ? s.home : s.away.id == member.teamId() ? s.away : null;
        if (team == null) throw new DefaultErrorException(
            HttpStatus.FORBIDDEN,
            "Você só pode comandar seu próprio time."
        );
        if (!input.type().equals("OPEN_TACTICS") && input.revision() != s.revision) conflict(
            "A partida avançou. Confira o estado atualizado e tente novamente."
        );
        if (input.type().equals("OPEN_TACTICS") && s.goalCelebrationUntil > clock.instant().toEpochMilli()) conflict(
            "Aguarde a comemoração do gol para abrir as táticas."
        );
        if (
            s.awaitingCoachDecision &&
            !s.pendingCoachTeams.contains(team.id) &&
            Set.of("SUBSTITUTE", "TACTICS", "PENALTY_TAKER").contains(input.type())
        ) conflict("Abra Táticas e formação para revisar os ajustes já confirmados.");
        br.com.manager.utils.PlayerMatchRatings.refresh(s);
        switch (input.type()) {
            case "TALK_PRAISE", "TALK_ENCOURAGE", "TALK_CRITICIZE" -> br.com.manager.utils.HalfTimeTalkRules.talk(s,team,input.playerIn(),input.type());
            case "SWAP_POSITIONS" -> {
                if(!s.status.equals("PAUSED")||!s.awaitingCoachDecision||!s.pendingCoachTeams.contains(team.id)) conflict("Abra as táticas antes de trocar as posições.");
                var a=MatchEngine.active(team).stream().filter(p->input.playerOut()!=null&&p.id==input.playerOut()).findFirst().orElse(null);
                var b=MatchEngine.active(team).stream().filter(p->input.playerIn()!=null&&p.id==input.playerIn()).findFirst().orElse(null);
                if(a==null||b==null||a.id==b.id)conflict("Escolha dois atletas disponíveis em campo.");
                int slot=a.slot;a.slot=b.slot;b.slot=slot;
                String role=a.assignedPosition==null?a.position:a.assignedPosition;
                br.com.manager.utils.PlayerPositionRules.assign(a,b.assignedPosition==null?b.position:b.assignedPosition);
                br.com.manager.utils.PlayerPositionRules.assign(b,role);
                var pa=team.tacticalPlan.positions.remove(a.id);var pb=team.tacticalPlan.positions.remove(b.id);
                if(pa!=null)team.tacticalPlan.positions.put(b.id,pa);if(pb!=null)team.tacticalPlan.positions.put(a.id,pb);
                MatchEngine.event(s,"TACTICS",team,a,b,a.name+" trocou de posição com "+b.name+".");
            }
            case "ENCOURAGE", "DEMAND", "CALM", "COMPLAIN" -> {
                br.com.manager.utils.TouchlineRules.command(s, team, input.type(), clock.instant().toEpochMilli());
                s.engineVersion = MatchEngine.CURRENT_VERSION;
            }
            case "OPEN_TACTICS" -> {
                // Idempotent acquisition: a browser with a stale minute can safely request the pause.
                if (
                    s.status.equals("PAUSED") && s.awaitingCoachDecision && s.pendingCoachTeams.contains(team.id)
                ) return view(id, header, fixture);
                if (!s.awaitingCoachDecision) {
                    s.pendingCoachTeams = new ArrayList<>();
                    if (s.home.human) s.pendingCoachTeams.add(s.home.id);
                    if (s.away.human) s.pendingCoachTeams.add(s.away.id);
                    s.pauseReason = "Ajustes táticos: aguardando os treinadores humanos.";
                } else if (!s.pendingCoachTeams.contains(team.id)) s.pendingCoachTeams.add(team.id);
                s.status = "PAUSED";
                s.awaitingCoachDecision = true;
                s.pausedBy = null;
                s.pausedUntil = 0;
                MatchEngine.event(
                    s,
                    "PAUSED",
                    team,
                    null,
                    null,
                    team.name + " abriu Táticas e formação. O relógio aguarda a conclusão dos ajustes."
                );
            }
            case "SUBSTITUTE" -> {
                if (
                    input.playerOut() == null ||
                    input.playerIn() == null ||
                    !MatchEngine.substitute(s, team, input.playerOut(), input.playerIn())
                ) conflict(
                    "Substituição indisponível. São permitidas cinco trocas e três janelas no segundo tempo; não há reentrada nem troca de expulso."
                );
            }
            case "TACTICS" -> {
                if (
                    input.mentality() == null ||
                    input.formation() == null ||
                    !ManagementService.FORMATIONS.containsKey(input.formation()) ||
                    !Set.of("BALANCED", "ATTACKING", "DEFENSIVE").contains(input.mentality())
                ) conflict("Tática inválida.");
                var activePlayers = MatchEngine.active(team);
                if (activePlayers.stream().allMatch(p -> p.slot < 0)) activePlayers = activePlayers
                    .stream()
                    .sorted(
                        Comparator.comparingInt(p ->
                            p.position.equals("GOL")
                                ? 0
                                : p.position.equals("DEF")
                                  ? 1
                                  : p.position.equals("MEI")
                                    ? 2
                                    : 3
                        )
                    )
                    .toList();
                var detailedRoles = br.com.manager.utils.FormationRules.ROLES.get(input.formation());
                for (var player : activePlayers) {
                    int slot = player.slot >= 0 ? player.slot : activePlayers.indexOf(player);
                    player.slot = slot;
                    if (slot < 11) br.com.manager.utils.PlayerPositionRules.assign(player, detailedRoles.get(slot));
                }
                if (input.tacticalPlan() != null) {
                    if (
                        !s.status.equals("PAUSED") || !s.awaitingCoachDecision || !s.pendingCoachTeams.contains(team.id)
                    ) conflict("Abra Táticas e formação para ajustar o posicionamento com a partida pausada.");
                    var starters = new LinkedHashMap<Long, String>();
                    MatchEngine.active(team).forEach(p -> starters.put(p.id, p.position));
                    team.tacticalPlan = br.com.manager.utils.TacticalPlanRules.validate(input.tacticalPlan(), starters);
                    s.engineVersion = MatchEngine.CURRENT_VERSION;
                }
                team.mentality = input.mentality();
                team.formation = input.formation();
                br.com.manager.utils.PlayerPositionRules.applyPlan(team);
                MatchEngine.event(
                    s,
                    "TACTICS",
                    team,
                    null,
                    null,
                    team.name + " ajustou a formação para " + team.formation + "."
                );
            }
            case "SET_KEEPER" -> {
                if (
                    !s.status.equals("PAUSED") || !s.awaitingCoachDecision || !s.pendingCoachTeams.contains(team.id)
                ) conflict("Abra Táticas e formação antes de trocar o goleiro.");
                var candidate = MatchEngine.active(team)
                    .stream()
                    .filter(p -> input.playerIn() != null && p.id == input.playerIn())
                    .findFirst()
                    .orElse(null);
                if (candidate == null) conflict("Escolha um atleta disponível em campo.");
                var previous = MatchEngine.active(team)
                    .stream()
                    .filter(p -> p.position.equals("GOL") && p.id != candidate.id)
                    .findFirst()
                    .orElse(null);
                String oldRole = candidate.assignedPosition == null ? "ZAG" : candidate.assignedPosition;
                int oldSlot = candidate.slot;
                var oldPoint = team.tacticalPlan.positions.remove(candidate.id);
                if (previous != null) {
                    br.com.manager.utils.PlayerPositionRules.assign(previous, oldRole);
                    previous.slot = oldSlot;
                    if (oldPoint != null) team.tacticalPlan.positions.put(previous.id, oldPoint);
                    else team.tacticalPlan.positions.remove(previous.id);
                }
                br.com.manager.utils.PlayerPositionRules.assign(candidate, "GOL");
                candidate.slot = 0;
                team.tacticalPlan.positions.put(
                    candidate.id,
                    new br.com.manager.models.dto.PitchPositionDto(50.0, 89.0)
                );
                MatchEngine.event(
                    s,
                    "TACTICS",
                    team,
                    candidate,
                    null,
                    candidate.name + " assume a posição de goleiro."
                );
            }
            case "PENALTY_TAKER" -> {
                if (
                    input.playerIn() == null ||
                    MatchEngine.active(team)
                        .stream()
                        .noneMatch(p -> p.id == input.playerIn())
                ) conflict("Escolha um cobrador em campo.");
                team.penaltyTaker = input.playerIn();
            }
            case "PAUSE" -> {
                if (!s.home.human || !s.away.human) conflict(
                    "Contra a IA o relógio continua correndo durante suas decisões."
                );
                if (!s.status.equals("LIVE") || team.pauseCount >= 2) conflict(
                    "São permitidas duas pausas de até 30 segundos por treinador."
                );
                team.pauseCount++;
                pause(s, team.id, "Pausa solicitada por " + team.name, clock.instant(), 30);
            }
            case "RESUME" -> {
                if (!s.status.equals("PAUSED")) conflict("Esta partida não está pausada.");
                if (s.awaitingCoachDecision) {
                    if (!s.pendingCoachTeams.remove(team.id)) conflict("Aguardando a decisão do outro treinador.");
                    MatchEngine.event(s, "COACH_READY", team, null, null, team.name + " confirmou os ajustes.");
                    if (!s.pendingCoachTeams.isEmpty()) break;
                    s.awaitingCoachDecision = false;
                } else if (!Objects.equals(s.pausedBy, team.id)) conflict(
                    "Somente o treinador que está decidindo pode retomar esta pausa."
                );
                s.status = "LIVE";
                s.pausedBy = null;
                s.pauseReason = null;
                s.pausedUntil = 0;
                MatchEngine.event(s, "RESUMED", team, null, null, "Partida retomada.");
            }
            default -> throw new DefaultErrorException(HttpStatus.BAD_REQUEST, "Comando de partida inválido.");
        }
        br.com.manager.utils.PlayerMatchRatings.refresh(s);
        s.revision++;
        repository.save(id, s, row.nextTick());
        lifecycle.touch(id);
        events.publishEvent(new MatchUpdatedEvent(id, fixture, s.revision));
        return view(id, header, fixture);
    }
}
