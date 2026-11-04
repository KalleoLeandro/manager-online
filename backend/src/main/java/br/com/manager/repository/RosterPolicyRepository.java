package br.com.manager.repository;

import br.com.manager.models.dto.PolicyLeagueDto;
import br.com.manager.models.dto.RosterPolicyDto;
import br.com.manager.utils.RosterPolicyKeys;
import java.sql.Date;
import java.sql.Types;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RosterPolicyRepository {

    private static final RowMapper<PolicyLeagueDto> POLICY_LEAGUE_ROW_MAPPER = (r, n) ->
        new PolicyLeagueDto(r.getLong("policy_id"), r.getLong("league_id"));

    private final NamedParameterJdbcTemplate db;

    public RosterPolicyRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public List<RosterPolicyDto> catalog() {
        var params = new MapSqlParameterSource();
        var links = db.query(
            "SELECT policy_id,league_id FROM catalog_roster_policy_leagues ORDER BY policy_id,league_id",
            params,
            POLICY_LEAGUE_ROW_MAPPER
        );
        return db.query(
            "SELECT * FROM catalog_roster_policies ORDER BY id",
            params,
            rosterPolicyMapper("id", group(links))
        );
    }

    public List<RosterPolicyDto> forSave(String id) {
        var params = new MapSqlParameterSource("saveId", id);
        var links = db.query(
            "SELECT policy_id,league_id FROM save_roster_policy_leagues WHERE save_id=:saveId ORDER BY policy_id,league_id",
            params,
            POLICY_LEAGUE_ROW_MAPPER
        );
        return db.query(
            "SELECT * FROM save_roster_policies WHERE save_id=:saveId ORDER BY policy_id",
            params,
            rosterPolicyMapper("policy_id", group(links))
        );
    }

    private Map<Long, List<Long>> group(List<PolicyLeagueDto> links) {
        return links
            .stream()
            .collect(
                Collectors.groupingBy(
                    PolicyLeagueDto::policyId,
                    LinkedHashMap::new,
                    Collectors.mapping(PolicyLeagueDto::leagueId, Collectors.toList())
                )
            );
    }

    public void snapshot(String saveId) {
        var params = new MapSqlParameterSource("saveId", saveId);
        db.update(
            """
            INSERT INTO save_roster_policies (save_id,policy_id,country,season,reference,window_start,window_end,source_url,verification,note)
            SELECT :saveId,id,country,season,reference,window_start,window_end,source_url,verification,note
            FROM catalog_roster_policies
            """,
            params
        );
        db.update(
            "INSERT INTO save_roster_policy_leagues (save_id,policy_id,league_id) SELECT :saveId,policy_id,league_id FROM catalog_roster_policy_leagues",
            params
        );
    }

    public void importCatalog(List<RosterPolicyDto> policies) {
        for (var policy : policies) write(null, policy);
    }

    public void importSave(String id, List<RosterPolicyDto> policies) {
        for (var policy : policies) write(id, policy);
    }

    private void write(String saveId, RosterPolicyDto policy) {
        var params = new MapSqlParameterSource()
            .addValue("country", policy.country())
            .addValue("season", policy.season())
            .addValue("reference", policy.reference())
            .addValue(
                "windowStart",
                policy.windowStart() == null ? null : Date.valueOf(policy.windowStart()),
                Types.DATE
            )
            .addValue("windowEnd", policy.windowEnd() == null ? null : Date.valueOf(policy.windowEnd()), Types.DATE)
            .addValue("sourceUrl", policy.sourceUrl())
            .addValue("verification", policy.verification())
            .addValue("note", policy.note());
        // Keep the import fingerprint separate from the database-generated primary key.
        params.addValue("sourceKey", RosterPolicyKeys.key(policy));
        var existing = db.query(
            "SELECT id FROM catalog_roster_policies WHERE source_key=:sourceKey",
            params,
            new org.springframework.jdbc.core.SingleColumnRowMapper<>(Long.class)
        );
        if (existing.isEmpty()) db.update(
            """
            INSERT INTO catalog_roster_policies (source_key,country,season,reference,window_start,window_end,source_url,verification,note)
            VALUES (:sourceKey,:country,:season,:reference,:windowStart,:windowEnd,:sourceUrl,:verification,:note)
            """,
            params
        );
        var policyId = existing.isEmpty()
            ? db.queryForObject(
                  "SELECT id FROM catalog_roster_policies WHERE source_key=:sourceKey",
                  params,
                  new org.springframework.jdbc.core.SingleColumnRowMapper<>(Long.class)
              )
            : existing.getFirst();
        params.addValue("policyId", policyId);
        if (saveId == null) {
            for (var leagueId : policy.leagueIds())
                db.update(
                    """
                    INSERT INTO catalog_roster_policy_leagues (policy_id,league_id)
                    SELECT :policyId,:leagueId
                    WHERE NOT EXISTS (SELECT 1
                    FROM catalog_roster_policy_leagues
                    WHERE policy_id=:policyId AND league_id=:leagueId)
                    """,
                    new MapSqlParameterSource("policyId", params.getValue("policyId")).addValue("leagueId", leagueId)
                );
        } else {
            params.addValue("saveId", saveId);
            db.update(
                """
                INSERT INTO save_roster_policies (save_id,policy_id,country,season,reference,window_start,window_end,source_url,verification,note)
                SELECT :saveId,:policyId,:country,:season,:reference,:windowStart,:windowEnd,:sourceUrl,:verification,:note
                WHERE NOT EXISTS (SELECT 1
                FROM save_roster_policies
                WHERE save_id=:saveId AND policy_id=:policyId)
                """,
                params
            );
            for (var leagueId : policy.leagueIds())
                db.update(
                    """
                    INSERT INTO save_roster_policy_leagues (save_id,policy_id,league_id)
                    SELECT :saveId,:policyId,:leagueId
                    WHERE NOT EXISTS (SELECT 1
                    FROM save_roster_policy_leagues
                    WHERE save_id=:saveId AND policy_id=:policyId AND league_id=:leagueId)
                    """,
                    new MapSqlParameterSource()
                        .addValue("saveId", saveId)
                        .addValue("policyId", params.getValue("policyId"))
                        .addValue("leagueId", leagueId)
                );
        }
    }

    private static RowMapper<RosterPolicyDto> rosterPolicyMapper(
        String keyColumn,
        Map<Long, List<Long>> leaguesByPolicy
    ) {
        return (r, n) ->
            new RosterPolicyDto(
                r.getString("country"),
                r.getString("season"),
                leaguesByPolicy.getOrDefault(r.getLong(keyColumn), List.of()),
                r.getString("reference"),
                r.getString("window_start"),
                r.getString("window_end"),
                r.getString("source_url"),
                r.getString("verification"),
                r.getString("note")
            );
    }
}
