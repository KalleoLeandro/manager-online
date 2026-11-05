package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.models.requests.*;
import br.com.manager.repository.*;
import br.com.manager.service.*;
import br.com.manager.utils.TournamentRulebooks.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TournamentDrawTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    TournamentRepository repository;

    @Autowired
    TournamentDrawRepository draws;

    @Autowired
    TournamentService tournaments;

    @Test
    void drawsWaitForTheirDateAndEachCoachAcknowledgesTheSameImmutableResults() {
        var clubs = catalog.teams(6L);
        var owner = saves.create(new CreateLeagueRequest("Draw test", 2, "Coach one", clubs.getFirst().id()));
        var friend = saves.join(owner.room().inviteCode(), new JoinLeagueRequest("Coach two", clubs.get(1).id()));
        String id = owner.room().id();
        var stages = List.of(
            new Stage("R4", "Synthetic semifinal", 4, 4, 1, false, false, LocalDate.of(2026, 2, 18), null, "OPEN_DRAW"),
            new Stage(
                "FINAL",
                "Synthetic final",
                2,
                0,
                1,
                false,
                true,
                LocalDate.of(2026, 3, 1),
                null,
                "PREVIOUS_BRACKET"
            )
        );
        var rules = new Rulebook(
            "BRA_CUP",
            "Synthetic cup",
            "Brasil",
            2026,
            "TEST",
            "https://example.test/rules",
            4,
            stages
        );
        var entrants = new ArrayList<Entrant>();
        for (int i = 0; i < 4; i++) entrants.add(
            new Entrant(clubs.get(i).id(), clubs.get(i).name(), "R4", i + 1, "BRA", null, 1, 70)
        );
        var edition = repository.create(id, rules, entrants);
        tournaments.synchronize(id, LocalDate.of(2026, 1, 27));
        assertThat(repository.stageExists(edition.id(), 0)).isFalse();
        assertThat(draws.pending(id, owner.memberId())).isEmpty();
        tournaments.synchronize(id, LocalDate.of(2026, 1, 28));
        var first = draws.pending(id, owner.memberId());
        assertThat(first).hasSize(1);
        assertThat(first.getFirst().rows()).hasSize(2);
        tournaments.synchronize(id, LocalDate.of(2026, 1, 28));
        assertThat(draws.pending(id, friend.memberId())).isEqualTo(first);
        assertThat(repository.games(id, edition.id())).allMatch(g -> g.status().equals("SCHEDULED"));
        assertThat(draws.acknowledge(id, owner.memberId(), first.getFirst().id())).isTrue();
        assertThat(draws.pending(id, owner.memberId())).isEmpty();
        assertThat(draws.pending(id, friend.memberId())).isEqualTo(first);
        assertThat(draws.acknowledge("another-league", friend.memberId(), first.getFirst().id())).isFalse();
    }
}
