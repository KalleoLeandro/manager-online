package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.*;
import java.util.Random;

/** Default: one-in-twelve goal incidents; runtime override supports visual testing. */
public final class VarRules {

    private static final int REVIEW_DENOMINATOR = reviewDenominator();

    private static int reviewDenominator() {
        String value = System.getProperty("manager.var.review-denominator", System.getenv("VAR_REVIEW_DENOMINATOR"));
        if (value == null) return 12;
        try {
            int denominator = Integer.parseInt(value);
            return denominator > 0 ? denominator : 12;
        } catch (NumberFormatException ignored) {
            return 12;
        }
    }

    public static final int REVIEW_SECONDS = 4,
        DECISION_SECONDS = 3;

    private VarRules() {}

    private static Random random(long seed, int goalSequence, long salt) {
        return new Random(seed ^ ((long) goalSequence * 0x9E3779B97F4A7C15L) ^ salt);
    }

    public static boolean needsReview(long seed, int goalSequence) {
        return random(seed, goalSequence, 0x5641525049434bL).nextInt(REVIEW_DENOMINATOR) == 0;
    }

    public static boolean confirms(long seed, int goalSequence) {
        return random(seed, goalSequence, 0x564152524553554cL).nextDouble() < .75;
    }

    public static void decide(MatchStateDto m) {
        var review = m.varReview;
        if (review == null || !review.phase.equals("REVIEW")) return;
        var team = m.home.id == review.teamId ? m.home : m.away;
        var scorer = team.players
            .stream()
            .filter(p -> p.id == review.playerId)
            .findFirst()
            .orElseThrow();
        var assistant =
            review.assistId == null
                ? null
                : team.players
                      .stream()
                      .filter(p -> p.id == review.assistId)
                      .findFirst()
                      .orElse(null);
        review.confirmed = confirms(m.seed, review.goalSequence);
        review.phase = "DECISION";
        var original = m.events.get(review.goalSequence - 1);
        if (review.confirmed) {
            review.reason = "Nenhuma irregularidade identificada. Gol confirmado.";
            m.events.set(
                review.goalSequence - 1,
                new Event(
                    original.sequence(),
                    original.minute(),
                    original.period(),
                    "GOAL",
                    original.teamId(),
                    original.playerId(),
                    original.relatedPlayerId(),
                    "GOL de " +
                        scorer.name +
                        "! " +
                        team.name +
                        ". " +
                        (assistant == null ? "" : "Assistência de " + assistant.name + ". ") +
                        "Confirmado pelo VAR."
                )
            );
        } else {
            int reason = review.penalty ? 3 : random(m.seed, review.goalSequence, 0x524541534f4eL).nextInt(3);
            review.reason = switch (reason) {
                case 0 -> "Impedimento do ataque na origem da jogada.";
                case 1 -> "Falta do ataque na construção da jogada.";
                case 2 -> "Toque de mão do atacante antes da finalização.";
                default -> "Toque duplo deliberado do cobrador no pênalti.";
            };
            team.goals--;
            scorer.goals--;
            if (assistant != null) assistant.assists--;
            team.stats.shots--;
            team.stats.shotsOnTarget--;
            scorer.shots--;
            scorer.shotsOnTarget=Math.max(0,scorer.shotsOnTarget-1);
            MatchEngine.active(team==m.home?m.away:m.home).forEach(p->p.goalsAgainst=Math.max(0,p.goalsAgainst-1));
            team.stats.expectedGoals = Math.max(0, team.stats.expectedGoals - review.expectedGoals);
            if (review.penalty) team.stats.penaltiesScored--;
            if (reason == 0) team.stats.offsides++;
            else if (reason != 3) team.stats.fouls++;
            m.events.set(
                review.goalSequence - 1,
                new Event(
                    original.sequence(),
                    original.minute(),
                    original.period(),
                    "GOAL_DISALLOWED",
                    original.teamId(),
                    original.playerId(),
                    null,
                    "Gol de " + scorer.name + " anulado. " + review.reason
                )
            );
            for (int i = 0; i < m.events.size(); i++) {
                var e = m.events.get(i);
                if (e.type().equals("FULL_TIME")) m.events.set(
                    i,
                    new Event(
                        e.sequence(),
                        e.minute(),
                        e.period(),
                        e.type(),
                        e.teamId(),
                        e.playerId(),
                        e.relatedPlayerId(),
                        "Fim de jogo: " +
                            m.home.name +
                            " " +
                            m.home.goals +
                            " × " +
                            m.away.goals +
                            " " +
                            m.away.name +
                            "."
                    )
                );
            }
        }
        MatchEngine.event(
            m,
            "VAR_DECISION",
            team,
            scorer,
            assistant,
            (review.confirmed ? "VAR: gol confirmado. " : "VAR: gol anulado. ") + review.reason
        );
    }
}
