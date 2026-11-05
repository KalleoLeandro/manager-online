package br.com.manager.repository;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.utils.TournamentRulebooks.*;
import br.com.manager.utils.NationalLeagueDraw;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class TournamentRepository {

    private final NamedParameterJdbcTemplate db;
    private final JsonMapper json = JsonMapper.builder().build();

    public TournamentRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private final RowMapper<Edition> EDITION = (r, n) ->
        new Edition(
            r.getLong("id"),
            r.getString("competition_key"),
            r.getInt("season_year"),
            r.getString("label"),
            r.getString("name"),
            r.getString("country"),
            r.getString("status"),
            r.getInt("current_stage"),
            r.getObject("champion_id", Long.class),
            r.getObject("runner_up_id", Long.class),
            json.readValue(r.getString("rulebook"), Rulebook.class)
        );
    private static final RowMapper<Entrant> ENTRANT = (r, n) ->
        new Entrant(
            r.getLong("team_id"),
            r.getString("name"),
            r.getString("entry_stage"),
            r.getInt("seed_rank"),
            r.getString("association"),
            r.getString("region"),
            r.getInt("division"),
            r.getInt("rating")
        );

    public List<Entrant> seeds(String id, String key, int year) {
        return db.query(
            "SELECT e.*,t.name,t.rating,COALESCE(l.division,9) AS division FROM tournament_entries e JOIN save_teams t ON t.save_id=:id AND t.team_id=e.team_id LEFT JOIN catalog_leagues l ON l.id=t.league_id WHERE e.competition_key=:key AND e.season_year=:year ORDER BY e.seed_rank,e.team_id",
            new MapSqlParameterSource("id", id).addValue("key", key).addValue("year", year),
            ENTRANT
        );
    }

    public List<Edition> editions(String id) {
        return db.query(
            "SELECT * FROM tournament_editions WHERE save_id=:id ORDER BY season_year DESC,country,name",
            new MapSqlParameterSource("id", id),
            EDITION
        );
    }

    public Edition create(String id, Rulebook rules, List<Entrant> teams) {
        var p = new MapSqlParameterSource("id", id)
            .addValue("key", rules.key())
            .addValue("year", rules.startYear())
            .addValue(
                "label",
                rules.country().equals("Brasil")
                    ? String.valueOf(rules.startYear())
                    : rules.startYear() + "/" + (rules.startYear() + 1)
            )
            .addValue("name", rules.name())
            .addValue("country", rules.country())
            .addValue("rules", json.writeValueAsString(rules));
        db.update(
            "INSERT INTO tournament_editions(save_id,competition_key,season_year,label,name,country,rulebook) VALUES(:id,:key,:year,:label,:name,:country,:rules)",
            p
        );
        var edition = db.queryForObject(
            "SELECT * FROM tournament_editions WHERE save_id=:id AND competition_key=:key AND season_year=:year",
            p,
            EDITION
        );
        db.batchUpdate(
            "INSERT INTO tournament_teams(save_id,edition_id,team_id,entry_stage,seed_rank,association,region) VALUES(:id,:edition,:team,:stage,:rank,:association,:region)",
            teams
                .stream()
                .map(t ->
                    new MapSqlParameterSource("id", id)
                        .addValue("edition", edition.id())
                        .addValue("team", t.id())
                        .addValue("stage", t.stage())
                        .addValue("rank", t.rank())
                        .addValue("association", t.association())
                        .addValue("region", t.region())
                )
                .toArray(MapSqlParameterSource[]::new)
        );
        return edition;
    }

    public List<Entrant> teams(String id, long edition) {
        return db.query(
            "SELECT e.*,t.name,t.rating,COALESCE(l.division,9) AS division FROM tournament_teams e JOIN save_teams t ON t.save_id=e.save_id AND t.team_id=e.team_id LEFT JOIN catalog_leagues l ON l.id=t.league_id WHERE e.save_id=:id AND e.edition_id=:edition ORDER BY e.seed_rank,e.team_id",
            new MapSqlParameterSource("id", id).addValue("edition", edition),
            ENTRANT
        );
    }

    public boolean stageExists(long edition, int stage) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM national_seasons WHERE tournament_id=:edition AND tournament_stage=:stage",
                new MapSqlParameterSource("edition", edition).addValue("stage", stage),
                Integer.class
            ) > 0
        );
    }

    public List<Tie> ties(long edition, int stage) {
        return db.query(
            "SELECT * FROM tournament_ties WHERE edition_id=:edition AND stage_index=:stage ORDER BY tie_number",
            new MapSqlParameterSource("edition", edition).addValue("stage", stage),
            (r, n) ->
                new Tie(
                    r.getInt("stage_index"),
                    r.getInt("tie_number"),
                    r.getLong("home_team_id"),
                    r.getLong("away_team_id"),
                    r.getObject("winner_id", Long.class)
                )
        );
    }

    public long createStage(String id, Edition edition, int index, List<Tie> ties) {
        var stage = edition.rules().stages().get(index);
        long virtualLeague = -(edition.id() * 1000 + index + 1);
        var p = new MapSqlParameterSource("id", id)
            .addValue("league", virtualLeague)
            .addValue("year", edition.year())
            .addValue("label", edition.label())
            .addValue("name", edition.name() + " · " + stage.label())
            .addValue("country", edition.country())
            .addValue("teams", ties.size() * 2)
            .addValue("rounds", stage.legs())
            .addValue("first", stage.firstDate())
            .addValue("last", stage.secondDate() == null ? stage.firstDate().plusDays(1) : stage.secondDate())
            .addValue("seed", virtualLeague)
            .addValue("edition", edition.id())
            .addValue("stage", index);
        db.update(
            "INSERT INTO national_seasons(save_id,league_id,season_year,label,competition_name,country,division,team_count,rounds,first_date,last_date,draw_seed,format,tournament_id,tournament_stage) VALUES(:id,:league,:year,:label,:name,:country,0,:teams,:rounds,:first,:last,:seed,'KNOCKOUT',:edition,:stage)",
            p
        );
        long season = db.queryForObject(
            "SELECT id FROM national_seasons WHERE tournament_id=:edition AND tournament_stage=:stage",
            p,
            Long.class
        );
        var clubIds = ties
            .stream()
            .flatMap(t -> java.util.stream.Stream.of(t.home(), t.away()))
            .toList();
        db.update(
            "INSERT INTO national_season_teams(save_id,season_id,team_id,team_name) SELECT :id,:season,team_id,name FROM save_teams WHERE save_id=:id AND team_id IN(:teams)",
            new MapSqlParameterSource("id", id).addValue("season", season).addValue("teams", clubIds)
        );
        var fixtures = new ArrayList<MapSqlParameterSource>();
        for (var tie : ties) {
            db.update(
                "INSERT INTO tournament_ties(edition_id,stage_index,tie_number,home_team_id,away_team_id) VALUES(:edition,:stage,:tie,:home,:away)",
                new MapSqlParameterSource("edition", edition.id())
                    .addValue("stage", index)
                    .addValue("tie", tie.number())
                    .addValue("home", tie.home())
                    .addValue("away", tie.away())
            );
            for (int leg = 1; leg <= stage.legs(); leg++) fixtures.add(
                new MapSqlParameterSource("id", id)
                    .addValue("season", season)
                    .addValue("round", leg)
                    .addValue("date", leg == 1 ? stage.firstDate() : stage.secondDate())
                    .addValue("home", leg == 1 ? tie.home() : tie.away())
                    .addValue("away", leg == 1 ? tie.away() : tie.home())
                    .addValue("tie", tie.number())
                    .addValue("leg", leg)
            );
        }
        db.batchUpdate(
            "INSERT INTO national_fixtures(save_id,season_id,round_number,match_date,home_team_id,away_team_id,tie_number,leg_number) VALUES(:id,:season,:round,:date,:home,:away,:tie,:leg)",
            fixtures.toArray(MapSqlParameterSource[]::new)
        );
        db.update("UPDATE tournament_editions SET current_stage=:stage WHERE id=:edition", p);
        return season;
    }

    public List<Game> games(String id, long edition) {
        return db.query(
            "SELECT f.*,s.tournament_stage,h.rating AS hr,a.rating AS ar FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id JOIN save_teams h ON h.save_id=f.save_id AND h.team_id=f.home_team_id JOIN save_teams a ON a.save_id=f.save_id AND a.team_id=f.away_team_id WHERE f.save_id=:id AND s.tournament_id=:edition ORDER BY f.match_date,f.id",
            new MapSqlParameterSource("id", id).addValue("edition", edition),
            (r, n) ->
                new Game(
                    r.getLong("id"),
                    edition,
                    r.getInt("tournament_stage"),
                    r.getInt("tie_number"),
                    r.getInt("leg_number"),
                    r.getObject("match_date", LocalDate.class),
                    r.getLong("home_team_id"),
                    r.getLong("away_team_id"),
                    r.getInt("hr"),
                    r.getInt("ar"),
                    r.getObject("home_goals", Integer.class),
                    r.getObject("away_goals", Integer.class),
                    r.getString("status")
                )
        );
    }

    public void resolve(long edition, int stage, int tie, long winner) {
        db.update(
            "UPDATE tournament_ties SET winner_id=:winner WHERE edition_id=:edition AND stage_index=:stage AND tie_number=:tie AND winner_id IS NULL",
            new MapSqlParameterSource("edition", edition)
                .addValue("stage", stage)
                .addValue("tie", tie)
                .addValue("winner", winner)
        );
    }

    public void advanceTeam(long edition,int stage,long team,int seed,String group){
        var p=new MapSqlParameterSource("edition",edition).addValue("stage",stage).addValue("team",team).addValue("seed",seed).addValue("group",group);
        db.update("INSERT INTO tournament_advancements(edition_id,stage_index,team_id,seed_rank,source_group) SELECT :edition,:stage,:team,:seed,:group WHERE NOT EXISTS(SELECT 1 FROM tournament_advancements WHERE edition_id=:edition AND stage_index=:stage AND team_id=:team)",p);
    }
    public List<Entrant> stageTeams(String id,Edition edition,int stage){
        String key=edition.rules().stages().get(stage).key();
        return db.query("SELECT t.team_id,t.name,t.rating,e.entry_stage,COALESCE(a.seed_rank,e.seed_rank) AS seed_rank,e.association,COALESCE(a.source_group,e.region) AS region,COALESCE(l.division,9) AS division FROM tournament_teams e JOIN save_teams t ON t.save_id=e.save_id AND t.team_id=e.team_id LEFT JOIN catalog_leagues l ON l.id=t.league_id LEFT JOIN tournament_advancements a ON a.edition_id=e.edition_id AND a.team_id=e.team_id AND a.stage_index=:stage WHERE e.save_id=:id AND e.edition_id=:edition AND (e.entry_stage=:key OR a.team_id IS NOT NULL) ORDER BY COALESCE(a.seed_rank,e.seed_rank),t.team_id",new MapSqlParameterSource("id",id).addValue("edition",edition.id()).addValue("stage",stage).addValue("key",key),ENTRANT);
    }
    public long leagueStage(String id,Edition edition,int index,List<Entrant> entrants,List<NationalLeagueDraw.Fixture> fixtures,Map<Long,String> groups){
        var stage=edition.rules().stages().get(index);long virtualLeague=-(edition.id()*1000+index+1);
        var p=new MapSqlParameterSource("id",id).addValue("league",virtualLeague).addValue("year",edition.year()).addValue("label",edition.label()).addValue("name",edition.name()+" · "+stage.label()).addValue("country",edition.country()).addValue("teams",entrants.size()).addValue("rounds",stage.matchDates().size()).addValue("first",stage.firstDate()).addValue("last",stage.secondDate()).addValue("seed",virtualLeague).addValue("edition",edition.id()).addValue("stage",index).addValue("format",stage.format());
        db.update("INSERT INTO national_seasons(save_id,league_id,season_year,label,competition_name,country,division,team_count,rounds,first_date,last_date,draw_seed,format,tournament_id,tournament_stage) VALUES(:id,:league,:year,:label,:name,:country,0,:teams,:rounds,:first,:last,:seed,:format,:edition,:stage)",p);
        long season=db.queryForObject("SELECT id FROM national_seasons WHERE tournament_id=:edition AND tournament_stage=:stage",p,Long.class);
        db.batchUpdate("INSERT INTO national_season_teams(save_id,season_id,team_id,team_name,group_name) VALUES(:id,:season,:team,:name,:group)",entrants.stream().map(t->new MapSqlParameterSource("id",id).addValue("season",season).addValue("team",t.id()).addValue("name",t.name()).addValue("group",groups.get(t.id()))).toArray(MapSqlParameterSource[]::new));
        db.batchUpdate("INSERT INTO national_fixtures(save_id,season_id,round_number,match_date,home_team_id,away_team_id,leg_number) VALUES(:id,:season,:round,:date,:home,:away,1)",fixtures.stream().map(f->new MapSqlParameterSource("id",id).addValue("season",season).addValue("round",f.round()).addValue("date",f.date()).addValue("home",f.homeTeamId()).addValue("away",f.awayTeamId())).toArray(MapSqlParameterSource[]::new));
        return season;
    }
    public Map<Long,String> stageGroups(long edition,int stage){
        var rows=db.query("SELECT t.team_id,t.group_name FROM national_season_teams t JOIN national_seasons s ON s.id=t.season_id WHERE s.tournament_id=:edition AND s.tournament_stage=:stage",new MapSqlParameterSource("edition",edition).addValue("stage",stage),(r,n)->new AbstractMap.SimpleEntry<>(r.getLong(1),r.getString(2)));
        var result=new HashMap<Long,String>();rows.forEach(row->result.put(row.getKey(),row.getValue()));return result;
    }

    public void champion(long edition, long winner, long runner) {
        db.update(
            "UPDATE tournament_editions SET status='FINISHED',champion_id=:winner,runner_up_id=:runner WHERE id=:edition AND status='ACTIVE'",
            new MapSqlParameterSource("edition", edition).addValue("winner", winner).addValue("runner", runner)
        );
    }

    public void nextStage(long edition, int stage) {
        db.update(
            "UPDATE tournament_editions SET current_stage=:stage WHERE id=:edition",
            new MapSqlParameterSource("edition", edition).addValue("stage", stage)
        );
    }

    public Optional<Edition> forFixture(String id, long fixture) {
        return db
            .query(
                "SELECT e.* FROM tournament_editions e JOIN national_seasons s ON s.tournament_id=e.id JOIN national_fixtures f ON f.season_id=s.id WHERE f.save_id=:id AND f.id=:fixture",
                new MapSqlParameterSource("id", id).addValue("fixture", fixture),
                EDITION
            )
            .stream()
            .findFirst();
    }

    public long disciplineSeason(long edition) {
        return db.queryForObject(
            "SELECT MIN(id) FROM national_seasons WHERE tournament_id=:edition",
            new MapSqlParameterSource("edition", edition),
            Long.class
        );
    }

    public boolean human(String id, long team) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM save_members WHERE save_id=:id AND team_id=:team",
                new MapSqlParameterSource("id", id).addValue("team", team),
                Integer.class
            ) > 0
        );
    }

    public void result(String id, Game game, int home, int away, Integer hp, Integer ap) {
        db.update(
            "UPDATE national_fixtures SET status='PLAYED',home_goals=:home,away_goals=:away,home_penalties=:hp,away_penalties=:ap WHERE save_id=:id AND id=:fixture AND status='SCHEDULED'",
            new MapSqlParameterSource("id", id)
                .addValue("fixture", game.id())
                .addValue("home", home)
                .addValue("away", away)
                .addValue("hp", hp)
                .addValue("ap", ap)
        );
    }

    public Integer penalties(long fixture, boolean home) {
        return db.queryForObject(
            "SELECT " + (home ? "home_penalties" : "away_penalties") + " FROM national_fixtures WHERE id=:fixture",
            new MapSqlParameterSource("fixture", fixture),
            (r, n) -> r.getObject(1, Integer.class)
        );
    }

    public void qualify(
        String id,
        String target,
        int year,
        long team,
        String source,
        int sourceYear,
        String reason,
        LocalDate date
    ) {
        var p = new MapSqlParameterSource("id", id)
            .addValue("target", target)
            .addValue("year", year)
            .addValue("team", team)
            .addValue("source", source)
            .addValue("sourceYear", sourceYear)
            .addValue("reason", reason)
            .addValue("date", date);
        db.update(
            "INSERT INTO competition_qualifications(save_id,target_key,target_year,team_id,source_key,source_year,qualification_type,confirmed_date) SELECT :id,:target,:year,:team,:source,:sourceYear,:reason,:date WHERE NOT EXISTS(SELECT 1 FROM competition_qualifications WHERE save_id=:id AND target_key=:target AND target_year=:year AND team_id=:team AND source_key=:source AND source_year=:sourceYear AND qualification_type=:reason)",
            p
        );
    }
}
