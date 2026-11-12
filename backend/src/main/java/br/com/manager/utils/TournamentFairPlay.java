package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto.Team;
import java.util.Random;

public final class TournamentFairPlay {

    private TournamentFairPlay() {}

    public static int points(Team team) {
        return team.players
            .stream()
            .mapToInt(p -> p.yellow >= 2 ? 3 : p.sentOff ? (p.yellow > 0 ? 5 : 4) : p.yellow)
            .sum();
    }

    public static int background(long seed) {
        var random = new Random(seed);
        int yellows = 0;
        for (int chance = 0; chance < 10; chance++) if (random.nextDouble() < .22) yellows++;
        return yellows + (random.nextDouble() < .05 ? 4 : 0);
    }
}
