package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.Player;
import java.util.List;

/** Cumulative match performance, independent from ability and outcome randomness. */
public final class PlayerMatchRatings {

    private PlayerMatchRatings() {}

    public static Double calculate(Player p) {
        if (p.minutes < 5) return null;
        double value =
            6 +
            p.goals * 1.1 +
            p.assists * .65 +
            Math.min(.4, p.passesCompleted * .004) -
            Math.min(1.2, p.passesMissed * .018) +
            Math.min(.8, p.tackles * .07) +
            Math.min(.7, p.interceptions * .045) +
            Math.min(.6, p.blockedShots * .12) +
            Math.min(1.5, p.saves * .16) +
            Math.min(.5, p.shotsOnTarget * .08) -
            Math.min(.5, Math.max(0, p.shots - p.shotsOnTarget) * .035) -
            p.yellow * .25 -
            (p.sentOff ? 1.4 : 0) -
            Math.min(.5, p.fouls * .045) -
            Math.min(1.5, p.goalsAgainst * ("GOL".equals(p.position) ? .28 : "DEF".equals(p.position) ? .14 : .04));
        return Math.round(Math.clamp(value, 1, 10) * 10) / 10.0;
    }

    public static void refresh(MatchStateDto state) {
        for (var team : List.of(state.home, state.away))
            for (var player : team.players) {
                // Candidate goals must not reveal a VAR decision through the rating.
                var review = state.varReview;
                boolean pending = review != null && "REVIEW".equals(review.phase);
                int goals = player.goals,
                    assists = player.assists,
                    against = player.goalsAgainst;
                if (pending) {
                    if (player.id == review.playerId) player.goals = Math.max(0, goals - 1);
                    if (review.assistId != null && player.id == review.assistId) player.assists = Math.max(
                        0,
                        assists - 1
                    );
                    if (team.id != review.teamId && player.onPitch) player.goalsAgainst = Math.max(0, against - 1);
                }
                player.rating = calculate(player);
                player.goals = goals;
                player.assists = assists;
                player.goalsAgainst = against;
            }
    }
}
