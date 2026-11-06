package br.com.manager.utils;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.MatchStateDto;
import java.util.Random;
import org.springframework.http.HttpStatus;

public final class HalfTimeTalkRules {

    private HalfTimeTalkRules() {}

    public static void talk(MatchStateDto state, MatchStateDto.Team team, Long playerId, String type) {
        if (
            !state.halfTimeBreak ||
            !"PAUSED".equals(state.status) ||
            !state.awaitingCoachDecision ||
            !state.pendingCoachTeams.contains(team.id)
        ) fail("A conversa só está disponível durante o intervalo, antes de confirmar os ajustes.");
        var player = team.players
            .stream()
            .filter(p -> playerId != null && p.id == playerId && !p.withdrawn && !p.sentOff && !p.injured)
            .findFirst()
            .orElseThrow(() ->
                new DefaultErrorException(HttpStatus.CONFLICT, "Escolha um atleta disponível do seu time.")
            );
        if (player.lastTalkPeriod == state.period) fail("Você já conversou com este jogador neste intervalo.");
        double rating = player.rating == null ? 6 : player.rating;
        double chance = switch (type) {
            case "TALK_PRAISE" -> rating >= 6.8 ? .85 : rating < 5.8 ? .28 : .62;
            case "TALK_CRITICIZE" -> rating < 6 ? .72 : rating >= 7 ? .20 : .46;
            default -> .76;
        };
        chance += (player.attributes.getOrDefault("determination", 50) - 50) * .001;
        boolean positive =
            new Random(state.seed ^ (player.id * 193L) ^ (state.period * 101L) ^ type.hashCode()).nextDouble() < chance;
        int morale = player.morale,
            sharpness = player.sharpness;
        player.morale = Math.clamp(morale + (positive ? 6 : -5), 1, 100);
        player.sharpness = Math.clamp(sharpness + (positive ? 4 : -3), 1, 100);
        player.talkMoraleDelta += player.morale - morale;
        player.talkSharpnessDelta += player.sharpness - sharpness;
        player.lastTalkPeriod = state.period;
        player.halfTimeTalk = type;
        player.talkReaction = positive
            ? "Recebeu bem a conversa e voltou mais confiante e concentrado."
            : "Não recebeu bem a abordagem e perdeu confiança e concentração.";
        MatchEngine.event(state, "HALF_TIME_TALK", team, player, null, player.name + ": " + player.talkReaction);
    }

    private static void fail(String message) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, message);
    }
}
