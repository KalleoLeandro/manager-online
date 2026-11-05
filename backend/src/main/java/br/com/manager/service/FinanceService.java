package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.NationalSeasonDto;
import br.com.manager.models.events.*;
import br.com.manager.models.requests.*;
import br.com.manager.models.responses.FinanceStateResponse;
import br.com.manager.repository.*;
import br.com.manager.utils.FinanceRules;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {

    private final FinanceRepository repository;
    private final SaveRepository leagues;
    private final DayProgressRepository dates;
    private final NationalSeasonService seasons;
    private final NationalSeasonRepository competitions;
    private final LeagueLifecycleService lifecycle;
    private final ApplicationEventPublisher events;
    private final boolean requireApproval;

    public FinanceService(
        FinanceRepository repository,
        SaveRepository leagues,
        DayProgressRepository dates,
        NationalSeasonService seasons,
        NationalSeasonRepository competitions,
        LeagueLifecycleService lifecycle,
        ApplicationEventPublisher events,
        @Value("${app.finance.require-season-approval:true}") boolean requireApproval
    ) {
        this.repository = repository;
        this.leagues = leagues;
        this.dates = dates;
        this.seasons = seasons;
        this.competitions = competitions;
        this.lifecycle = lifecycle;
        this.events = events;
        this.requireApproval = requireApproval;
    }

    public void lock(String id) {
        if (!leagues.lock(id)) throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Liga não encontrada.");
    }

    public LocalDate date(String id, long team) {
        return dates.date(id, team);
    }

    public void initialize(String id, long team) {
        repository.initialize(id, date(id, team));
    }

    private NationalSeasonDto season(String id, long team) {
        var date = date(id, team);
        seasons.ensureForDate(id, date);
        Long selected = repository.nextSeason(id, team);
        if (selected == null) {
            seasons.ensureForDate(id, date.plusYears(1));
            selected = repository.nextSeason(id, team);
        }
        final Long wanted = selected;
        return competitions
            .seasons(id, team)
            .stream()
            .filter(s -> Objects.equals(s.id(), wanted))
            .findFirst()
            .orElseThrow(() ->
                new DefaultErrorException(
                    HttpStatus.CONFLICT,
                    "Não há uma temporada nacional disponível para o orçamento."
                )
            );
    }

    public Plan ensurePlan(String id, long team) {
        initialize(id, team);
        settleCompleted(id, team);
        var season = season(id, team);
        var plan = repository.plan(id, team, season.id());
        if (plan == null) {
            int n = season.teamCount(),
                expected = repository.expectedRank(id, team);
            int target = FinanceRules.expectedObjective(expected, n);
            repository.createPlan(
                id,
                team,
                season.id(),
                FinanceRules.forecast(
                    repository.annualRevenue(id, team, season.country(), season.division()),
                    repository.commitment(id, team)
                ),
                target
            );
            plan = repository.plan(id, team, season.id());
        }
        if (!plan.approved()) {
            repository.rebaseUnapproved(
                id,
                team,
                season.id(),
                FinanceRules.forecast(
                    repository.annualRevenue(id, team, season.country(), season.division()),
                    repository.commitment(id, team)
                )
            );
            plan = repository.plan(id, team, season.id());
        }
        return plan;
    }

    public List<Objective> objectives(String id, long team, Plan plan, NationalSeasonDto season) {
        var result = new ArrayList<Objective>();
        var previous = repository.history(id, team);
        var last = previous.isEmpty() ? null : previous.getFirst();
        int expected = plan.expectedRank() == null ? repository.expectedRank(id, team) : plan.expectedRank(),
            trust = repository.account(id, team).boardTrust();
        var choices = new LinkedHashMap<Integer, String>();
        choices.put(1, "Ser campeão nacional");
        choices.put(Math.min(4, season.teamCount()), "Terminar entre os quatro primeiros");
        choices.put(Math.max(1, season.teamCount() / 2), "Terminar na metade superior");
        choices.put(Math.max(1, season.teamCount() - 3), "Evitar o rebaixamento");
        choices.forEach((rank, label) ->
            result.add(
                new Objective(
                    rank,
                    label,
                    Math.max(
                        (long) Math.ceil((repository.commitment(id, team) * 12) / .8),
                        FinanceRules.ceiling(
                            plan.baseFunding(),
                            trust,
                            last != null && last.finalRank() == 1,
                            last == null ? null : last.promiseMet(),
                            rank,
                            expected
                        )
                    ),
                    FinanceRules.promiseReaction(rank, expected, season.teamCount()).board(),
                    FinanceRules.promiseReaction(rank, expected, season.teamCount()).fans(),
                    FinanceRules.promiseReaction(rank, expected, season.teamCount()).description()
                )
            )
        );
        return result;
    }

    @Transactional
    public FinanceStateResponse state(String id, long team) {
        lock(id);
        var plan = ensurePlan(id, team);
        var season = competitions
            .seasons(id, team)
            .stream()
            .filter(s -> s.id() == plan.seasonId())
            .findFirst()
            .orElseThrow();
        long reserved = repository.reservedFees(id, team, null);
        return new FinanceStateResponse(
            date(id, team),
            team,
            "BRL",
            repository.annualRevenue(id, team, season.country(), season.division()),
            season,
            plan,
            repository.cash(id, team),
            repository.payroll(id, team),
            repository.account(id, team).staffSalaryMonthly(),
            repository.commitment(id, team) + repository.reservedWages(id, team, null),
            reserved,
            Math.max(
                0,
                Math.min(
                    repository.cash(id, team) - plan.reserveBudget(),
                    plan.transferBudget() - plan.transferSpent()
                ) - reserved
            ),
            repository.account(id, team).boardTrust(),
            repository.account(id, team).fanTrust(),
            plan.expectedRank() == null ? repository.expectedRank(id, team) : plan.expectedRank(),
            objectives(id, team, plan, season),
            repository.history(id, team),
            repository.ledger(id, team),
            !plan.approved()
        );
    }

    @Transactional
    public FinanceStateResponse approve(String id, long team, SeasonBudgetRequest input) {
        lock(id);
        if (repository.live(id)) fail("Aguarde os jogos terminarem para aprovar o orçamento.");
        var state = state(id, team);
        var plan = state.plan();
        if (input.seasonId() != plan.seasonId()) fail("O orçamento pertence a outra temporada. Atualize a tela.");
        if (plan.approved()) fail("O orçamento desta temporada já foi confirmado.");
        var goal = state
            .objectives()
            .stream()
            .filter(o -> o.rank() == input.promisedRank())
            .findFirst()
            .orElseThrow(() -> new DefaultErrorException(HttpStatus.BAD_REQUEST, "Selecione um objetivo válido."));
        long total =
            input.transferBudget() +
            input.salaryBudget() +
            input.infrastructureBudget() +
            input.miscellaneousBudget() +
            input.reserveBudget();
        if (total > goal.maximumFunding() || total <= 0) fail(
            "A divisão de verbas ultrapassa o limite negociável com a diretoria."
        );
        if (input.reserveBudget() < Math.ceil(total * .10)) fail("Mantenha pelo menos 10% da verba em reserva.");
        if (input.salaryBudget() < state.committedWagesMonthly() * 12) fail(
            "A verba salarial anual deve cobrir o elenco, os bônus previstos e o staff."
        );
        repository.approve(id, team, input);
        repository.adjustTrust(id, team, goal.boardTrustDelta(), goal.fanTrustDelta());
        repository.ledger(
            id,
            team,
            state.date(),
            "BOARD_FUNDING",
            "Verba anual aprovada pela diretoria",
            total,
            "FUNDING:" + plan.seasonId()
        );
        dates.event(
            id,
            team,
            state.date(),
            "CLUB",
            "Orçamento da temporada aprovado",
            "A diretoria aprovou as verbas. Compromisso no nacional: terminar até a posição " +
                input.promisedRank() +
                "."
        );
        changed(id, team);
        return state(id, team);
    }

    public void editable(String id, long team) {
        if (repository.live(id) || repository.ready(id, team)) fail(
            "Retire a prontidão e aguarde os jogos terminarem para negociar."
        );
    }

    public void requireApprovedForMember(String id, String member) {
        if (requireApproval) requireApproved(id, repository.teamForMember(id, member));
    }

    public void requireApproved(String id, long team) {
        if (requireApproval && !ensurePlan(id, team).approved()) fail(
            "Confirme os objetivos e o orçamento da temporada no gerenciamento antes de continuar."
        );
    }

    public boolean needsApproval(String id, long team) {
        return requireApproval && !ensurePlan(id, team).approved();
    }

    public void prepareComputerBudget(String id, long team) {
        if (repository.humanTeam(id, team)) return;
        var plan = ensurePlan(id, team);
        if (plan.approved()) return;
        var season = competitions
            .seasons(id, team)
            .stream()
            .filter(s -> s.id() == plan.seasonId())
            .findFirst()
            .orElseThrow();
        var goal = objectives(id, team, plan, season)
            .stream()
            .filter(o -> o.rank() == plan.promisedRank())
            .findFirst()
            .orElseThrow();
        long total = (goal.maximumFunding() * 95) / 100,
            wages = Math.max(
                repository.commitment(id, team) * 12,
                Math.min((long) Math.ceil(repository.commitment(id, team) * 12 * 1.15), (total * 78) / 100)
            ),
            reserve = (total + 9) / 10,
            remaining = Math.max(0, total - wages - reserve),
            infrastructure = (remaining * 15) / 100,
            miscellaneous = (remaining * 10) / 100;
        var input = new SeasonBudgetRequest(
            plan.seasonId(),
            goal.rank(),
            remaining - infrastructure - miscellaneous,
            wages,
            infrastructure,
            miscellaneous,
            reserve
        );
        repository.approve(id, team, input);
        repository.ledger(
            id,
            team,
            date(id, team),
            "BOARD_FUNDING",
            "Verba anual administrada pelo clube do computador",
            total,
            "FUNDING:" + plan.seasonId()
        );
    }

    public void requireFunds(String id, long buyer, TransferOfferRequest terms, Long excluded) {
        var plan = ensurePlan(id, buyer);
        if (!plan.approved()) fail("A diretoria precisa aprovar o orçamento antes de negociar jogadores.");
        long cost = terms.fee() + terms.signingBonus(),
            reserved = repository.reservedFees(id, buyer, excluded);
        if (
            cost >
            Math.min(repository.cash(id, buyer) - plan.reserveBudget(), plan.transferBudget() - plan.transferSpent()) -
                reserved
        ) fail("Verba de transferências ou caixa insuficiente, considerando propostas em andamento.");
        var old = repository.contract(id, terms.playerId());
        long offset = old != null && Objects.equals(old.teamId(), buyer) ? FinanceRules.commitment(old) : 0;
        long wages =
            repository.commitment(id, buyer) +
            repository.reservedWages(id, buyer, excluded) -
            offset +
            FinanceRules.commitment(terms);
        if (wages > plan.salaryBudget() / 12) fail(
            "A proposta excede o limite salarial, incluindo bônus previstos e staff."
        );
    }

    public void changed(String id, long team) {
        repository.touchTeam(id, team);
        lifecycle.touch(id);
        events.publishEvent(new TeamUpdatedEvent(id, team, repository.version(id, team)));
        events.publishEvent(new SaveChangedEvent(id));
    }

    public void settleCompleted(String id, long team) {
        for (var plan : repository.unfinishedPlans(id, team)) {
            Integer rank = repository.finalRank(id, team, plan.seasonId());
            if (rank == null) continue;
            repository.settle(id, team, plan, rank);
            if (rank == 1) {
                long prize = Math.max(500000, plan.baseFunding() / 10);
                repository.ledger(
                    id,
                    team,
                    date(id, team),
                    "TITLE_PRIZE",
                    "Premiação pelo título nacional",
                    prize,
                    "TITLE_PRIZE:" + plan.seasonId()
                );
                for (var c : repository.contracts(id, team))
                    if (c.titleBonus() > 0) repository.ledger(
                        id,
                        team,
                        date(id, team),
                        "TITLE_BONUS",
                        "Bônus de título: " + c.name(),
                        -c.titleBonus(),
                        "TITLE_BONUS:" + plan.seasonId() + ":" + c.playerId()
                    );
            }
            dates.event(
                id,
                team,
                date(id, team),
                "CLUB",
                "Balanço das promessas da temporada",
                "Posição final: " +
                    rank +
                    ". Promessa: até " +
                    plan.promisedRank() +
                    ". " +
                    FinanceRules.seasonReaction(
                        rank,
                        plan.promisedRank(),
                        plan.expectedRank() == null ? repository.expectedRank(id, team) : plan.expectedRank(),
                        competitions
                            .seasons(id, team)
                            .stream()
                            .filter(s -> s.id() == plan.seasonId())
                            .findFirst()
                            .orElseThrow()
                            .teamCount()
                    ).description()
            );
        }
    }

    public void matchBonuses(String id, MatchStateDto match) {
        for (var team : List.of(match.home, match.away)) {
            if (repository.contracts(id, team.id).isEmpty()) continue;
            for (var p : team.players) {
                if (!p.entered || p.minutes <= 0) continue;
                var c = repository.contract(id, p.id);
                if (c == null) continue;
                long amount = c.appearanceBonus() + c.goalBonus() * p.goals;
                if (amount > 0) repository.ledger(
                    id,
                    team.id,
                    LocalDate.parse(match.date),
                    "MATCH_BONUS",
                    "Bônus de jogo: " + p.name,
                    -amount,
                    "MATCH_BONUS:" + match.fixtureId + ":" + p.id
                );
            }
            settleCompleted(id, team.id);
        }
    }

    private void fail(String message) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, message);
    }
}
