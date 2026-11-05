package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.events.*;
import br.com.manager.models.requests.AdvanceDaysRequest;
import br.com.manager.models.responses.DayProgressResponse;
import br.com.manager.repository.*;
import br.com.manager.utils.TrainingRules;
import java.time.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DayProgressService {

    private final SaveService saves;
    private final SaveRepository leagues;
    private final DayProgressRepository repository;
    private final LeagueCalendarRepository calendar;
    private final NationalSeasonService seasons;
    private final LeagueLifecycleService lifecycle;
    private final ApplicationEventPublisher events;
    private final FinanceService finances;
    private final FinanceDailyService financialDays;
    private final WorldCompetitionService world;
    private final TournamentService tournaments;

    public DayProgressService(
        SaveService saves,
        SaveRepository leagues,
        DayProgressRepository repository,
        LeagueCalendarRepository calendar,
        NationalSeasonService seasons,
        LeagueLifecycleService lifecycle,
        ApplicationEventPublisher events,
        FinanceService finances,
        FinanceDailyService financialDays,
        WorldCompetitionService world,
        TournamentService tournaments
    ) {
        this.saves = saves;
        this.leagues = leagues;
        this.repository = repository;
        this.calendar = calendar;
        this.seasons = seasons;
        this.lifecycle = lifecycle;
        this.events = events;
        this.finances = finances;
        this.financialDays = financialDays;
        this.world = world;
        this.tournaments = tournaments;
    }

    @Transactional
    public DayProgressResponse state(String id, String token) {
        saves.authorizedRoom(id, token);
        var member = saves.realtimeMember(id, token.substring(7));
        var date = repository.date(id, member.teamId());
        seasons.ensureForDate(id, date);
        world.synchronize(id);
        return response(id, member.teamId(), 0, "");
    }

    private DayProgressResponse response(String id, long team, int days, String reason) {
        var date = repository.date(id, team);
        return new DayProgressResponse(
            date,
            repository.nextMatch(id, team),
            days,
            reason,
            calendar
                .events(id, team, date.minusDays(7), date)
                .stream()
                .filter(e -> e.status().equals("COMPLETED"))
                .toList()
        );
    }

    @Transactional
    public DayProgressResponse advance(String id, String token, AdvanceDaysRequest input) {
        if (!leagues.lock(id)) throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Liga não encontrada.");
        var room = saves.authorizedRoom(id, token);
        var member = saves.realtimeMember(id, token.substring(7));
        if (room.status().equals("LIVE") || member.ready()) fail(
            "Aguarde todos os jogos terminarem e retire sua prontidão antes de avançar os dias."
        );
        finances.requireApproved(id, member.teamId());
        var date = repository.date(id, member.teamId());
        if (input.expectedDate() == null || !input.expectedDate().equals(date)) fail(
            "O calendário mudou. Atualize a tela antes de avançar novamente."
        );
        if (input.days() < 1 || input.days() > 7) fail("Avance entre um e sete dias por vez.");
        seasons.ensureForDate(id, date.plusDays(input.days()));
        var next = repository.nextMatch(id, member.teamId());
        var drawDate = tournaments.nextDraw(id, date.plusDays(1));
        int advanced = 0,
            version = 0;
        String reason = "Período de preparação concluído.";
        while (advanced < input.days()) {
            if (next != null && !date.isBefore(next)) {
                reason = "Dia de jogo: prepare sua equipe e confirme a prontidão.";
                break;
            }
            int day = repository.day(id, date);
            boolean injury = false;
            for (var player : repository.athletes(id, member.teamId())) {
                if (player.injury() != null && !player.injury().isBlank()) {
                    if (player.returnDay() != null && player.returnDay() <= day) {
                        repository.recover(id, player.id());
                        repository.event(
                            id,
                            member.teamId(),
                            date,
                            "CLUB",
                            "Retorno aos treinos: " + player.name(),
                            "O departamento médico liberou o atleta."
                        );
                    }
                    continue;
                }
                if (date.getDayOfWeek() == DayOfWeek.SUNDAY || player.fitness() < 40) continue;
                var random = TrainingRules.random(id, member.teamId(), player.id(), date);
                if (random.nextDouble() < TrainingRules.injuryChance(player.fitness())) {
                    int days = TrainingRules.injuryDays(random);
                    repository.injure(id, player.id(), day + days);
                    injury = true;
                    repository.event(
                        id,
                        member.teamId(),
                        date,
                        "TRAINING",
                        "Lesão no treino: " + player.name(),
                        "O atleta sofreu uma lesão no treinamento. Retorno previsto em " +
                            days +
                            " dias, em " +
                            date.plusDays(days) +
                            ". Revise a escalação."
                    );
                }
            }
            repository.event(
                id,
                member.teamId(),
                date,
                "TRAINING",
                date.getDayOfWeek() == DayOfWeek.SUNDAY ? "Descanso do elenco" : "Treinamento concluído",
                date.getDayOfWeek() == DayOfWeek.SUNDAY
                    ? "Dia dedicado à recuperação física."
                    : "A comissão técnica concluiu o treino. O elenco segue em preparação para a próxima partida."
            );
            boolean important = repository.completeScheduled(id, member.teamId(), date);
            date = date.plusDays(1);
            version = repository.advance(id, member.teamId(), day + 1);
            advanced++;
            boolean financialEvent = financialDays.processThrough(id, member.teamId(), date);
            if (drawDate != null && !date.isBefore(drawDate)) {
                reason = "Dia de sorteio: acompanhe a montagem dos confrontos.";
                break;
            }

            if (finances.needsApproval(id, member.teamId())) {
                reason = "Nova temporada: confirme os objetivos e o or?amento no gerenciamento.";
                break;
            }
            if (financialEvent) {
                reason = "H? novidades financeiras ou respostas de negocia??o. Consulte o mercado e as finan?as.";
                break;
            }
            if (injury) {
                reason = "Treinamento interrompido por lesão. Confira o departamento médico e a escalação.";
                break;
            }
            if (important) {
                reason = "Um evento do clube requer sua atenção. Consulte o calendário.";
                break;
            }
            if (next != null && !date.isBefore(next)) {
                reason = "Dia de jogo: prepare sua equipe e confirme a prontidão.";
                break;
            }
        }
        if (advanced > 0) {
            world.synchronize(id);
            lifecycle.touch(id);
            events.publishEvent(new TeamUpdatedEvent(id, member.teamId(), version));
            events.publishEvent(new SaveChangedEvent(id));
        }
        return response(id, member.teamId(), advanced, reason);
    }

    private void fail(String text) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, text);
    }
}
