package br.com.manager.utils;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.Team;
import org.springframework.http.HttpStatus;

/** Commands use game minutes, never wall time. Effects replace each other and never change base attributes. */
public final class TouchlineRules {

    public static final int EFFECT_MINUTES = 5,
        REPLACEMENT_COST = 8,
        COMPLAINT_COST = 30;

    private TouchlineRules() {}

    public static int elapsed(MatchStateDto m) {
        return m.period == 1 ? m.minute : m.addedFirst + m.minute;
    }

    public static void command(MatchStateDto m, Team team, String type, long now) {
        var c = team.touchline;
        int minute = elapsed(m);
        if (!team.human || !m.status.equals("LIVE") || m.varReview != null || m.goalCelebrationUntil > now) fail(
            "Aguarde a bola voltar a rolar para dar uma instrução."
        );
        if (c.dismissed) fail(
            "Você foi expulso da área técnica. Os ajustes táticos continuam disponíveis pelo auxiliar."
        );
        if (minute < c.cooldownUntil) fail("Aguarde três minutos de jogo entre as instruções.");
        normalizeDuration(team);
        boolean replacing = !c.command.equals("NONE") && minute < c.effectUntil;
        boolean complaint = type.equals("COMPLAIN");
        int cost = complaint ? COMPLAINT_COST : replacing ? REPLACEMENT_COST : 0;
        c.cooldownUntil = minute + 3;
        c.command = type;
        c.commandDuration = EFFECT_MINUTES;
        c.effectUntil = minute + EFFECT_MINUTES;
        String instruction = switch (type) {
            case "ENCOURAGE" -> "Incentivar: o treinador apoia seus jogadores. O ataque ganha um pequeno estímulo por cinco minutos.";
            case "DEMAND" -> "Cobrar intensidade: mais pressão, com maior desgaste, por cinco minutos.";
            case "CALM" -> "Acalmar: mais controle e menos ímpeto ofensivo por cinco minutos.";
            default -> "Reclamar: o treinador defende sua equipe diante da arbitragem. O time reage com mais ataque e pressão, mas maior desgaste, por cinco minutos.";
        };
        MatchEngine.event(
            m,
            "COACH_COMMAND",
            team,
            null,
            null,
            team.name + " — " + instruction + (replacing ? " A orientação anterior foi substituída." : "")
        );
        if (complaint) c.complaints++;
        if (cost > 0) {
            c.lastComplaint = minute;
            c.refereeConfidence = Math.max(0, c.refereeConfidence - cost);
            discipline(m, team, complaint);
        }
    }

    private static void discipline(MatchStateDto m, Team team, boolean complaint) {
        var c = team.touchline;
        String event;
        if (c.refereeConfidence <= 15 && c.warnings > 0) {
            c.dismissed = true;
            c.command = "NONE";
            c.effectUntil = 0;
            c.refereeMessage = "Chega! Você está expulso da área técnica por insistência.";
            event = "COACH_RED";
        } else if (c.refereeConfidence <= 45) {
            c.warnings++;
            c.refereeMessage = "Está advertido. Controle sua insistência na área técnica ou será expulso!";
            event = "COACH_WARNING";
        } else {
            c.refereeMessage = complaint
                ? "Treinador, acalme-se e respeite as decisões. Este é um aviso."
                : "Treinador, modere as intervenções e deixe o jogo seguir!";
            event = "REFEREE_NOTICE";
        }
        MatchEngine.event(
            m,
            event,
            team,
            null,
            null,
            "Árbitro ao treinador do " +
                team.name +
                ": “" +
                c.refereeMessage +
                "”" +
                (c.dismissed ? " O auxiliar assume a comunicação com o time." : "")
        );
    }

    /** Upgrade instructions already persisted under the previous eight-minute rule. */
    private static void normalizeDuration(Team team) {
        var c = team.touchline;
        if (c.commandDuration > EFFECT_MINUTES) {
            c.effectUntil = Math.max(0, c.effectUntil - (c.commandDuration - EFFECT_MINUTES));
            c.commandDuration = EFFECT_MINUTES;
        }
    }

    public static void tick(MatchStateDto m, Team team) {
        var c = team.touchline;
        normalizeDuration(team);
        if (c.dismissed || elapsed(m) >= c.effectUntil) c.command = "NONE";
        if (!c.dismissed && elapsed(m) - c.lastComplaint > 3) c.refereeConfidence = Math.min(
            100,
            c.refereeConfidence + 1
        );
    }

    public static double attack(Team t) {
        return switch (t.touchline.command) {
            case "ENCOURAGE" -> 1.03;
            case "CALM" -> .98;
            case "COMPLAIN" -> 1.02;
            default -> 1;
        };
    }

    public static double control(Team t) {
        return t.touchline.command.equals("CALM") ? 1.03 : 1;
    }

    public static double pressing(Team t) {
        return switch (t.touchline.command) {
            case "DEMAND" -> 1.04;
            case "COMPLAIN" -> 1.02;
            default -> 1;
        };
    }

    public static double drain(Team t) {
        return switch (t.touchline.command) {
            case "DEMAND" -> 1.12;
            case "COMPLAIN" -> 1.05;
            default -> 1;
        };
    }

    private static void fail(String message) {
        throw new DefaultErrorException(HttpStatus.CONFLICT, message);
    }
}
