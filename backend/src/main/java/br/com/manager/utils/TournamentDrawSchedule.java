package br.com.manager.utils;

import br.com.manager.models.dto.TournamentDtos.Edition;
import java.time.LocalDate;
import java.util.List;

public final class TournamentDrawSchedule {

    private TournamentDrawSchedule() {}

    public static LocalDate date(Edition edition, int stage) {
        var rules = edition.rules().stages().get(stage);
        if (edition.key().equals("INTERCONTINENTAL")) return rules.firstDate();
        if (edition.key().equals("CLUB_WORLD_CUP") && stage == 0) return LocalDate.of(edition.year() - 1, 12, 5);
        if (edition.key().equals("BRA_CUP") && stage == 0) return LocalDate.of(edition.year(), 1, 28);
        if (edition.key().equals("CHAMPIONS") && rules.key().equals("LEAGUE")) return LocalDate.of(
            edition.year(),
            8,
            27
        );
        return rules.firstDate().minusDays(14);
    }

    public static boolean ceremony(Edition edition, int stage) {
        return !List.of(
            "PREVIOUS_BRACKET",
            "UEFA_FIXED_BRACKET",
            "FIFA_GROUP_BRACKET",
            "INTERCONTINENTAL_PATH"
        ).contains(edition.rules().stages().get(stage).drawRule());
    }
}
