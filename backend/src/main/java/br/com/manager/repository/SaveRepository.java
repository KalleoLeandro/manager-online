package br.com.manager.repository;

import br.com.manager.models.dto.LeagueMemberDto;
import br.com.manager.models.dto.LeagueRecordDto;
import br.com.manager.models.dto.MembershipDto;
import br.com.manager.models.dto.TeamSelectionDto;
import br.com.manager.models.enums.PlayerAttribute;
import br.com.manager.utils.AssetUrls;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SaveRepository {

    private static final RowMapper<LeagueMemberDto> LEAGUE_MEMBER_ROW_MAPPER = (r, n) ->
        new LeagueMemberDto(
            r.getString("id"),
            r.getString("name"),
            r.getLong("team_id"),
            r.getString("team_name"),
            AssetUrls.crest(r.getLong("team_id")),
            r.getBoolean("owner"),
            r.getBoolean("ready")
        );

    private static final RowMapper<LeagueRecordDto> LEAGUE_RECORD_ROW_MAPPER = (r, n) ->
        new LeagueRecordDto(
            r.getString("id"),
            r.getString("name"),
            r.getString("invite_code"),
            r.getInt("capacity"),
            r.getString("status"),
            r.getString("catalog_version"),
            r.getInt("round_number"),
            r.getTimestamp("last_activity_at").toInstant()
        );

    private static final RowMapper<MembershipDto> MEMBERSHIP_ROW_MAPPER = (r, n) ->
        new MembershipDto(r.getString("save_id"), r.getString("id"));

    private static final RowMapper<TeamSelectionDto> TEAM_SELECTION_ROW_MAPPER = (r, n) ->
        new TeamSelectionDto(r.getLong("team_id"), r.getString("name"));

    private final NamedParameterJdbcTemplate db;

    public SaveRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public void attachAccount(String memberId, String managerId) {
        db.update(
            "UPDATE save_members SET manager_id=:managerId WHERE id=:memberId",
            new MapSqlParameterSource().addValue("memberId", memberId).addValue("managerId", managerId)
        );
    }

    public List<String> memberIdsForAccount(String saveId, String managerId) {
        return db.query(
            "SELECT id FROM save_members WHERE save_id=:saveId AND manager_id=:managerId",
            new MapSqlParameterSource().addValue("saveId", saveId).addValue("managerId", managerId),
            new SingleColumnRowMapper<>(String.class)
        );
    }

    public List<MembershipDto> memberships(String managerId) {
        return db.query(
            "SELECT m.save_id,m.id FROM save_members m JOIN game_saves s ON s.id=m.save_id WHERE m.manager_id=:managerId ORDER BY s.created_at DESC,s.id",
            new MapSqlParameterSource("managerId", managerId),
            MEMBERSHIP_ROW_MAPPER
        );
    }

    public boolean codeExists(String code) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM game_saves WHERE invite_code=:code",
                new MapSqlParameterSource("code", code),
                new SingleColumnRowMapper<>(Integer.class)
            ) > 0
        );
    }

    public void create(String id, String name, String code, int capacity, String version, Instant activity) {
        db.update(
            "INSERT INTO game_saves (id,name,invite_code,capacity,catalog_version,last_activity_at) VALUES (:id,:name,:code,:capacity,:version,:activity)",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("code", code)
                .addValue("capacity", capacity)
                .addValue("version", version)
                .addValue("activity", Timestamp.from(activity))
        );
    }

    public void snapshotCatalog(String id) {
        var params = new MapSqlParameterSource("saveId", id);
        db.update(
            """
            INSERT INTO save_teams (save_id,team_id,league_id,name,short_name,abbreviation,crest_url,rating,selectable)
            SELECT :saveId,id,league_id,name,short_name,abbreviation,crest_url,rating,selectable
            FROM catalog_teams
            """,
            params
        );
        db.update(
            """
            INSERT INTO save_players (save_id,player_id,team_id,name,position,level,ability,attribute1,value1,attribute2,value2,age,shirt_number,photo_url,nationality,generated,generation_origin)
            SELECT :saveId,id,team_id,name,position,level,ability,attribute1,value1,attribute2,value2,age,shirt_number,photo_url,nationality,generated,generation_origin
            FROM catalog_players
            """,
            params
        );
        db.update(
            "INSERT INTO save_player_profiles(save_id,player_id,reference_ability,potential,source_mask,source_name,source_url,origin," +
                PlayerAttribute.columns() +
                ") SELECT :saveId,pr.player_id,pr.reference_ability,pr.potential,pr.source_mask,pr.source_name,pr.source_url,'SNAPSHOT'," +
                PlayerAttribute.selection("pr") +
                " FROM catalog_player_profiles pr JOIN save_players p ON p.save_id=:saveId AND p.player_id=pr.player_id",
            params
        );
    }

    public boolean selectableTeam(String id, Long teamId) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM save_teams WHERE save_id=:saveId AND team_id=:teamId AND selectable=TRUE",
                new MapSqlParameterSource().addValue("saveId", id).addValue("teamId", teamId),
                new SingleColumnRowMapper<>(Integer.class)
            ) > 0
        );
    }

    public void addMember(String id, String saveId, String name, Long teamId, boolean owner, String tokenHash) {
        db.update(
            "INSERT INTO save_members (id,save_id,name,team_id,owner,token_hash) VALUES (:id,:saveId,:name,:teamId,:owner,:tokenHash)",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("saveId", saveId)
                .addValue("name", name)
                .addValue("teamId", teamId)
                .addValue("owner", owner)
                .addValue("tokenHash", tokenHash)
        );
    }

    public Optional<String> idByCode(String code) {
        return db
            .query(
                "SELECT id FROM game_saves WHERE invite_code=:code",
                new MapSqlParameterSource("code", code),
                new SingleColumnRowMapper<>(String.class)
            )
            .stream()
            .findFirst();
    }

    public boolean owner(String memberId) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT owner FROM save_members WHERE id=:memberId",
                new MapSqlParameterSource("memberId", memberId),
                new SingleColumnRowMapper<>(Boolean.class)
            )
        );
    }

    public void setReady(String memberId, boolean ready) {
        db.update(
            "UPDATE save_members SET ready=:ready WHERE id=:memberId",
            new MapSqlParameterSource().addValue("memberId", memberId).addValue("ready", ready)
        );
    }

    public void setStatus(String id, String status) {
        db.update(
            "UPDATE game_saves SET status=:status WHERE id=:saveId",
            new MapSqlParameterSource().addValue("saveId", id).addValue("status", status)
        );
    }

    public List<String> memberIdsByToken(String id, String hash) {
        return db.query(
            "SELECT id FROM save_members WHERE save_id=:saveId AND token_hash=:tokenHash",
            new MapSqlParameterSource().addValue("saveId", id).addValue("tokenHash", hash),
            new SingleColumnRowMapper<>(String.class)
        );
    }

    public boolean lock(String id) {
        return !db
            .query(
                "SELECT id FROM game_saves WHERE id=:saveId FOR UPDATE",
                new MapSqlParameterSource("saveId", id),
                new SingleColumnRowMapper<>(String.class)
            )
            .isEmpty();
    }

    public List<LeagueMemberDto> members(String id) {
        return db.query(
            """
            SELECT m.id,m.name,m.team_id,t.name AS team_name,m.owner,m.ready
            FROM save_members m
            JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id
            WHERE m.save_id=:saveId
            ORDER BY m.owner DESC,m.joined_at,m.id
            """,
            new MapSqlParameterSource("saveId", id),
            LEAGUE_MEMBER_ROW_MAPPER
        );
    }

    public Optional<LeagueRecordDto> find(String id) {
        return db
            .query(
                "SELECT * FROM game_saves WHERE id=:saveId",
                new MapSqlParameterSource("saveId", id),
                LEAGUE_RECORD_ROW_MAPPER
            )
            .stream()
            .findFirst();
    }

    public Map<Long, String> selectionTeams(String id) {
        var rows = db.query(
            "SELECT team_id,name FROM save_teams WHERE save_id=:saveId AND selectable=TRUE",
            new MapSqlParameterSource("saveId", id),
            TEAM_SELECTION_ROW_MAPPER
        );
        var result = new HashMap<Long, String>();
        for (var row : rows) result.put(row.teamId(), row.name());
        return result;
    }
}
