package br.com.manager.repository;

import br.com.manager.models.dto.ManagerCredentialsDto;
import br.com.manager.models.dto.ManagerDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManagerRepository {

    private static final RowMapper<ManagerCredentialsDto> MANAGER_CREDENTIALS_ROW_MAPPER = (r, n) ->
        new ManagerCredentialsDto(
            r.getString("id"),
            r.getString("name"),
            r.getString("password_hash"),
            r.getString("first_name"),
            r.getString("last_name")
        );

    private static final RowMapper<ManagerDto> MANAGER_ROW_MAPPER = (r, n) ->
        new ManagerDto(r.getString("id"), r.getString("name"), r.getString("first_name"), r.getString("last_name"));

    private final NamedParameterJdbcTemplate db;

    public ManagerRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public void create(String id, String name, String email, String passwordHash, String firstName, String lastName) {
        db.update(
            "INSERT INTO managers (id,name,email,password_hash,first_name,last_name) VALUES (:id,:name,:email,:passwordHash,:firstName,:lastName)",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("email", email)
                .addValue("passwordHash", passwordHash)
                .addValue("firstName", firstName)
                .addValue("lastName", lastName)
        );
    }

    public Optional<ManagerCredentialsDto> credentials(String email) {
        return db
            .query(
                "SELECT id,name,password_hash,first_name,last_name FROM managers WHERE email=:email",
                new MapSqlParameterSource("email", email),
                MANAGER_CREDENTIALS_ROW_MAPPER
            )
            .stream()
            .findFirst();
    }

    public void removeExpiredSessions(Instant now) {
        db.update(
            "DELETE FROM manager_sessions WHERE expires_at<=:now",
            new MapSqlParameterSource("now", Timestamp.from(now))
        );
    }

    public void createSession(String tokenHash, String managerId, Instant expiresAt) {
        db.update(
            "INSERT INTO manager_sessions (token_hash,manager_id,expires_at) VALUES (:tokenHash,:managerId,:expiresAt)",
            new MapSqlParameterSource()
                .addValue("tokenHash", tokenHash)
                .addValue("managerId", managerId)
                .addValue("expiresAt", Timestamp.from(expiresAt))
        );
    }

    public Optional<ManagerDto> findSession(String tokenHash, Instant now) {
        return db
            .query(
                "SELECT m.id,m.name,m.first_name,m.last_name FROM managers m JOIN manager_sessions s ON s.manager_id=m.id WHERE s.token_hash=:tokenHash AND s.expires_at>:now",
                new MapSqlParameterSource().addValue("tokenHash", tokenHash).addValue("now", Timestamp.from(now)),
                MANAGER_ROW_MAPPER
            )
            .stream()
            .findFirst();
    }
}
