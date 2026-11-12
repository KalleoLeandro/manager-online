package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.Player;
import java.util.List;

/** Cumulative match performance, independent from ability and outcome randomness. */
public final class PlayerMatchRatings {

    private PlayerMatchRatings() {}

    public static Double calculate(Player p) {
        return calculate(p, true);
    }

    private static Double calculate(Player p, boolean actualInterceptions) {
        if (p.minutes < 5) return null;
        String role = FormationRules.family(p.position == null ? "MEI" : p.position);
        double defensiveWeight = switch (role) {
            case "DEF" -> 1.15;
            case "ATA" -> .65;
            case "GOL" -> .3;
            default -> 1;
        };
        // Reward reliable distribution, not pass volume alone. Failed passes matter too.
        double passing = Math.clamp(p.passesCompleted * .032 - p.passesMissed * .12, -1.8, 1.2);
        double defending = Math.min(
            2.2,
            (p.tackles * (actualInterceptions ? .18 : .12) +
                p.interceptions * (actualInterceptions ? .22 : .08) +
                p.blockedShots * (actualInterceptions ? .24 : .22)) * defensiveWeight
        );
        double cleanSheet =
            p.goalsAgainst == 0 && p.minutes >= 30
                ? (role.equals("GOL") ? .55 : role.equals("DEF") ? .35 : 0) * Math.min(1, p.minutes / 90.)
                : 0;
        // An anonymous full match should differ from a short, uneventful substitute appearance.
        double inactiveAttack =
            role.equals("ATA") && p.shots == 0 && p.goals == 0 && p.assists == 0
                ? .35 * Math.clamp((p.minutes - 30) / 60., 0, 1)
                : 0;
        double value =
            6 +
            p.goals * 1.15 +
            p.assists * .75 +
            passing +
            defending +
            cleanSheet +
            Math.min(2.6, p.saves * .26) +
            Math.min(.9, p.shotsOnTarget * .15) -
            Math.min(.8, Math.max(0, p.shots - p.shotsOnTarget) * .075) -
            p.yellow * .3 -
            (p.sentOff ? 1.5 : 0) -
            Math.min(.7, p.fouls * .07) -
            inactiveAttack -
            Math.min(2.1, p.goalsAgainst * (role.equals("GOL") ? .42 : role.equals("DEF") ? .18 : .04));
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
                player.rating = calculate(player, state.engineVersion >= 5);
                player.goals = goals;
                player.assists = assists;
                player.goalsAgainst = against;
            }
    }
}
