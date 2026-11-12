package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.ClubEventDtos.*;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.events.*;
import br.com.manager.models.requests.ClubEventRequest;
import br.com.manager.repository.*;
import br.com.manager.utils.ClubEventRules;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClubEventService {

    private final ClubEventRepository repository;
    private final SaveService saves;
    private final SaveRepository leagues;
    private final FinanceService finances;
    private final FinanceRepository money;
    private final DayProgressRepository days;
    private final LeagueLifecycleService lifecycle;
    private final ApplicationEventPublisher events;
    private final JuniorPlayersService juniors;
    private final WorldCompetitionService world;
    private final NationalSeasonService seasons;

    public ClubEventService(
        ClubEventRepository repository,
        SaveService saves,
        SaveRepository leagues,
        FinanceService finances,
        FinanceRepository money,
        DayProgressRepository days,
        LeagueLifecycleService lifecycle,
        ApplicationEventPublisher events,
        JuniorPlayersService juniors,
        WorldCompetitionService world,
        NationalSeasonService seasons
    ) {
        this.repository = repository;
        this.saves = saves;
        this.leagues = leagues;
        this.finances = finances;
        this.money = money;
        this.days = days;
        this.lifecycle = lifecycle;
        this.events = events;
        this.juniors = juniors;
        this.world = world;
        this.seasons = seasons;
    }

    private long team(String id, String token) {
        saves.summary(id, token);
        var team = saves.realtimeMember(id, token.substring(7)).teamId();
        if (team == null) fail("Assuma um clube para organizar eventos.");
        return team;
    }

    private long edit(String id, String token) {
        finances.lock(id);
        long team = team(id, token);
        var room = saves.room(id);
        if (
            room.status().equals("LIVE") ||
            (room.capacity() > 1 &&
                !room.localMultiplayer() &&
                room
                    .members()
                    .stream()
                    .anyMatch(m -> m.teamId() != null && m.teamId() == team && m.ready()))
        ) fail("Aguarde o fim das partidas e retire sua prontidão para organizar eventos.");
        finances.requireApproved(id, team);
        return team;
    }

    @Transactional(readOnly = true)
    public Options options(String id, String token) {
        long team = team(id, token);
        var date = days.date(id, team);
        var foundation = repository.foundation(id, team);
        return new Options(
            team,
            date,
            foundation,
            foundation == null ? null : ClubEventRules.anniversary(foundation, date),
            repository.seasonEnd(id, team, date),
            money.cash(id, team),
            repository.fanTrust(id, team),
            repository.squadTrust(id, team),
            ClubEventRules.KINDS,
            ClubEventRules.VENUES,
            repository.opponents(id, team),
            repository.list(id, team)
        );
    }

    @Transactional(readOnly = true)
    public Quote quote(String id, String token, ClubEventRequest input) {
        long team = team(id, token);
        return validated(id, team, input);
    }

    private Quote validated(String id, long team, ClubEventRequest input) {
        var now = days.date(id, team);
        var kind = ClubEventRules.kind(input.type());
        var date = input.date();
        var foundation = repository.foundation(id, team);
        if (input.type().equals("ANNIVERSARY")) {
            if (foundation == null) foundation = input.foundationDate();
            if (foundation == null || foundation.getYear() < 1800 || foundation.isAfter(now)) fail(
                "Cadastre uma data de fundação válida para celebrar o aniversário do clube."
            );
            date = ClubEventRules.anniversary(foundation, now);
        }
        if (date.isBefore(now) || date.isAfter(now.plusYears(1))) fail(
            "Escolha uma data entre hoje no jogo e os próximos doze meses."
        );
        if (!input.type().equals("CAMP") && input.days() != 1) fail("Somente concentrações podem ocupar vários dias.");
        var end = date.plusDays(input.days() - 1L);
        if (end.isAfter(now.plusYears(1))) fail("O evento termina fora do período permitido.");
        if (repository.bookingClash(id, team, date, end, 0)) fail("Já há um evento reservado nesse período.");
        boolean birthday = input.type().equals("ANNIVERSARY");
        if (
            !birthday &&
            repository.fixtureClash(
                id,
                team,
                date.minusDays(input.type().equals("FRIENDLY") ? 2 : 0),
                end.plusDays(input.type().equals("FRIENDLY") ? 2 : 0)
            )
        ) fail("O período conflita com uma partida. Escolha outra data.");
        if (
            kind.annual() &&
            repository.limited(id, team, input.type(), date.withDayOfYear(1), LocalDate.of(date.getYear(), 12, 31))
        ) fail("Esse evento já foi organizado neste ano.");
        if (
            kind.cooldownDays() > 0 &&
            repository.limited(
                id,
                team,
                input.type(),
                date.minusDays(kind.cooldownDays() - 1L),
                end.plusDays(kind.cooldownDays() - 1L)
            )
        ) fail("Aguarde o intervalo mínimo entre eventos desse tipo.");
        int opponentRating = 0;
        if (input.type().equals("FRIENDLY")) {
            var opponent = repository
                .opponents(id, team)
                .stream()
                .filter(o -> Objects.equals(o.id(), input.opponentId()))
                .findFirst()
                .orElseThrow(() ->
                    new DefaultErrorException(HttpStatus.BAD_REQUEST, "Selecione um clube adversário válido.")
                );
            opponentRating = opponent.rating();
            if (repository.roster(id, team).size() < 11 || repository.roster(id, opponent.id()).size() < 11) fail(
                "Os dois clubes precisam ter ao menos onze atletas cadastrados para marcar um amistoso."
            );
            if (date.isBefore(days.date(id, opponent.id()))) fail("O adversário já avançou além dessa data.");
            if (
                repository.fixtureClash(id, opponent.id(), date.minusDays(2), date.plusDays(2)) ||
                repository.bookingClash(id, opponent.id(), date, end, 0)
            ) fail("O adversário tem outro compromisso próximo dessa data.");
        } else if (input.opponentId() != null) fail("Esse evento não admite adversário.");
        if (input.type().equals("CAMP")) {
            if (ClubEventRules.VENUES.stream().noneMatch(v -> v.key().equals(input.venue()))) fail(
                "Selecione um local de concentração válido."
            );
        } else if (input.venue() != null && !input.venue().isBlank()) fail(
            "O local de concentração só é usado nesse tipo de evento."
        );
        if (input.type().equals("SEASON_PARTY") || input.type().equals("PLAYER_AWARDS")) {
            var endOfSeason = repository.seasonEnd(id, team, now);
            if (endOfSeason == null || !date.isAfter(endOfSeason)) fail(
                "Organize essa celebração após o encerramento da temporada nacional."
            );
            var last = repository.lastCompetitiveMatch(id, team, now, endOfSeason.plusDays(21));
            if (last != null && !date.isAfter(last)) fail(
                "Ainda há compromissos competitivos previstos antes dessa celebração."
            );
        }
        long cost = ClubEventRules.cost(
            input.type(),
            repository.rating(id, team),
            opponentRating,
            input.days(),
            input.venue()
        );
        return new Quote(date, end, cost, kind.description());
    }

    @Transactional
    public Booking create(String id, String token, ClubEventRequest input) {
        long team = edit(id, token);
        var quote = validated(id, team, input);
        if (input.type().equals("FRIENDLY")) {
            seasons.ensureWorldForDate(id, quote.endDate().plusDays(2));
            world.synchronize(id);
            quote = validated(id, team, input);
        }
        var plan = finances.ensurePlan(id, team);
        if (money.cash(id, team) - quote.cost() < plan.reserveBudget() + money.reservedFees(id, team, null)) fail(
            "O caixa disponível, descontadas a reserva e as propostas pendentes, não cobre o evento."
        );
        if (input.type().equals("YOUTH_DAY")) juniors.players(id, token);
        boolean invitation = input.type().equals("FRIENDLY") && money.humanTeam(id, input.opponentId());
        long event = repository.create(
            id,
            team,
            input.opponentId(),
            input.type(),
            quote.date(),
            quote.endDate(),
            input.venue(),
            quote.cost(),
            invitation ? "PROPOSED" : "SCHEDULED"
        );
        money.ledger(
            id,
            team,
            days.date(id, team),
            "CLUB_EVENT",
            "Organização: " + ClubEventRules.kind(input.type()).name(),
            -quote.cost(),
            "EVENT_COST:" + event
        );
        if (input.type().equals("ANNIVERSARY") && repository.foundation(id, team) == null) repository.foundation(
            id,
            team,
            input.foundationDate()
        );
        var booking = repository.find(id, event);
        repository.calendar(id, booking, team);
        if (input.opponentId() != null) repository.calendar(id, booking, input.opponentId());
        if (input.type().equals("FRIENDLY") && !invitation) repository.friendly(id, booking);
        changed(id, team);
        return repository.find(id, event);
    }

    @Transactional
    public Booking accept(String id, String token, long event) {
        long team = edit(id, token);
        var booking = authorized(id, event, team);
        if (!booking.status().equals("PROPOSED") || !Objects.equals(booking.opponentId(), team)) fail(
            "Somente o treinador convidado pode aceitar este amistoso."
        );
        if (
            booking.date().isBefore(days.date(id, team)) || booking.date().isBefore(days.date(id, booking.teamId()))
        ) fail("A data do convite já passou para um dos clubes.");
        for (long club : List.of(team, booking.teamId()))
            if (
                repository.fixtureClash(id, club, booking.date().minusDays(2), booking.date().plusDays(2)) ||
                repository.bookingClash(id, club, booking.date(), booking.endDate(), event)
            ) fail("O calendário mudou. O amistoso conflita com outro compromisso.");
        repository.friendly(id, booking);
        repository.update(
            id,
            event,
            "SCHEDULED",
            0,
            "Amistoso confirmado. Renda dividida igualmente entre os dois clubes.",
            0
        );
        changed(id, team);
        changed(id, booking.teamId());
        return repository.find(id, event);
    }

    @Transactional
    public Booking cancel(String id, String token, long event) {
        long team = edit(id, token);
        var booking = authorized(id, event, team);
        if (!booking.status().equals("PROPOSED") && !booking.status().equals("SCHEDULED")) fail(
            "Esse evento não pode mais ser cancelado."
        );
        if (booking.fixtureId() != null && repository.kickedOff(booking.fixtureId())) fail("O amistoso já começou.");
        cancelBooking(id, booking, "Evento cancelado pelo treinador. Valor não utilizado devolvido ao organizador.");
        changed(id, team);
        if (booking.opponentId() != null) changed(id, booking.opponentId());
        return repository.find(id, event);
    }

    private Booking authorized(String id, long event, long team) {
        var booking = repository.find(id, event);
        if (
            booking == null || (booking.teamId() != team && !Objects.equals(booking.opponentId(), team))
        ) throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Evento não encontrado para este clube.");
        return booking;
    }

    private void cancelBooking(String id, Booking event, String reason) {
        if (event.fixtureId() != null) repository.cancelFixture(id, event.fixtureId());
        long total = java.time.temporal.ChronoUnit.DAYS.between(event.date(), event.endDate()) + 1;
        long refund = (event.cost() * (total - event.processedDays())) / total;
        money.ledger(
            id,
            event.teamId(),
            days.date(id, event.teamId()),
            "CLUB_EVENT_REFUND",
            reason,
            refund,
            "EVENT_REFUND:" + event.id()
        );
        repository.update(id, event.id(), "CANCELLED", event.processedDays(), reason, 0);
    }

    public boolean processDay(String id, long team, LocalDate date) {
        boolean completed = false;
        for (var booking : repository.due(id, team, date)) {
            if (booking.status().equals("PROPOSED")) {
                if (!date.isBefore(booking.date())) {
                    cancelBooking(id, booking, "Convite expirado sem confirmação do adversário. Taxa devolvida.");
                    completed = true;
                }
                continue;
            }
            if (booking.type().equals("FRIENDLY")) continue;
            if (booking.teamId() != team) continue;
            int duration = (int) java.time.temporal.ChronoUnit.DAYS.between(booking.date(), booking.endDate()) + 1;
            if (booking.type().equals("CAMP")) {
                int expected = (int) Math.min(
                    duration,
                    java.time.temporal.ChronoUnit.DAYS.between(booking.date(), date) + 1
                );
                if (expected <= booking.processedDays()) continue;
                if (repository.fixtureClash(id, team, date, date)) {
                    cancelBooking(
                        id,
                        booking,
                        "Concentração interrompida por compromisso de jogo. Diárias não utilizadas devolvidas."
                    );
                    completed = true;
                    continue;
                }
                var venue = ClubEventRules.venue(booking.venue());
                for (int d = booking.processedDays(); d < expected; d++) {
                    repository.effects(id, team, -venue.fatigue(), 1, 0, 0);
                    for (int i = 0; i < venue.focus().size(); i++) repository.train(
                        id,
                        team,
                        venue.focus().get(i),
                        i == 0 ? 25 : 15
                    );
                }
                repository.update(
                    id,
                    booking.id(),
                    expected == duration ? "COMPLETED" : "SCHEDULED",
                    expected,
                    "Treinos em " +
                        venue.name() +
                        ". Evolução acumulada nos fundamentos trabalhados; a condição física foi consumida.",
                    0
                );
                completed |= expected == duration;
            } else {
                if (repository.fixtureClash(id, team, date, date) && !booking.type().equals("ANNIVERSARY")) {
                    cancelBooking(
                        id,
                        booking,
                        "Evento cancelado por conflito com o calendário competitivo. Valor devolvido."
                    );
                    completed = true;
                    continue;
                }
                switch (booking.type()) {
                    case "CHARITY" -> repository.effects(id, team, -1, 2, 5, 1);
                    case "MARKETING" -> repository.effects(id, team, -2, 1, 6, 1);
                    case "YOUTH_DAY" -> {
                        repository.effects(id, team, -2, 3, 2, 3);
                        repository.youth(id, team);
                    }
                    case "ANNIVERSARY" -> repository.effects(id, team, 0, 4, 8, 3);
                    case "TEAM_PARTY" -> repository.effects(id, team, -4, 5, 0, 5);
                    case "SEASON_PARTY" -> repository.effects(id, team, -3, 7, 2, 7);
                    case "PLAYER_AWARDS" -> repository.effects(id, team, -1, 6, 1, 6);
                    default -> throw new IllegalStateException("Evento sem regra de processamento");
                }
                repository.update(
                    id,
                    booking.id(),
                    "COMPLETED",
                    1,
                    ClubEventRules.kind(booking.type()).description() +
                        " Evento realizado; relações e moral atualizadas." +
                        (booking.type().equals("PLAYER_AWARDS") ? " " + repository.awards(id, team, date) : ""),
                    0
                );
                completed = true;
            }
        }
        return completed;
    }

    public void configure(String id, MatchStateDto state) {
        var event = repository.fixture(id, state.fixtureId);
        if (event != null) {
            state.friendlyMatch = true;
            state.cupMatch = true;
            state.decisiveTie = false;
            state.extraTimeAllowed = false;
            state.disciplineSeasonId = state.seasonId;
        }
    }

    public void completeFriendly(String id, MatchStateDto state) {
        if (!state.friendlyMatch) return;
        var event = repository.fixture(id, state.fixtureId);
        if (event == null || event.status().equals("COMPLETED")) return;
        long gate = ClubEventRules.gate(repository.rating(id, state.home.id), repository.rating(id, state.away.id));
        var date = LocalDate.parse(state.date);
        for (long team : List.of(state.home.id, state.away.id))
            money.ledger(
                id,
                team,
                date,
                "FRIENDLY_GATE",
                "Metade da renda do amistoso",
                gate / 2,
                "FRIENDLY_GATE:" + event.id()
            );
        money.ledger(
            id,
            state.away.id,
            date,
            "FRIENDLY_FEE",
            "Cachê de participação no amistoso",
            event.cost(),
            "FRIENDLY_FEE:" + event.id()
        );
        repository.update(
            id,
            event.id(),
            "COMPLETED",
            1,
            state.home.name +
                " " +
                state.home.goals +
                " × " +
                state.away.goals +
                " " +
                state.away.name +
                ". Renda total: R$ " +
                gate +
                "; cada clube recebeu R$ " +
                gate / 2 +
                ".",
            gate
        );
    }

    private void changed(String id, long team) {
        lifecycle.touch(id);
        events.publishEvent(new TeamUpdatedEvent(id, team, repository.changed(id, team)));
        events.publishEvent(new SaveChangedEvent(id));
    }

    private static void fail(String message) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, message);
    }
}
