package br.com.manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ManagerApplicationTests {

    @Autowired
    private JdbcTemplate database;

    @Test
    void initializesApplicationAndDatabaseSchema() {
        assertThat(database.queryForObject("SELECT COUNT(*) FROM managers", Integer.class)).isZero();
    }
}
