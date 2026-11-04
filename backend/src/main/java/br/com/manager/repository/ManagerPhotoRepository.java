package br.com.manager.repository;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class ManagerPhotoRepository {

    private final NamedParameterJdbcTemplate db;

    public ManagerPhotoRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public void create(String id, byte[] png) {
        db.update(
            "INSERT INTO manager_photos(manager_id,png) VALUES(:id,:png)",
            new MapSqlParameterSource("id", id).addValue("png", png)
        );
    }

    public Optional<byte[]> photo(String id) {
        return db
            .query("SELECT png FROM manager_photos WHERE manager_id=:id", new MapSqlParameterSource("id", id), (r, n) ->
                r.getBytes("png")
            )
            .stream()
            .findFirst();
    }

    public boolean exists(String id) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM manager_photos WHERE manager_id=:id",
                new MapSqlParameterSource("id", id),
                Integer.class
            ) > 0
        );
    }
}
