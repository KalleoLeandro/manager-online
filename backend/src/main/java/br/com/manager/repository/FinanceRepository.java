package br.com.manager.repository;

import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.requests.*;
import br.com.manager.utils.FinanceRules;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceRepository {

    private final NamedParameterJdbcTemplate db;

    public FinanceRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public MapSqlParameterSource params(String id, long team) {
        return new MapSqlParameterSource("id", id).addValue("team", team);
    }

    private record Seed(long id, int ability, Integer age, long value) {}

    private record ContractSeed(long player, Long team, long value) {}

    private static final RowMapper<Seed> SEED = (r, n) ->
        new Seed(r.getLong(1), r.getInt(2), r.getObject(3, Integer.class), r.getLong(4));
    private static final String CONTRACT_SELECT =
        "SELECT p.player_id,p.name,p.team_id,t.name AS team_name,p.position,p.ability,p.age,p.market_value,c.*,EXISTS(SELECT 1 FROM save_members m WHERE m.save_id=p.save_id AND m.team_id=p.team_id) AS human_club FROM save_players p JOIN player_contracts c ON c.save_id=p.save_id AND c.player_id=p.player_id LEFT JOIN save_teams t ON t.save_id=p.save_id AND t.team_id=p.team_id ";
    private static final RowMapper<Contract> CONTRACT = (r, n) ->
        new Contract(
            r.getLong("player_id"),
            r.getString("name"),
            r.getObject("team_id", Long.class),
            r.getString("team_name"),
            r.getString("position"),
            r.getInt("ability"),
            r.getObject("age", Integer.class),
            r.getLong("market_value"),
            r.getLong("salary_monthly"),
            Math.max(r.getLong("salary_monthly"), FinanceRules.salary(r.getLong("market_value"))),
            r.getObject("start_date", LocalDate.class),
            r.getObject("end_date", LocalDate.class),
            r.getLong("signing_bonus"),
            r.getLong("appearance_bonus"),
            r.getLong("goal_bonus"),
            r.getLong("title_bonus"),
            r.getBoolean("estimated"),
            r.getBoolean("human_club")
        );
    private static final RowMapper<Plan> PLAN = (r, n) ->
        new Plan(
            r.getLong("id"),
            r.getLong("season_id"),
            r.getBoolean("approved"),
            r.getInt("promised_rank"),
            r.getLong("base_funding"),
            r.getLong("transfer_budget"),
            r.getLong("salary_budget"),
            r.getLong("infrastructure_budget"),
            r.getLong("miscellaneous_budget"),
            r.getLong("reserve_budget"),
            r.getLong("transfer_spent"),
            r.getObject("final_rank", Integer.class),
            r.getObject("promise_met", Boolean.class),
            r.getBoolean("settled"),
            r.getObject("expected_rank", Integer.class)
        );

    public void initialize(String id, LocalDate date) {
        revalueEstimates(id);
        var scope = new MapSqlParameterSource("id", id);
        long playerCount = db.queryForObject("SELECT COUNT(*) FROM save_players WHERE save_id=:id", scope, Long.class);
        long contractCount = db.queryForObject(
            "SELECT COUNT(*) FROM player_contracts WHERE save_id=:id",
            scope,
            Long.class
        );
        List<ContractSeed> seeds = List.of();
        // The FK and primary key guarantee at most one contract per existing player.
        // Avoid anti-joins over freshly inserted rows, whose planner estimates can be stale.
        if (playerCount != contractCount) {
            var existing =
                contractCount == 0
                    ? Set.<Long>of()
                    : new HashSet<>(
                          db.queryForList("SELECT player_id FROM player_contracts WHERE save_id=:id", scope, Long.class)
                      );
            seeds = db
                .query("SELECT player_id,team_id,market_value FROM save_players WHERE save_id=:id", scope, (r, n) ->
                    new ContractSeed(
                        r.getLong("player_id"),
                        r.getObject("team_id", Long.class),
                        r.getLong("market_value")
                    )
                )
                .stream()
                .filter(seed -> !existing.contains(seed.player()))
                .toList();
        }
        // Multi-row inserts avoid one database command per athlete. All clubs remain initialized.
        for (int offset = 0; offset < seeds.size(); offset += 500) {
            var chunk = seeds.subList(offset, Math.min(offset + 500, seeds.size()));
            var values = new StringJoiner(",");
            var params = new MapSqlParameterSource("id", id).addValue("start", date);
            for (int i = 0; i < chunk.size(); i++) {
                var seed = chunk.get(i);
                values.add("(:id,:player" + i + ",:team" + i + ",:salary" + i + ",:start,:end" + i + ")");
                params
                    .addValue("player" + i, seed.player())
                    .addValue("team" + i, seed.team(), java.sql.Types.BIGINT)
                    .addValue("salary" + i, FinanceRules.salary(seed.value()))
                    .addValue("end" + i, date.plusMonths(24 + (seed.player() % 25)));
            }
            db.update(
                "INSERT INTO player_contracts(save_id,player_id,team_id,salary_monthly,start_date,end_date) VALUES " +
                    values,
                params
            );
        }
        db.update(
            "INSERT INTO club_finance_accounts(save_id,team_id,last_processed_date) SELECT save_id,team_id,:date FROM save_teams t WHERE save_id=:id AND NOT EXISTS(SELECT 1 FROM club_finance_accounts a WHERE a.save_id=t.save_id AND a.team_id=t.team_id)",
            new MapSqlParameterSource("id", id).addValue("date", date.minusDays(1))
        );
    }

    private void revalueEstimates(String id) {
        var catalog = db.query(
            "SELECT id,ability,age,market_value FROM catalog_players WHERE valuation_version=0",
            new MapSqlParameterSource(),
            SEED
        );
        revalue(
            "UPDATE catalog_players SET market_value=:value,valuation_version=1 WHERE id=:player AND valuation_version=0",
            catalog,
            id
        );
        var players = db.query(
            "SELECT p.player_id,p.ability,p.age,p.market_value FROM save_players p WHERE p.save_id=:id AND p.valuation_version=0 AND NOT EXISTS(SELECT 1 FROM transfer_offers o WHERE o.save_id=p.save_id AND o.player_id=p.player_id AND o.status IN ('PENDING','NEGOTIABLE','CLUB_REVIEW'))",
            new MapSqlParameterSource("id", id),
            SEED
        );
        revalue(
            "UPDATE save_players SET market_value=:value,valuation_version=1 WHERE save_id=:id AND player_id=:player AND valuation_version=0",
            players,
            id
        );
    }

    private void revalue(String sql, List<Seed> rows, String id) {
        if (rows.isEmpty()) return;
        db.batchUpdate(
            sql,
            rows
                .stream()
                .map(s ->
                    new MapSqlParameterSource("id", id)
                        .addValue("player", s.id())
                        .addValue(
                            "value",
                            s.value() == 0 || s.value() == FinanceRules.legacyMarketValue(s.ability(), s.age())
                                ? FinanceRules.marketValue(s.ability(), s.age())
                                : s.value()
                        )
                )
                .toArray(MapSqlParameterSource[]::new)
        );
    }

    public long annualRevenue(String id, long team, String country, int division) {
        String name = db.queryForObject(
            "SELECT name FROM save_teams WHERE save_id=:id AND team_id=:team",
            params(id, team),
            String.class
        );
        return FinanceRules.annualRevenue(name, country, division, rating(id, team));
    }

    public void rebaseUnapproved(String id, long team, long season, long funding) {
        db.update(
            "UPDATE season_finances SET base_funding=:funding,funding_version=1 WHERE save_id=:id AND team_id=:team AND season_id=:season AND approved=FALSE AND funding_version=0",
            params(id, team).addValue("season", season).addValue("funding", funding)
        );
    }

    public Contract contract(String id, long player) {
        return db
            .query(
                CONTRACT_SELECT + "WHERE p.save_id=:id AND p.player_id=:player",
                new MapSqlParameterSource("id", id).addValue("player", player),
                CONTRACT
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public List<Contract> contracts(String id, long team) {
        return db.query(
            CONTRACT_SELECT + "WHERE p.save_id=:id AND p.team_id=:team ORDER BY p.ability DESC,p.name",
            params(id, team),
            CONTRACT
        );
    }

    public List<Contract> market(String id, String query) {
        return db.query(
            CONTRACT_SELECT +
                "WHERE p.save_id=:id AND (LOWER(p.name) LIKE :q OR LOWER(COALESCE(t.name,'')) LIKE :q) ORDER BY p.ability DESC,p.player_id LIMIT 60",
            new MapSqlParameterSource("id", id).addValue(
                "q",
                "%" + query.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%"
            ),
            CONTRACT
        );
    }

    public Account account(String id, long team) {
        return db.queryForObject(
            "SELECT * FROM club_finance_accounts WHERE save_id=:id AND team_id=:team",
            params(id, team),
            (r, n) ->
                new Account(
                    r.getObject("last_processed_date", LocalDate.class),
                    r.getLong("staff_salary_monthly"),
                    r.getInt("board_trust"),
                    r.getInt("fan_trust")
                )
        );
    }

    public long cash(String id, long team) {
        return db.queryForObject(
            "SELECT budget FROM save_teams WHERE save_id=:id AND team_id=:team",
            params(id, team),
            Long.class
        );
    }

    public int rating(String id, long team) {
        return db.queryForObject(
            "SELECT rating FROM save_teams WHERE save_id=:id AND team_id=:team",
            params(id, team),
            Integer.class
        );
    }

    public long teamForMember(String id, String member) {
        return db.queryForObject(
            "SELECT team_id FROM save_members WHERE save_id=:id AND id=:member",
            new MapSqlParameterSource("id", id).addValue("member", member),
            Long.class
        );
    }

    public boolean ready(String id, long team) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM save_members m JOIN game_saves g ON g.id=m.save_id WHERE m.save_id=:id AND m.team_id=:team AND m.ready=TRUE AND g.capacity>1 AND g.local_multiplayer=FALSE",
                params(id, team),
                Integer.class
            ) > 0
        );
    }

    public boolean live(String id) {
        return "LIVE".equals(
            db.queryForObject(
                "SELECT status FROM game_saves WHERE id=:id",
                new MapSqlParameterSource("id", id),
                String.class
            )
        );
    }

    public Long nextSeason(String id, long team) {
        return db
            .query(
                "SELECT season_id FROM national_fixtures WHERE save_id=:id AND (home_team_id=:team OR away_team_id=:team) AND status='SCHEDULED' ORDER BY match_date,id LIMIT 1",
                params(id, team),
                (r, n) -> r.getLong(1)
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public int expectedRank(String id, long team) {
        return db.queryForObject(
            "SELECT 1+COUNT(*) FROM save_teams t JOIN save_teams own ON own.save_id=t.save_id AND own.team_id=:team WHERE t.save_id=:id AND t.league_id=own.league_id AND (t.rating>own.rating OR t.rating=own.rating AND t.team_id<own.team_id)",
            params(id, team),
            Integer.class
        );
    }

    public Plan plan(String id, long team, long season) {
        return db
            .query(
                "SELECT * FROM season_finances WHERE save_id=:id AND team_id=:team AND season_id=:season",
                params(id, team).addValue("season", season),
                PLAN
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public void createPlan(String id, long team, long season, long funding, int target) {
        db.update(
            "INSERT INTO season_finances(save_id,team_id,season_id,base_funding,promised_rank,expected_rank,funding_version) VALUES(:id,:team,:season,:funding,:target,:expected,1)",
            params(id, team)
                .addValue("season", season)
                .addValue("funding", funding)
                .addValue("target", target)
                .addValue("expected", expectedRank(id, team))
        );
    }

    public void approve(String id, long team, SeasonBudgetRequest r) {
        db.update(
            "UPDATE season_finances SET approved=TRUE,promised_rank=:rank,expected_rank=COALESCE(expected_rank,:expected),transfer_budget=:transfer,salary_budget=:salary,infrastructure_budget=:infra,miscellaneous_budget=:misc,reserve_budget=:reserve WHERE save_id=:id AND team_id=:team AND season_id=:season AND approved=FALSE",
            params(id, team)
                .addValue("season", r.seasonId())
                .addValue("rank", r.promisedRank())
                .addValue("expected", expectedRank(id, team))
                .addValue("transfer", r.transferBudget())
                .addValue("salary", r.salaryBudget())
                .addValue("infra", r.infrastructureBudget())
                .addValue("misc", r.miscellaneousBudget())
                .addValue("reserve", r.reserveBudget())
        );
    }

    public long payroll(String id, long team) {
        return (
            db.queryForObject(
                "SELECT COALESCE(SUM(salary_monthly),0) FROM player_contracts WHERE save_id=:id AND team_id=:team",
                params(id, team),
                Long.class
            ) + account(id, team).staffSalaryMonthly()
        );
    }

    public long commitment(String id, long team) {
        return (
            contracts(id, team).stream().mapToLong(FinanceRules::commitment).sum() +
            account(id, team).staffSalaryMonthly()
        );
    }

    public long reservedFees(String id, long team, Long exclude) {
        return db.queryForObject(
            "SELECT COALESCE(SUM(fee+signing_bonus),0) FROM transfer_offers WHERE save_id=:id AND buyer_id=:team AND status IN ('CLUB_REVIEW','PENDING','NEGOTIABLE') AND (:exclude IS NULL OR id<>:exclude)",
            params(id, team).addValue("exclude", exclude, java.sql.Types.BIGINT),
            Long.class
        );
    }

    public long reservedWages(String id, long team, Long exclude) {
        return db.queryForObject(
            "SELECT COALESCE(SUM(GREATEST(0,o.salary_monthly+o.appearance_bonus*4+o.goal_bonus*2+(o.title_bonus+11)/12-CASE WHEN o.seller_id=o.buyer_id THEN c.salary_monthly+c.appearance_bonus*4+c.goal_bonus*2+(c.title_bonus+11)/12 ELSE 0 END)),0) FROM transfer_offers o JOIN player_contracts c ON c.save_id=o.save_id AND c.player_id=o.player_id WHERE o.save_id=:id AND o.buyer_id=:team AND o.status IN ('CLUB_REVIEW','PENDING','NEGOTIABLE') AND (:exclude IS NULL OR o.id<>:exclude)",
            params(id, team).addValue("exclude", exclude, java.sql.Types.BIGINT),
            Long.class
        );
    }

    public void transferSpent(String id, long team, long season, long amount) {
        db.update(
            "UPDATE season_finances SET transfer_spent=transfer_spent+:amount WHERE save_id=:id AND team_id=:team AND season_id=:season",
            params(id, team).addValue("season", season).addValue("amount", amount)
        );
    }

    public void saleAllowance(String id, long team, long amount) {
        db.update(
            "UPDATE season_finances SET transfer_budget=transfer_budget+:amount WHERE save_id=:id AND team_id=:team AND approved=TRUE AND settled=FALSE",
            params(id, team).addValue("amount", (amount * 80) / 100)
        );
    }

    public boolean ledger(
        String id,
        long team,
        LocalDate date,
        String category,
        String description,
        long amount,
        String key
    ) {
        var p = params(id, team)
            .addValue("date", date)
            .addValue("category", category)
            .addValue("description", description)
            .addValue("amount", amount)
            .addValue("key", key);
        // Callers hold the league row lock. Read explicitly for portable H2/PostgreSQL idempotency.
        if (
            db.queryForObject(
                "SELECT COUNT(*) FROM finance_ledger WHERE save_id=:id AND team_id=:team AND entry_key=:key",
                p,
                Integer.class
            ) > 0
        ) return false;
        int inserted = db.update(
            "INSERT INTO finance_ledger(save_id,team_id,event_date,category,description,amount,entry_key) VALUES(:id,:team,:date,:category,:description,:amount,:key)",
            p
        );
        if (inserted > 0) db.update(
            "UPDATE save_teams SET budget=budget+:amount WHERE save_id=:id AND team_id=:team",
            p
        );
        return inserted > 0;
    }

    public List<Ledger> ledger(String id, long team) {
        return db.query(
            "SELECT * FROM finance_ledger WHERE save_id=:id AND team_id=:team ORDER BY event_date DESC,id DESC LIMIT 60",
            params(id, team),
            (r, n) ->
                new Ledger(
                    r.getLong("id"),
                    r.getObject("event_date", LocalDate.class),
                    r.getString("category"),
                    r.getString("description"),
                    r.getLong("amount")
                )
        );
    }

    public List<History> history(String id, long team) {
        return db.query(
            "SELECT s.label,f.promised_rank,f.final_rank,f.promise_met FROM season_finances f JOIN national_seasons s ON s.id=f.season_id WHERE f.save_id=:id AND f.team_id=:team AND f.settled=TRUE ORDER BY s.first_date DESC",
            params(id, team),
            (r, n) -> new History(r.getString(1), r.getInt(2), r.getInt(3), r.getBoolean(4))
        );
    }

    public List<Plan> unfinishedPlans(String id, long team) {
        return db.query(
            "SELECT * FROM season_finances WHERE save_id=:id AND team_id=:team AND approved=TRUE AND settled=FALSE",
            params(id, team),
            PLAN
        );
    }

    public Integer finalRank(String id, long team, long season) {
        var p = params(id, team).addValue("season", season);
        if (
            db.queryForObject(
                "SELECT COUNT(*) FROM national_fixtures WHERE save_id=:id AND season_id=:season AND status<>'PLAYED'",
                p,
                Integer.class
            ) > 0
        ) return null;
        var rows = db.query(
            "SELECT home_team_id,away_team_id,home_goals,away_goals FROM national_fixtures WHERE save_id=:id AND season_id=:season",
            p,
            (r, n) -> new long[] { r.getLong(1), r.getLong(2), r.getInt(3), r.getInt(4) }
        );
        if (rows.isEmpty()) return null;
        var totals = new HashMap<Long, long[]>();
        for (var f : rows) {
            var h = totals.computeIfAbsent(f[0], k -> new long[3]);
            var a = totals.computeIfAbsent(f[1], k -> new long[3]);
            h[0] += f[2] > f[3] ? 3 : f[2] == f[3] ? 1 : 0;
            a[0] += f[3] > f[2] ? 3 : f[2] == f[3] ? 1 : 0;
            h[1] += f[2] - f[3];
            a[1] += f[3] - f[2];
            h[2] += f[2];
            a[2] += f[3];
        }
        var sorted = totals
            .keySet()
            .stream()
            .sorted((a, b) -> {
                for (int i = 0; i < 3; i++) {
                    int c = Long.compare(totals.get(b)[i], totals.get(a)[i]);
                    if (c != 0) return c;
                }
                return Long.compare(a, b);
            })
            .toList();
        int rank = sorted.indexOf(team);
        return rank < 0 ? null : rank + 1;
    }

    public void settle(String id, long team, Plan plan, int rank) {
        boolean met = rank <= plan.promisedRank();
        int updated = db.update(
            "UPDATE season_finances SET settled=TRUE,final_rank=:rank,promise_met=:met WHERE id=:plan AND settled=FALSE",
            new MapSqlParameterSource("plan", plan.id()).addValue("rank", rank).addValue("met", met)
        );
        if (updated == 0) return;
        int teams = db.queryForObject(
            "SELECT team_count FROM national_seasons WHERE id=:season",
            new MapSqlParameterSource("season", plan.seasonId()),
            Integer.class
        );
        var reaction = FinanceRules.seasonReaction(
            rank,
            plan.promisedRank(),
            plan.expectedRank() == null ? expectedRank(id, team) : plan.expectedRank(),
            teams
        );
        adjustTrust(id, team, reaction.board(), reaction.fans());
    }

    public void adjustTrust(String id, long team, int board, int fans) {
        db.update(
            "UPDATE club_finance_accounts SET board_trust=LEAST(100,GREATEST(0,board_trust+:board)),fan_trust=LEAST(100,GREATEST(0,fan_trust+:fans)) WHERE save_id=:id AND team_id=:team",
            params(id, team).addValue("board", board).addValue("fans", fans)
        );
    }

    public void processed(String id, long team, LocalDate date) {
        db.update(
            "UPDATE club_finance_accounts SET last_processed_date=:date WHERE save_id=:id AND team_id=:team",
            params(id, team).addValue("date", date)
        );
    }

    public void touchTeam(String id, long team) {
        var p = params(id, team);
        if (
            db.queryForObject(
                "SELECT COUNT(*) FROM save_team_management WHERE save_id=:id AND team_id=:team",
                p,
                Integer.class
            ) == 0
        ) db.update(
            "INSERT INTO save_team_management(save_id,team_id,formation,mentality,version) VALUES(:id,:team,'4-3-3','BALANCED',0)",
            p
        );
        db.update("UPDATE save_team_management SET version=version+1 WHERE save_id=:id AND team_id=:team", p);
    }

    public int version(String id, long team) {
        return db.queryForObject(
            "SELECT COALESCE(MAX(version),0) FROM save_team_management WHERE save_id=:id AND team_id=:team",
            params(id, team),
            Integer.class
        );
    }

    public void replaceLineup(String id, long team, long player) {
        var p = params(id, team).addValue("player", player);
        var replacements = db.query(
            "SELECT p.player_id FROM save_players p WHERE p.save_id=:id AND p.team_id=:team AND p.player_id<>:player AND NOT EXISTS(SELECT 1 FROM save_lineups l WHERE l.save_id=p.save_id AND l.team_id=p.team_id AND l.player_id=p.player_id) ORDER BY CASE WHEN p.position=(SELECT position FROM save_players WHERE save_id=:id AND player_id=:player) THEN 0 ELSE 1 END,p.ability DESC,p.player_id LIMIT 1",
            p,
            (r, n) -> r.getLong(1)
        );
        if (replacements.isEmpty()) db.update(
            "DELETE FROM save_lineups WHERE save_id=:id AND team_id=:team AND player_id=:player",
            p
        );
        else db.update(
            "UPDATE save_lineups SET player_id=:replacement WHERE save_id=:id AND team_id=:team AND player_id=:player",
            p.addValue("replacement", replacements.getFirst())
        );
    }

    public void release(String id, Contract c) {
        if (c.teamId() == null) return;
        replaceLineup(id, c.teamId(), c.playerId());
        var p = new MapSqlParameterSource("id", id).addValue("player", c.playerId());
        db.update("UPDATE save_players SET team_id=NULL WHERE save_id=:id AND player_id=:player", p);
        db.update(
            "UPDATE player_contracts SET team_id=NULL,salary_monthly=0 WHERE save_id=:id AND player_id=:player",
            p
        );
        touchTeam(id, c.teamId());
    }

    public void recalculateRating(String id, long team) {
        db.update(
            "UPDATE save_teams SET rating=COALESCE((SELECT CAST(ROUND(AVG(ability)) AS INTEGER) FROM (SELECT ability FROM save_players WHERE save_id=:id AND team_id=:team ORDER BY ability DESC LIMIT 18) squad),rating) WHERE save_id=:id AND team_id=:team",
            params(id, team)
        );
    }

    public int squadSize(String id, long team) {
        return db.queryForObject(
            "SELECT COUNT(*) FROM save_players WHERE save_id=:id AND team_id=:team",
            params(id, team),
            Integer.class
        );
    }

    public boolean humanTeam(String id, long team) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM save_members WHERE save_id=:id AND team_id=:team",
                params(id, team),
                Integer.class
            ) > 0
        );
    }

    public void renewComputerContract(String id, Contract contract, LocalDate date) {
        db.update(
            "UPDATE player_contracts SET salary_monthly=:salary,start_date=:start,end_date=:end,signing_bonus=0,estimated=TRUE WHERE save_id=:id AND player_id=:player",
            new MapSqlParameterSource("id", id)
                .addValue("player", contract.playerId())
                .addValue("salary", Math.max(contract.salaryMonthly(), contract.expectedSalary()))
                .addValue("start", date)
                .addValue("end", date.plusMonths(24))
        );
    }

    public void sign(String id, long buyer, Contract old, TransferOfferRequest terms, LocalDate date) {
        if (old.teamId() != null && old.teamId() != buyer) replaceLineup(id, old.teamId(), old.playerId());
        var p = params(id, buyer)
            .addValue("player", old.playerId())
            .addValue("salary", terms.salaryMonthly())
            .addValue("start", date)
            .addValue("end", date.plusMonths(terms.months()))
            .addValue("signing", terms.signingBonus())
            .addValue("appearance", terms.appearanceBonus())
            .addValue("goal", terms.goalBonus())
            .addValue("title", terms.titleBonus());
        db.update("UPDATE save_players SET team_id=:team WHERE save_id=:id AND player_id=:player", p);
        db.update(
            "UPDATE player_contracts SET team_id=:team,salary_monthly=:salary,start_date=:start,end_date=:end,signing_bonus=:signing,appearance_bonus=:appearance,goal_bonus=:goal,title_bonus=:title,estimated=FALSE WHERE save_id=:id AND player_id=:player",
            p
        );
        touchTeam(id, buyer);
        if (!Objects.equals(old.teamId(), buyer)) recalculateRating(id, buyer);
        if (old.teamId() != null && old.teamId() != buyer) {
            touchTeam(id, old.teamId());
            recalculateRating(id, old.teamId());
        }
    }
}
