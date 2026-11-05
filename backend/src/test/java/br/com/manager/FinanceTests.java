package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.requests.*;
import br.com.manager.models.responses.*;
import br.com.manager.repository.*;
import br.com.manager.service.*;
import br.com.manager.utils.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.finance.require-season-approval=true")
@ActiveProfiles("test")
@Transactional
class FinanceTests {

    @Test
    void bulkInitializationCoversEveryClubAndNeverReplacesAnExistingContract() {
        var access = create();
        String id = access.room().id();
        long own = team(access);
        var state = finance.state(id, own);
        int players = db.queryForObject("SELECT COUNT(*) FROM save_players WHERE save_id=?", Integer.class, id);
        assertThat(players).isGreaterThan(500);
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM player_contracts WHERE save_id=?", Integer.class, id)
        ).isEqualTo(players);
        assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM save_players p LEFT JOIN player_contracts c ON c.save_id=p.save_id AND c.player_id=p.player_id WHERE p.save_id=? AND (c.player_id IS NULL OR c.team_id<>p.team_id)",
                Integer.class,
                id
            )
        ).isZero();
        var contract = repository.contracts(id, own).getFirst();
        assertThat(contract.salaryMonthly()).isEqualTo(FinanceRules.salary(contract.marketValue()));
        assertThat(contract.endDate()).isEqualTo(state.date().plusMonths(24 + (contract.playerId() % 25)));
        db.update(
            "UPDATE player_contracts SET salary_monthly=765432,end_date=DATE '2030-02-03' WHERE save_id=? AND player_id=?",
            id,
            contract.playerId()
        );
        var missing = repository.contracts(id, own).getLast();
        db.update("DELETE FROM player_contracts WHERE save_id=? AND player_id=?", id, missing.playerId());
        finance.initialize(id, own);
        var preserved = repository.contract(id, contract.playerId());
        assertThat(preserved.salaryMonthly()).isEqualTo(765432);
        assertThat(preserved.endDate()).isEqualTo(LocalDate.of(2030, 2, 3));
        assertThat(repository.contract(id, missing.playerId()).salaryMonthly()).isEqualTo(missing.salaryMonthly());
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM player_contracts WHERE save_id=?", Integer.class, id)
        ).isEqualTo(players);
    }

    @Test
    void revaluationUpdatesOnlyLegacyEstimatesPreservesPendingOffersAndNeverChangesExistingContracts() {
        var a = create();
        var id = a.room().id();
        long team = team(a);
        var initial = finance.state(id, team);
        var players = repository.contracts(id, team);
        var old = players.get(0);
        var manual = players.get(1);
        var pending = players.get(2);
        long legacy = FinanceRules.legacyMarketValue(old.ability(), old.age());
        db.update(
            "UPDATE save_players SET market_value=?,valuation_version=0 WHERE save_id=? AND player_id=?",
            legacy,
            id,
            old.playerId()
        );
        db.update(
            "UPDATE save_players SET market_value=123456789,valuation_version=0 WHERE save_id=? AND player_id=?",
            id,
            manual.playerId()
        );
        db.update(
            "UPDATE save_players SET market_value=?,valuation_version=0 WHERE save_id=? AND player_id=?",
            FinanceRules.legacyMarketValue(pending.ability(), pending.age()),
            id,
            pending.playerId()
        );
        var originalPending = repository.contract(id, pending.playerId());
        db.update(
            "INSERT INTO transfer_offers(save_id,player_id,buyer_id,seller_id,season_id,status,club_meter,player_meter,fee,salary_monthly,months,offered_date,expires_date,feedback) VALUES(?,?,?,?,?,'PENDING',50,50,100000,10000,36,?,?,'test')",
            id,
            pending.playerId(),
            team,
            team,
            initial.season().id(),
            initial.date(),
            initial.date().plusDays(14)
        );
        var changed = finance.state(id, team);
        assertThat(repository.contract(id, old.playerId()).marketValue()).isEqualTo(
            FinanceRules.marketValue(old.ability(), old.age())
        );
        assertThat(repository.contract(id, manual.playerId()).marketValue()).isEqualTo(123456789);
        assertThat(repository.contract(id, pending.playerId()).marketValue()).isEqualTo(originalPending.marketValue());
        assertThat(repository.contract(id, old.playerId()).salaryMonthly()).isEqualTo(old.salaryMonthly());
        assertThat(repository.contract(id, old.playerId()).endDate()).isEqualTo(old.endDate());
        assertThat(changed.cash()).isEqualTo(initial.cash());
        db.update("UPDATE transfer_offers SET status='CANCELLED' WHERE save_id=?", id);
        finance.state(id, team);
        assertThat(repository.contract(id, pending.playerId()).marketValue()).isEqualTo(
            FinanceRules.marketValue(pending.ability(), pending.age())
        );
    }

    @Test
    void onlyUnapprovedLegacyBudgetsAreRebasedWithoutCreditingCash() {
        var a = create();
        var id = a.room().id();
        long team = team(a);
        var initial = finance.state(id, team);
        db.update(
            "UPDATE season_finances SET base_funding=1000000,funding_version=0 WHERE save_id=? AND team_id=?",
            id,
            team
        );
        var updated = finance.state(id, team);
        assertThat(updated.plan().baseFunding()).isGreaterThan(1000000);
        assertThat(updated.cash()).isEqualTo(initial.cash());
        var approved = approve(a);
        db.update("UPDATE season_finances SET funding_version=0 WHERE save_id=? AND team_id=?", id, team);
        assertThat(finance.state(id, team).plan()).isEqualTo(approved.plan());
        assertThat(finance.state(id, team).cash()).isEqualTo(approved.cash());
    }

    @Test
    void cautiousPromiseForAFavoriteReducesBothTrustsOnceAndPreviewsAreReadOnly() {
        var a = create();
        var id = a.room().id();
        long team = team(a);
        db.update("UPDATE save_teams SET rating=50 WHERE save_id=?", id);
        db.update("UPDATE save_teams SET rating=99 WHERE save_id=? AND team_id=?", id, team);
        var initial = finance.state(id, team);
        assertThat(initial.expectedRank()).isEqualTo(1);
        var goal = initial.objectives().stream().max(Comparator.comparingInt(Objective::rank)).orElseThrow();
        assertThat(goal.boardTrustDelta()).isNegative();
        assertThat(goal.fanTrustDelta()).isLessThan(goal.boardTrustDelta());
        assertThat(finance.state(id, team).fanTrust()).isEqualTo(initial.fanTrust());
        long salary = initial.committedWagesMonthly() * 12,
            total = (goal.maximumFunding() * 95) / 100,
            reserve = (total + 9) / 10;
        var terms = new SeasonBudgetRequest(
            initial.season().id(),
            goal.rank(),
            total - salary - reserve,
            salary,
            0,
            0,
            reserve
        );
        var approved = finance.approve(id, team, terms);
        assertThat(approved.boardTrust()).isEqualTo(initial.boardTrust() + goal.boardTrustDelta());
        assertThat(approved.fanTrust()).isEqualTo(initial.fanTrust() + goal.fanTrustDelta());
        assertThatThrownBy(() -> finance.approve(id, team, terms)).isInstanceOf(DefaultErrorException.class);
        assertThat(finance.state(id, team).fanTrust()).isEqualTo(approved.fanTrust());
        db.update("UPDATE save_teams SET rating=1 WHERE save_id=? AND team_id=?", id, team);
        assertThat(finance.state(id, team).expectedRank()).isEqualTo(1);
        repository.settle(id, team, approved.plan(), goal.rank());
        var settled = repository.account(id, team);
        assertThat(settled.boardTrust()).isLessThan(approved.boardTrust());
        assertThat(settled.fanTrust()).isLessThan(approved.fanTrust());
        repository.settle(id, team, approved.plan(), goal.rank());
        assertThat(repository.account(id, team)).isEqualTo(settled);
        repository.adjustTrust(id, team, 1000, 1000);
        assertThat(repository.account(id, team).boardTrust()).isEqualTo(100);
        assertThat(repository.account(id, team).fanTrust()).isEqualTo(100);
        repository.adjustTrust(id, team, -1000, -1000);
        assertThat(repository.account(id, team).boardTrust()).isZero();
        assertThat(repository.account(id, team).fanTrust()).isZero();
    }

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    FinanceService finance;

    @Autowired
    FinanceFacadeService facade;

    @Autowired
    FinanceDailyService daily;

    @Autowired
    TransferService transfers;

    @Autowired
    FinanceRepository repository;

    @Autowired
    TransferRepository offerRepository;

    @Autowired
    DayProgressService days;

    @Autowired
    JdbcTemplate db;

    private LeagueAccessResponse create() {
        return create(1);
    }

    private LeagueAccessResponse create(int capacity) {
        return saves.create(
            new CreateLeagueRequest("Gestão financeira", capacity, "Treinador", catalog.teams(null).getFirst().id())
        );
    }

    private String token(LeagueAccessResponse a) {
        return "Bearer " + a.accessToken();
    }

    private long team(LeagueAccessResponse a) {
        return a.room().members().getFirst().teamId();
    }

    private FinanceStateResponse approve(LeagueAccessResponse a) {
        var d = finance.state(a.room().id(), team(a));
        var goal = d
            .objectives()
            .stream()
            .filter(o -> o.rank() == d.plan().promisedRank())
            .findFirst()
            .orElseThrow();
        long total = (goal.maximumFunding() * 95) / 100,
            salary = Math.max(d.committedWagesMonthly() * 12, (total * 60) / 100),
            reserve = (total + 9) / 10;
        return finance.approve(
            a.room().id(),
            team(a),
            new SeasonBudgetRequest(d.season().id(), goal.rank(), total - salary - reserve, salary, 0, 0, reserve)
        );
    }

    private Contract target(LeagueAccessResponse a) {
        long other = catalog
            .teams(null)
            .stream()
            .filter(t -> t.id() != team(a))
            .findFirst()
            .orElseThrow()
            .id();
        return repository.contracts(a.room().id(), other).getLast();
    }

    private TransferOfferRequest strong(LeagueAccessResponse a, Contract c) {
        long desired = FinanceRules.desiredSalary(
            c,
            repository.rating(a.room().id(), team(a)),
            c.teamId() == null
                ? repository.rating(a.room().id(), team(a))
                : repository.rating(a.room().id(), c.teamId())
        );
        return new TransferOfferRequest(
            c.playerId(),
            Objects.equals(c.teamId(), team(a)) || c.teamId() == null ? 0 : (c.marketValue() * 14) / 10,
            (desired * 15) / 10,
            36,
            1000,
            0,
            0,
            0
        );
    }

    @Test
    void newSeasonRequiresApprovalAndSeedsEstimatedValuesWithoutOverwritingContracts() {
        var a = create();
        var id = a.room().id();
        var d = finance.state(id, team(a));
        assertThat(d.approvalRequired()).isTrue();
        assertThatThrownBy(() -> saves.ready(id, token(a), true)).isInstanceOf(DefaultErrorException.class);
        assertThatThrownBy(() -> days.advance(id, token(a), new AdvanceDaysRequest(d.date(), 1))).isInstanceOf(
            DefaultErrorException.class
        );
        var c = repository.contracts(id, team(a)).getFirst();
        assertThat(c.marketValue()).isPositive();
        assertThat(c.salaryMonthly()).isPositive();
        assertThat(c.estimated()).isTrue();
        assertThat(c.endDate()).isAfter(d.date());
        assertThat(
            db.queryForObject("SELECT market_value FROM catalog_players WHERE id=?", Long.class, c.playerId())
        ).isPositive();
        var approved = approve(a);
        assertThat(approved.plan().approved()).isTrue();
        assertThat(approved.cash()).isGreaterThan(d.cash());
        assertThatThrownBy(() -> approve(a)).isInstanceOf(DefaultErrorException.class);
        assertThat(
            finance
                .state(id, team(a))
                .ledger()
                .stream()
                .filter(l -> l.category().equals("BOARD_FUNDING"))
                .count()
        ).isEqualTo(1);
    }

    @Test
    void refusesUnderfundedSalariesAndReserveAndOverpromisedFunding() {
        var a = create();
        var d = finance.state(a.room().id(), team(a));
        long max = d.objectives().getFirst().maximumFunding();
        assertThatThrownBy(() ->
            finance.approve(a.room().id(), team(a), new SeasonBudgetRequest(d.season().id(), 1, max, 1, 0, 0, 1))
        ).isInstanceOf(DefaultErrorException.class);
        assertThatThrownBy(() ->
            finance.approve(a.room().id(), team(a), new SeasonBudgetRequest(d.season().id(), 1, 0, 1, 0, 0, max / 10))
        ).isInstanceOf(DefaultErrorException.class);
        assertThat(finance.state(a.room().id(), team(a)).plan().approved()).isFalse();
    }

    @Test
    void payrollIncludesStaffAndIsChargedOnlyOncePerMonth() {
        var a = create();
        var d = approve(a);
        var id = a.room().id();
        db.update(
            "UPDATE club_finance_accounts SET staff_salary_monthly=12345 WHERE save_id=? AND team_id=?",
            id,
            team(a)
        );
        long payroll = repository.payroll(id, team(a)),
            cash = repository.cash(id, team(a));
        var monthEnd = d.date().with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        daily.processThrough(id, team(a), monthEnd);
        assertThat(repository.cash(id, team(a))).isEqualTo(cash - payroll);
        daily.processThrough(id, team(a), monthEnd);
        assertThat(repository.cash(id, team(a))).isEqualTo(cash - payroll);
        assertThat(
            repository
                .ledger(id, team(a))
                .stream()
                .filter(l -> l.category().equals("PAYROLL"))
                .count()
        ).isEqualTo(1);
    }

    @Test
    void strongOfferTransfersPlayerAndMoneyAtomicallyAndRecalculatesSquads() {
        var a = create();
        approve(a);
        var id = a.room().id();
        var c = target(a);
        var terms = strong(a, c);
        long buyerCash = repository.cash(id, team(a)),
            sellerCash = repository.cash(id, c.teamId());
        var offer = transfers.submit(id, team(a), terms);
        assertThat(offer.status()).isEqualTo("ACCEPTED");
        var signed = repository.contract(id, c.playerId());
        assertThat(signed.teamId()).isEqualTo(team(a));
        assertThat(signed.salaryMonthly()).isEqualTo(terms.salaryMonthly());
        assertThat(signed.estimated()).isFalse();
        assertThat(repository.cash(id, team(a))).isEqualTo(buyerCash - terms.fee() - terms.signingBonus());
        assertThat(repository.cash(id, c.teamId())).isEqualTo(sellerCash + terms.fee());
        assertThat(finance.state(id, team(a)).plan().transferSpent()).isEqualTo(terms.fee() + terms.signingBonus());
        assertThat(transfers.process(id, team(a), finance.date(id, team(a)).plusDays(2))).isFalse();
        assertThat(repository.cash(id, team(a))).isEqualTo(buyerCash - terms.fee() - terms.signingBonus());
    }

    @Test
    void pendingOffersReserveFundsCannotBeDoubleBookedAndCancellationReleasesThem() {
        var a = create();
        var d = approve(a);
        var id = a.room().id();
        var c = target(a);
        long desired = FinanceRules.desiredSalary(c, repository.rating(id, team(a)), repository.rating(id, c.teamId()));
        long fee = (d.transferAvailable() * 60) / 100;
        var terms = new TransferOfferRequest(c.playerId(), fee, (desired * 80) / 100, 36, 0, 0, 0, 0);
        var offer = transfers.submit(id, team(a), terms);
        assertThat(offer.status()).isEqualTo("PENDING");
        assertThat(offer.decisionDate()).isAfter(d.date()).isBeforeOrEqualTo(d.date().plusDays(4));
        assertThat(repository.reservedFees(id, team(a), null)).isEqualTo(fee);
        var second = repository
            .contracts(id, c.teamId())
            .stream()
            .filter(p -> p.playerId() != c.playerId())
            .findFirst()
            .orElseThrow();
        assertThatThrownBy(() ->
            transfers.submit(
                id,
                team(a),
                new TransferOfferRequest(second.playerId(), fee, (desired * 80) / 100, 36, 0, 0, 0, 0)
            )
        ).isInstanceOf(DefaultErrorException.class);
        long cash = repository.cash(id, team(a));
        transfers.cancel(id, team(a), offer.id());
        assertThat(repository.reservedFees(id, team(a), null)).isZero();
        assertThat(repository.cash(id, team(a))).isEqualTo(cash);
    }

    @Test
    void pendingResponsesUseGameDatesAndExpiredOffersReleaseReservations() {
        var a = create();
        approve(a);
        var id = a.room().id();
        var c = target(a);
        long desired = FinanceRules.desiredSalary(c, repository.rating(id, team(a)), repository.rating(id, c.teamId()));
        var o = transfers.submit(
            id,
            team(a),
            new TransferOfferRequest(c.playerId(), c.marketValue(), desired, 36, 0, 0, 0, 0)
        );
        assertThat(o.status()).isEqualTo("PENDING");
        transfers.process(id, team(a), o.offeredDate());
        assertThat(offerRepository.offer(id, o.id()).status()).isEqualTo("PENDING");
        transfers.process(id, team(a), o.decisionDate());
        assertThat(offerRepository.offer(id, o.id()).status()).isIn("ACCEPTED", "NEGOTIABLE", "REJECTED");
        if (offerRepository.offer(id, o.id()).status().equals("NEGOTIABLE")) {
            transfers.process(id, team(a), o.expiresDate());
            assertThat(offerRepository.offer(id, o.id()).status()).isEqualTo("EXPIRED");
        }
    }

    @Test
    void anInsufficientOfferCanBeImprovedWithoutAccumulatingReservations() {
        var a = create();
        approve(a);
        var id = a.room().id();
        var c = target(a);
        long desired = FinanceRules.desiredSalary(c, repository.rating(id, team(a)), repository.rating(id, c.teamId()));
        var o = transfers.submit(
            id,
            team(a),
            new TransferOfferRequest(c.playerId(), c.marketValue(), desired, 36, 0, 0, 0, 0)
        );
        var improved = transfers.improve(id, team(a), o.id(), strong(a, c));
        assertThat(improved.status()).isEqualTo("ACCEPTED");
        assertThat(repository.reservedFees(id, team(a), null)).isZero();
    }

    @Test
    void humanSellerMustAuthorizeAndOtherManagersCannotAnswer() {
        var a = create(2);
        var id = a.room().id();
        var seller = catalog
            .teams(null)
            .stream()
            .filter(t -> t.id() != team(a))
            .findFirst()
            .orElseThrow();
        var b = saves.join(a.room().inviteCode(), new JoinLeagueRequest("Outro treinador", seller.id()));
        approve(a);
        var c = repository.contracts(id, seller.id()).getLast();
        var o = transfers.submit(id, team(a), strong(a, c));
        assertThat(o.status()).isEqualTo("CLUB_REVIEW");
        assertThat(repository.contract(id, c.playerId()).teamId()).isEqualTo(seller.id());
        assertThatThrownBy(() ->
            transfers.decideClub(id, team(a), o.id(), new TransferDecisionRequest(true, o.revision()))
        ).isInstanceOf(DefaultErrorException.class);
        assertThat(
            transfers.decideClub(id, seller.id(), o.id(), new TransferDecisionRequest(true, o.revision())).status()
        ).isEqualTo("ACCEPTED");
    }

    @Test
    void ownPlayerCanRenewAndBonusesArePaidOnlyOnceWhenEarned() {
        var a = create();
        approve(a);
        var id = a.room().id();
        var c = repository.contracts(id, team(a)).getLast();
        int ratingBefore = repository.rating(id, team(a));
        var terms = new TransferOfferRequest(c.playerId(), 0, c.salaryMonthly() * 2, 24, 1000, 500, 1000, 2000);
        var o = transfers.submit(id, team(a), terms);
        assertThat(o.status()).isEqualTo("ACCEPTED");
        assertThat(repository.rating(id, team(a))).isEqualTo(ratingBefore);
        var contract = repository.contract(id, c.playerId());
        assertThat(contract.endDate()).isEqualTo(finance.date(id, team(a)).plusMonths(24));
        assertThat(contract.appearanceBonus()).isEqualTo(500);
        var match = new MatchStateDto();
        match.fixtureId = 999;
        match.seasonId = finance.ensurePlan(id, team(a)).seasonId();
        match.date = finance.date(id, team(a)).toString();
        match.home.id = team(a);
        match.away.id = target(a).teamId();
        var p = new MatchStateDto.Player();
        p.id = c.playerId();
        p.name = c.name();
        p.entered = true;
        p.minutes = 90;
        p.goals = 2;
        match.home.players.add(p);
        long cash = repository.cash(id, team(a));
        finance.matchBonuses(id, match);
        finance.matchBonuses(id, match);
        assertThat(repository.cash(id, team(a))).isEqualTo(cash - 2500);
    }

    @Test
    void contractExpiryReleasesAthleteAndStopsHisSalaryCommitment() {
        var a = create();
        var d = approve(a);
        var id = a.room().id();
        var c = repository.contracts(id, team(a)).getLast();
        db.update("UPDATE player_contracts SET end_date=? WHERE save_id=? AND player_id=?", d.date(), id, c.playerId());
        daily.processThrough(id, team(a), d.date());
        assertThat(repository.contract(id, c.playerId()).teamId()).isNull();
        assertThat(repository.contract(id, c.playerId()).salaryMonthly()).isZero();
        var free = repository.contract(id, c.playerId());
        assertThat(free.expectedSalary()).isPositive();
        assertThat(FinanceRules.desiredSalary(free, 80, 80)).isGreaterThanOrEqualTo(
            FinanceRules.salary(free.marketValue())
        );
        var signed = transfers.submit(id, team(a), strong(a, free));
        assertThat(signed.status()).isEqualTo("ACCEPTED");
        assertThat(signed.fee()).isZero();
        assertThat(repository.contract(id, c.playerId()).teamId()).isEqualTo(team(a));
    }

    @Test
    void winningAndKeepingPromisesImprovesNextSeasonsBudgetAndPaysPrizeOnce() {
        var a = create();
        var id = a.room().id();
        var initial = finance.state(id, team(a));
        var win = initial
            .objectives()
            .stream()
            .filter(o -> o.rank() == 1)
            .findFirst()
            .orElseThrow();
        long salary = initial.committedWagesMonthly() * 12,
            total = (win.maximumFunding() * 95) / 100,
            reserve = total / 10 + 1;
        finance.approve(
            id,
            team(a),
            new SeasonBudgetRequest(initial.season().id(), 1, total - salary - reserve, salary, 0, 0, reserve)
        );
        db.update(
            "UPDATE national_fixtures SET status='PLAYED',home_goals=CASE WHEN home_team_id=? THEN 2 ELSE 0 END,away_goals=CASE WHEN away_team_id=? THEN 2 ELSE 0 END WHERE save_id=? AND season_id=?",
            team(a),
            team(a),
            id,
            initial.season().id()
        );
        var next = finance.state(id, team(a));
        assertThat(next.history()).anyMatch(h -> h.finalRank() == 1 && h.promiseMet());
        assertThat(next.boardTrust()).isGreaterThan(initial.boardTrust());
        assertThat(next.approvalRequired()).isTrue();
        assertThat(next.season().id()).isNotEqualTo(initial.season().id());
        assertThat(
            next
                .objectives()
                .stream()
                .filter(o -> o.rank() == 1)
                .findFirst()
                .orElseThrow()
                .maximumFunding()
        ).isGreaterThan(win.maximumFunding());
        finance.state(id, team(a));
        assertThat(
            repository
                .ledger(id, team(a))
                .stream()
                .filter(l -> l.category().equals("TITLE_PRIZE"))
                .count()
        ).isEqualTo(1);
    }

    @Test
    void breakingAChampionshipPromiseReducesConfidenceAndNextNegotiableBudget() {
        var a = create();
        var id = a.room().id();
        var initial = finance.state(id, team(a));
        var win = initial
            .objectives()
            .stream()
            .filter(o -> o.rank() == 1)
            .findFirst()
            .orElseThrow();
        long salary = initial.committedWagesMonthly() * 12,
            total = (win.maximumFunding() * 95) / 100,
            reserve = total / 10 + 1;
        finance.approve(
            id,
            team(a),
            new SeasonBudgetRequest(initial.season().id(), 1, total - salary - reserve, salary, 0, 0, reserve)
        );
        db.update(
            "UPDATE national_fixtures SET status='PLAYED',home_goals=CASE WHEN away_team_id=? THEN 2 ELSE 0 END,away_goals=CASE WHEN home_team_id=? THEN 2 ELSE 0 END WHERE save_id=? AND season_id=?",
            team(a),
            team(a),
            id,
            initial.season().id()
        );
        var next = finance.state(id, team(a));
        assertThat(next.history().getFirst().promiseMet()).isFalse();
        assertThat(next.boardTrust()).isLessThan(initial.boardTrust());
        assertThat(
            next
                .objectives()
                .stream()
                .filter(o -> o.rank() == 1)
                .findFirst()
                .orElseThrow()
                .maximumFunding()
        ).isLessThan(win.maximumFunding());
    }

    @Test
    void monetaryAndNegotiationRulesHaveBoundedValuationsAndAllResponseTypes() {
        assertThat(FinanceRules.marketValue(90, 22)).isGreaterThan(FinanceRules.marketValue(70, 22));
        assertThat(FinanceRules.marketValue(80, 34)).isLessThan(FinanceRules.marketValue(80, 23));
        assertThat(FinanceRules.marketValue(99, 18)).isLessThanOrEqualTo(1500000000L);
        assertThat(NegotiationRules.decision(80, .5)).isEqualTo("ACCEPTED");
        assertThat(NegotiationRules.decision(60, .5)).isEqualTo("NEGOTIABLE");
        assertThat(NegotiationRules.decision(60, .95)).isEqualTo("REJECTED");
    }

    @Test
    void computerClubsKeepTheirSquadsAndReceiveOnlyOneSeasonFunding() {
        var a = create();
        var d = approve(a);
        var id = a.room().id();
        long other = catalog
            .teams(null)
            .stream()
            .filter(t -> t.id() != team(a) && t.leagueId().equals(catalog.teams(null).getFirst().leagueId()))
            .findFirst()
            .orElseThrow()
            .id();
        var contract = repository.contracts(id, other).getLast();
        db.update(
            "UPDATE player_contracts SET end_date=? WHERE save_id=? AND player_id=?",
            d.date(),
            id,
            contract.playerId()
        );
        daily.processThrough(id, other, d.date());
        assertThat(repository.contract(id, contract.playerId()).teamId()).isEqualTo(other);
        assertThat(repository.contract(id, contract.playerId()).endDate()).isEqualTo(d.date().plusMonths(24));
        assertThat(finance.ensurePlan(id, other).approved()).isTrue();
        long cash = repository.cash(id, other);
        daily.processThrough(id, other, d.date());
        assertThat(repository.cash(id, other)).isEqualTo(cash);
        assertThat(
            repository
                .ledger(id, other)
                .stream()
                .filter(l -> l.category().equals("BOARD_FUNDING"))
                .count()
        ).isEqualTo(1);
    }
}
