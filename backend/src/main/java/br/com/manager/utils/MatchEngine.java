package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.*;
import java.util.*;

/** Own probabilistic text engine. A step is a game minute, independent of connected browsers. */
public final class MatchEngine {

    public static final int CURRENT_VERSION = 4;

    private MatchEngine() {}

    public static List<Player> active(Team team) {
        if (team.players.stream().noneMatch(p -> p.slot >= 0)) return team.players
            .stream()
            .filter(p -> p.onPitch && !p.sentOff && !p.injured)
            .toList();
        return team.players
            .stream()
            .filter(p -> p.onPitch && !p.sentOff && !p.injured)
            .sorted(Comparator.comparingInt(p -> p.slot < 0 ? 0 : p.slot))
            .toList();
    }

    public static double skill(Player p, String... keys) {
        double value = Arrays.stream(keys)
            .mapToInt(k -> p.attributes.getOrDefault(k, p.ability))
            .average()
            .orElse(p.ability);
        // Current condition changes performance, never the permanent attributes.
        double form =
            1 +
            (new Random(p.id * 31L + p.ability).nextDouble() - .5) *
                .22 *
                (1 - p.attributes.getOrDefault("consistency", 60) / 100.0);
        double performance =
            value *
            form *
            (.68 + (.32 * p.fitness) / 100.0) *
            (.9 + (.1 * p.morale) / 100.0) *
            (.9 + (.1 * p.sharpness) / 100.0);
        return p.assignedPosition == null ? performance : performance * PlayerPositionRules.fit(p);
    }

    private static double unit(Team team, String role, String... keys) {
        var field = active(team);
        var selected = field
            .stream()
            .filter(p -> role == null ? !p.position.equals("GOL") : p.position.equals(role))
            .toList();
        if (selected.isEmpty()) return 12;
        double quality = selected
            .stream()
            .mapToDouble(p -> skill(p, keys))
            .average()
            .orElse(12);
        var formation = team.formation.split("-");
        int desired =
            role == null
                ? selected.size()
                : role.equals("DEF")
                  ? Integer.parseInt(formation[0])
                  : role.equals("MEI")
                    ? Integer.parseInt(formation[1])
                    : Integer.parseInt(formation[2]);
        double shape = role == null ? 1 : Math.max(.65, 1 - .075 * Math.abs(selected.size() - desired));
        double leadership = field
            .stream()
            .mapToInt(p -> p.attributes.getOrDefault("leadership", 50))
            .max()
            .orElse(50);
        return quality * shape * (1 + (leadership - 50) * .0003) * Math.min(1, field.size() / 11.0);
    }

    public static void event(MatchStateDto m, String type, Team t, Player p, Player related, String text) {
        m.events.add(
            new Event(
                m.events.size() + 1,
                m.minute,
                m.period,
                type,
                t.id,
                p == null ? null : p.id,
                related == null ? null : related.id,
                text
            )
        );
    }

    public static void step(MatchStateDto m) {
        if (!m.status.equals("LIVE") || m.varReview != null) return;
        if (m.periodEndPending) {
            m.periodEndPending = false;
            completePeriod(m);
            m.revision++;
            return;
        }
        m.halfTimeBreak = false;
        int firstEvent = m.events.size();
        var r = new Random(m.seed ^ ((long) (m.period * 1000 + m.minute + 1) * 0x9E3779B97F4A7C15L));
        m.minute++;
        if (m.engineVersion >= 4 && ((m.period == 1 && m.minute == 45) || (m.period == 2 && m.minute == 90))) {
            int added = m.period == 1 ? m.addedFirst : m.addedSecond;
            event(
                m,
                "ADDED_TIME",
                m.home,
                null,
                null,
                "O quarto árbitro anuncia mais " +
                    added +
                    " minuto" +
                    (added == 1 ? "" : "s") +
                    " de acréscimos no " +
                    (m.period == 1 ? "primeiro" : "segundo") +
                    " tempo."
            );
        }
        for (var team : List.of(m.home, m.away)) {
            if (m.engineVersion >= 4) TouchlineRules.tick(m, team);
            for (var p : active(team)) {
                p.minutes++;
                double drain = .18 + (100 - p.attributes.getOrDefault("stamina", p.ability)) * .002;
                if (team.mentality.equals("ATTACKING")) drain *= 1.2;
                if (m.engineVersion >= 3) drain *= MatchTacticalEffects.drainMultiplier(team);
                if (m.engineVersion >= 4) drain *= TouchlineRules.drain(team);
                if (r.nextDouble() < drain) p.fitness = Math.max(1, p.fitness - 1);
            }
        }
        int scoreBefore = m.home.goals + m.away.goals;
        if (m.penaltyTeam != null) {
            penalty(m, m.penaltyTeam == m.home.id ? m.home : m.away, r);
            m.penaltyTeam = null;
        }
        boolean modern = m.engineVersion >= 2;
        var homeSectors = modern ? MatchPerformance.sectors(m.home, m.engineVersion >= 3) : null;
        var awaySectors = modern ? MatchPerformance.sectors(m.away, m.engineVersion >= 3) : null;
        int homeStrengthCount = active(m.home).size(),
            awayStrengthCount = active(m.away).size();
        for (
            int possession = 0;
            possession < 6 && m.home.goals + m.away.goals == scoreBefore && !humanIncident(m, firstEvent);
            possession++
        ) {
            double controlHome = modern
                ? 0
                : unit(m.home, null, "short_passing", "teamwork", "vision", "first_touch") *
                  (m.neutralVenue ? 1 : 1.035);
            double controlAway = modern ? 0 : unit(m.away, null, "short_passing", "teamwork", "vision", "first_touch");
            // Condition changes once per minute; only dismissals/injuries invalidate a sector inside it.
            if (modern && homeStrengthCount != active(m.home).size()) {
                homeStrengthCount = active(m.home).size();
                homeSectors = MatchPerformance.sectors(m.home, m.engineVersion >= 3);
            }
            if (modern && awayStrengthCount != active(m.away).size()) {
                awayStrengthCount = active(m.away).size();
                awaySectors = MatchPerformance.sectors(m.away, m.engineVersion >= 3);
            }
            double possessionChance = modern
                ? MatchPerformance.homePossession(homeSectors, awaySectors, m.neutralVenue)
                : controlHome / (controlHome + controlAway);
            Team attack = r.nextDouble() < possessionChance ? m.home : m.away;
            Team defend = attack == m.home ? m.away : m.home;
            if (active(attack).isEmpty() || active(defend).isEmpty()) continue;
            attack.stats.possessionTicks++;
            var carrier = pick(attack, r, r.nextDouble() < .1);
            var marker = pick(defend, r, false);
            double passChance = clamp(
                .78 +
                    (skill(
                        carrier,
                        carrier.position.equals("GOL") ? "kicking" : "short_passing",
                        "technique",
                        "decisions"
                    ) -
                        unit(defend, null, "anticipation", "marking", "work_rate")) *
                        .002,
                .56,
                .94
            );
            if (m.engineVersion >= 3) passChance = clamp(
                passChance + MatchTacticalEffects.passAdjustment(attack, carrier),
                .50,
                .96
            );
            int completed = 0;
            boolean possessionLost = false;
            Player firstPasser = carrier;
            var narration = new Random(m.seed ^ (m.minute * 1009L + possession * 7919L + m.period * 131L));
            for (int pass = 0, count = 1 + r.nextInt(3); pass < count; pass++) {
                if (r.nextDouble() < passChance) {
                    attack.stats.passesCompleted++;
                    carrier.passesCompleted++;
                    completed++;
                    long previousCarrier = carrier.id;
                    var receivers = active(attack)
                        .stream()
                        .filter(p -> p.id != previousCarrier && !p.position.equals("GOL"))
                        .toList();
                    if (!receivers.isEmpty()) carrier = receivers.get(narration.nextInt(receivers.size()));
                } else {
                    attack.stats.passesMissed++;
                    carrier.passesMissed++;
                    defend.stats.interceptions++;
                    possessionLost = true;
                    break;
                }
            }
            if (possession == 0 && completed > 0) event(
                m,
                "PASS_SEQUENCE",
                attack,
                firstPasser,
                carrier,
                MatchNarration.exchange(m, firstPasser, carrier, attack)
            );
            else if (possession == 0 && completed == 0) event(
                m,
                "INTERCEPTION",
                defend,
                marker,
                carrier,
                marker.name +
                    " antecipa a intenção de " +
                    carrier.name +
                    ", corta a linha de passe e recupera a posse para " +
                    defend.name +
                    "."
            );
            double incident = r.nextDouble();
            double trap =
                m.engineVersion >= 3 && defend.tacticalPlan != null && defend.tacticalPlan.offsideTrap ? .003 : 0;
            if (incident < .052) {
                attack.stats.throwIns++;
                event(
                    m,
                    "THROW_IN",
                    attack,
                    carrier,
                    null,
                    carrier.name +
                        " se apresenta para cobrar o lateral de " +
                        attack.name +
                        ", enquanto " +
                        marker.name +
                        " acompanha a movimentação e fecha a opção curta."
                );
            } else if (incident < .061 + trap) {
                attack.stats.offsides++;
                event(
                    m,
                    "OFFSIDE",
                    attack,
                    carrier,
                    null,
                    carrier.name +
                        " dispara às costas de " +
                        marker.name +
                        ", mas a bandeira sobe: impedimento na tentativa de receber o passe em profundidade."
                );
            } else if (incident < .101 + trap) {
                foul(m, attack, defend, carrier, marker, r);
            } else if (incident < .103 + trap) {
                injury(m, attack, carrier, r);
            } else if (completed > 0 && (!modern || !possessionLost)) {
                double chance;
                if (modern) chance = MatchPerformance.shotChance(
                    attack == m.home ? homeSectors : awaySectors,
                    attack == m.home ? awaySectors : homeSectors,
                    attack,
                    defend,
                    attack == m.home,
                    m.neutralVenue
                );
                else {
                    double creation =
                        unit(attack, "MEI", "vision", "long_passing", "dribbling", "agility") +
                        unit(attack, "ATA", "off_ball", "pace", "acceleration", "determination");
                    double prevention =
                        2 * unit(defend, "DEF", "marking", "tackling", "anticipation", "strength", "concentration");
                    double posture = attack.mentality.equals("ATTACKING")
                        ? 1.22
                        : attack.mentality.equals("DEFENSIVE")
                          ? .77
                          : 1;
                    double exposure = defend.mentality.equals("ATTACKING")
                        ? 1.15
                        : defend.mentality.equals("DEFENSIVE")
                          ? .85
                          : 1;
                    chance = clamp(.043 * Math.exp((creation - prevention) / 160) * posture * exposure, .014, .09);
                }
                if (r.nextDouble() < chance) shot(m, attack, defend, r, false, false, carrier);
                else if (r.nextDouble() < .09) defend.stats.tackles++;
            }
        }
        aiChanges(m, m.home, r);
        aiChanges(m, m.away, r);
        if (active(m.home).size() < 7 || active(m.away).size() < 7) {
            // Simplified competition rule: administrative 3–0, retaining an already larger winning margin.
            Team loser = active(m.home).size() < 7 ? m.home : m.away;
            Team winner = loser == m.home ? m.away : m.home;
            if (winner.goals - loser.goals < 3) {
                winner.goals = 3;
                loser.goals = 0;
            }
            event(
                m,
                "ABANDONED",
                loser,
                null,
                null,
                "Partida encerrada: menos de sete atletas disponíveis. Resultado administrativo aplicado."
            );
            finish(m);
        } else if (m.period == 1 && m.minute >= 45 + m.addedFirst) {
            event(m, "HALF_TIME", m.home, null, null, "Intervalo.");
            m.period = 2;
            m.minute = 45;
            m.halfTimeBreak = true;
            for (var team : List.of(m.home, m.away))
                for (var p : active(team)) p.fitness = Math.min(100, p.fitness + 3);
        } else if (
            ((m.period == 2 && m.minute >= 90 + m.addedSecond) ||
                (m.period == 3 && m.minute >= 106) ||
                (m.period == 4 && m.minute >= 121)) &&
            m.penaltyTeam == null
        ) {
            if (
                m.cupMatch &&
                (m.varReview != null ||
                    m.events
                        .subList(firstEvent, m.events.size())
                        .stream()
                        .anyMatch(e -> e.type().equals("GOAL")))
            ) m.periodEndPending = true;
            else completePeriod(m);
        }
        m.revision++;
    }

    private static void finish(MatchStateDto m) {
        m.status = "FINISHED";
        event(
            m,
            "FULL_TIME",
            m.home,
            null,
            null,
            "Fim de jogo: " + m.home.name + " " + m.home.goals + " × " + m.away.goals + " " + m.away.name + "."
        );
    }

    /** Aggregate goals have no away-goals bonus. Shootout goals never change the match score. */
    public static void completePeriod(MatchStateDto m) {
        if (m.period == 3) {
            m.period = 4;
            m.minute = 105;
            m.halfTimeBreak = true;
            event(m, "HALF_TIME", m.home, null, null, "Intervalo da prorrogação.");
            return;
        }
        boolean tied = m.home.goals + m.aggregateHomeBefore == m.away.goals + m.aggregateAwayBefore;
        if (m.decisiveTie && tied && m.extraTimeAllowed && m.period == 2) {
            m.period = 3;
            m.minute = 90;
            m.halfTimeBreak = true;
            event(
                m,
                "EXTRA_TIME",
                m.home,
                null,
                null,
                "Empate no confronto. Serão disputados dois tempos de quinze minutos de prorrogação."
            );
            event(m, "HALF_TIME", m.home, null, null, "Intervalo antes da prorrogação. Prepare sua equipe.");
            return;
        }
        if (m.decisiveTie && tied) {
            var random = new Random(m.seed ^ 0x50454E414C5459L);
            var score = PenaltyShootoutRules.play(
                shootoutChance(m.home, m.away),
                shootoutChance(m.away, m.home),
                random
            );
            m.homePenalties = score.home();
            m.awayPenalties = score.away();
            event(
                m,
                "SHOOTOUT",
                score.home() > score.away() ? m.home : m.away,
                null,
                null,
                "Decisão por pênaltis: " +
                    m.home.name +
                    " " +
                    score.home() +
                    " × " +
                    score.away() +
                    " " +
                    m.away.name +
                    "."
            );
        }
        finish(m);
    }

    private static double shootoutChance(Team attack, Team defend) {
        double taking = active(attack)
            .stream()
            .mapToDouble(p -> skill(p, "penalties", "composure"))
            .average()
            .orElse(50);
        double keeper = active(defend)
            .stream()
            .filter(p -> p.position.equals("GOL"))
            .mapToDouble(p -> skill(p, "reflexes", "one_on_ones"))
            .findFirst()
            .orElse(30);
        return Math.clamp(.76 + (taking - keeper) / 250, .55, .90);
    }

    private static boolean humanIncident(MatchStateDto m, int firstEvent) {
        return m.events
            .subList(firstEvent, m.events.size())
            .stream()
            .anyMatch(
                e ->
                    (e.type().equals("INJURY") || e.type().equals("RED")) &&
                    (e.teamId() == m.home.id ? m.home.human : m.away.human)
            );
    }

    private static void foul(MatchStateDto m, Team attack, Team defend, Player carrier, Player marker, Random r) {
        defend.stats.fouls++;
        event(
            m,
            "FOUL",
            defend,
            marker,
            carrier,
            marker.name +
                " tenta fechar o avanço de " +
                carrier.name +
                ", chega atrasado na disputa e comete a falta. " +
                attack.name +
                " terá a bola parada para reorganizar o ataque."
        );
        int aggression = marker.attributes.getOrDefault("aggression", 50);
        int composure = marker.attributes.getOrDefault("composure", marker.ability);
        int decisions = marker.attributes.getOrDefault("decisions", marker.ability);
        if (r.nextDouble() < DisciplineRules.directRedChance(aggression, composure, decisions)) red(
            m,
            defend,
            marker,
            "Cartão vermelho direto para "
        );
        else if (r.nextDouble() < DisciplineRules.yellowChance(aggression, composure, decisions, marker.yellow > 0)) {
            marker.yellow++;
            defend.stats.yellows++;
            event(m, "YELLOW", defend, marker, null, "Cartão amarelo para " + marker.name + ".");
            if (marker.yellow == 2) red(m, defend, marker, "Segundo amarelo: expulso ");
        }
        if (marker.sentOff && defend.human) return;
        if (r.nextDouble() < .022 && m.penaltyTeam == null) {
            m.penaltyTeam = attack.id;
            attack.stats.penalties++;
            event(
                m,
                "PENALTY_AWARDED",
                attack,
                carrier,
                null,
                "Pênalti para " + attack.name + ". Cobrador automático se não houver indicação."
            );
        } else if (r.nextDouble() < .09) shot(m, attack, defend, r, false, true, null);
        if (r.nextDouble() < .012) injury(m, attack, carrier, r);
    }

    private static void red(MatchStateDto m, Team t, Player p, String text) {
        p.sentOff = true;
        p.onPitch = false;
        t.stats.reds++;
        event(m, "RED", t, p, null, text + p.name + ".");
    }

    private static void injury(MatchStateDto m, Team t, Player p, Random r) {
        if (p.injured) return;
        // Low fitness and poor resistance make an incident more likely to become an injury.
        double risk =
            .16 + (100 - p.fitness) * .004 + (100 - p.attributes.getOrDefault("injury_resistance", 60)) * .003;
        if (r.nextDouble() > risk) return;
        p.injured = true;
        p.onPitch = false;
        p.injuryDays = r.nextDouble() < .8 ? 3 + r.nextInt(12) : 15 + r.nextInt(29);
        t.stats.injuries++;
        event(m, "INJURY", t, p, null, p.name + " saiu lesionado. Previsão inicial: " + p.injuryDays + " dias.");
    }

    private static void shot(
        MatchStateDto m,
        Team attack,
        Team defend,
        Random r,
        boolean corner,
        boolean freeKick,
        Player creator
    ) {
        var candidates = active(attack)
            .stream()
            .filter(p -> !p.position.equals("GOL"))
            .toList();
        if (candidates.isEmpty()) return;
        var shooter = corner
            ? candidates
                  .stream()
                  .max(Comparator.comparingDouble(p -> skill(p, "heading", "jumping")))
                  .orElseThrow()
            : freeKick
              ? candidates
                    .stream()
                    .max(Comparator.comparingDouble(p -> skill(p, "free_kicks", "shot_power")))
                    .orElseThrow()
              : candidates.get(r.nextInt(candidates.size()));
        // Prefer forwards without making defenders incapable of scoring.
        if (!corner && !freeKick && r.nextDouble() < .65) shooter = active(attack)
            .stream()
            .filter(p -> p.position.equals("ATA"))
            .max(Comparator.comparingDouble(p -> skill(p, "finishing", "off_ball")))
            .orElse(shooter);
        if (m.engineVersion >= 2 && !corner && !freeKick) shooter = MatchPerformance.shooter(candidates, r);
        var keeper = active(defend)
            .stream()
            .filter(p -> p.position.equals("GOL"))
            .findFirst()
            .orElse(null);
        double keeperSkill =
            keeper == null
                ? 15
                : skill(keeper, "reflexes", "handling", "gk_positioning", "diving", "one_on_ones", "concentration");
        boolean longShot = !corner && !freeKick && r.nextDouble() < .25;
        double precision = corner
            ? skill(shooter, "heading", "jumping", "strength")
            : freeKick
              ? skill(shooter, "free_kicks", "technique")
              : skill(shooter, longShot ? "long_shots" : "finishing", "composure", "shot_power", "technique");
        Player provider = creator != null && creator.id != shooter.id ? creator : null;
        event(
            m,
            "SHOT_BUILDUP",
            attack,
            shooter,
            provider,
            MatchNarration.shot(shooter, provider, corner, freeKick, longShot)
        );
        double xg = clamp(
            (corner ? .10 : freeKick ? .065 : longShot ? .065 : .15) + (precision - keeperSkill) * .0013,
            .025,
            .32
        );
        if (m.engineVersion >= 2) xg = MatchPerformance.expectedGoals(
            precision,
            keeperSkill,
            MatchPerformance.sectors(attack, m.engineVersion >= 3),
            MatchPerformance.sectors(defend, m.engineVersion >= 3),
            corner,
            freeKick,
            longShot
        );
        attack.stats.expectedGoals += xg;
        attack.stats.shots++;
        shooter.shots++;
        boolean onTarget = r.nextDouble() < clamp(.36 + (precision - 60) * .004, .20, .65);
        if (onTarget) {
            attack.stats.shotsOnTarget++;
            if (r.nextDouble() < Math.min(.85, xg / clamp(.36 + (precision - 60) * .004, .20, .65))) goal(
                m,
                attack,
                shooter,
                assistProvider(attack, r, corner, freeKick, provider),
                xg,
                false
            );
            else {
                defend.stats.saves++;
                if (keeper != null) keeper.saves++;
                event(
                    m,
                    "SAVE",
                    defend,
                    keeper,
                    shooter,
                    "Defesa de " +
                        (keeper == null ? "goleiro improvisado" : keeper.name) +
                        "! O goleiro acompanha a trajetória, reage à finalização de " +
                        shooter.name +
                        "."
                );
                if (!corner && r.nextDouble() < .23) corner(m, attack, defend, r);
            }
        } else if (r.nextDouble() < .35 && !corner) {
            defend.stats.blockedShots++;
            event(
                m,
                "BLOCKED_SHOT",
                defend,
                null,
                shooter,
                shooter.name +
                    " procura espaço para finalizar, mas a marcação de " +
                    defend.name +
                    " fecha o caminho e bloqueia a bola antes de chegar ao gol."
            );
            corner(m, attack, defend, r);
        } else event(
            m,
            "SHOT_OFF",
            attack,
            shooter,
            null,
            shooter.name +
                " tenta definir a jogada" +
                (longShot
                    ? " de fora da área"
                    : corner
                      ? " pelo alto"
                      : freeKick
                        ? " na cobrança de falta"
                        : " na finalização") +
                ", mas não encontra o alvo. A bola sai e a defesa respira aliviada."
        );
    }

    private static void corner(MatchStateDto m, Team a, Team d, Random r) {
        a.stats.corners++;
        var taker = active(a)
            .stream()
            .max(Comparator.comparingDouble(p -> skill(p, "corners", "crossing")))
            .orElse(null);
        event(
            m,
            "CORNER",
            a,
            taker,
            null,
            "Escanteio para " +
                a.name +
                ". " +
                (taker == null
                    ? "O time prepara a cobrança."
                    : taker.name +
                      " posiciona a bola e observa os companheiros na área, enquanto a defesa ajusta a marcação.")
        );
        if (taker != null && r.nextDouble() < .25 + skill(taker, "corners", "crossing") * .002) shot(
            m,
            a,
            d,
            r,
            true,
            false,
            taker
        );
    }

    private static void penalty(MatchStateDto m, Team attack, Random r) {
        var defend = attack == m.home ? m.away : m.home;
        var taker = active(attack)
            .stream()
            .filter(p -> Objects.equals(attack.penaltyTaker, p.id))
            .findFirst()
            .orElseGet(() ->
                active(attack)
                    .stream()
                    .max(Comparator.comparingDouble(p -> skill(p, "penalties", "composure")))
                    .orElse(null)
            );
        if (taker == null) return;
        var keeper = active(defend)
            .stream()
            .filter(p -> p.position.equals("GOL"))
            .findFirst()
            .orElse(null);
        double chance = clamp(
            .76 +
                (skill(taker, "penalties", "composure") -
                    (keeper == null ? 15 : skill(keeper, "reflexes", "one_on_ones"))) *
                    .002,
            .5,
            .93
        );
        attack.stats.expectedGoals += chance;
        attack.stats.shots++;
        taker.shots++;
        if (r.nextDouble() < chance) {
            attack.stats.shotsOnTarget++;
            attack.stats.penaltiesScored++;
            goal(m, attack, taker, null, chance, true);
        } else if (r.nextBoolean()) {
            attack.stats.shotsOnTarget++;
            defend.stats.saves++;
            if (keeper != null) keeper.saves++;
            event(m, "PENALTY_SAVED", defend, keeper, taker, "Pênalti defendido! Cobrança de " + taker.name + ".");
        } else event(m, "PENALTY_MISSED", attack, taker, null, taker.name + " perde o pênalti para fora.");
    }

    private static void goal(MatchStateDto m, Team team, Player scorer, Player assist, double xg, boolean penalty) {
        team.goals++;
        scorer.goals++;
        if (assist != null && assist.id == scorer.id) assist = null;
        if (assist != null) assist.assists++;
        int sequence = m.events.size() + 1;
        if (VarRules.needsReview(m.seed, sequence)) {
            var review = new VarReview();
            review.goalSequence = sequence;
            review.teamId = team.id;
            review.playerId = scorer.id;
            review.assistId = assist == null ? null : assist.id;
            review.expectedGoals = xg;
            review.penalty = penalty;
            m.varReview = review;
            event(
                m,
                "GOAL_PENDING",
                team,
                scorer,
                assist,
                "Possível gol de " + scorer.name + ". Lance em análise pelo VAR."
            );
            event(m, "VAR_REVIEW", team, scorer, null, "VAR: revisão do lance de gol.");
        } else event(
            m,
            "GOAL",
            team,
            scorer,
            assist,
            "GOL de " +
                scorer.name +
                "! " +
                (assist == null
                    ? "A finalização vence a defesa"
                    : "Após o passe de " + assist.name + ", o arremate vence a defesa") +
                " e a torcida de " +
                team.name +
                " explode nas arquibancadas!"
        );
    }

    // Preserve the outcome RNG draw previously used for random assists; assists now follow the narrated play.
    private static Player assistProvider(Team team, Random random, boolean corner, boolean freeKick, Player provider) {
        if (!corner && !freeKick) pick(team, random, false);
        return freeKick ? null : provider;
    }

    private static Player pick(Team t, Random r, boolean keeper) {
        var players = active(t)
            .stream()
            .filter(p -> keeper || !p.position.equals("GOL"))
            .toList();
        if (players.isEmpty()) players = active(t);
        return players.get(r.nextInt(players.size()));
    }

    public static boolean substitute(MatchStateDto m, Team team, long out, long in) {
        var leaving = team.players
            .stream()
            .filter(p -> p.id == out)
            .findFirst()
            .orElse(null);
        var entering = team.players
            .stream()
            .filter(p -> p.id == in)
            .findFirst()
            .orElse(null);
        boolean newWindow = m.period >= 2 && !m.halfTimeBreak && team.lastWindowMinute != m.minute;
        if (
            leaving == null ||
            entering == null ||
            leaving.withdrawn ||
            leaving.sentOff ||
            (!leaving.onPitch && !leaving.injured) ||
            !leaving.entered ||
            entering.entered ||
            entering.onPitch ||
            entering.injured ||
            entering.sentOff ||
            team.substitutions >= (m.period >= 3 ? 6 : 5) ||
            (newWindow && team.substitutionWindows >= (m.period >= 3 ? 4 : 3))
        ) return false;
        leaving.onPitch = false;
        leaving.withdrawn = true;
        entering.slot = leaving.slot;
        if (leaving.assignedPosition != null) PlayerPositionRules.assign(entering, leaving.assignedPosition);
        if (team.tacticalPlan != null && team.tacticalPlan.positions != null) {
            var positions = new LinkedHashMap<>(team.tacticalPlan.positions);
            var position = positions.remove(leaving.id);
            if (position != null) positions.put(
                entering.id,
                entering.position.equals("GOL") ? new br.com.manager.models.dto.PitchPositionDto(50.0, 89.0) : position
            );
            team.tacticalPlan.positions = positions;
        }
        entering.onPitch = true;
        entering.entered = true;
        team.substitutions++;
        if (newWindow) {
            team.substitutionWindows++;
            team.lastWindowMinute = m.minute;
        }
        event(m, "SUBSTITUTION", team, entering, leaving, "Entra " + entering.name + ", sai " + leaving.name + ".");
        return true;
    }

    private static void aiChanges(MatchStateDto m, Team team, Random r) {
        if (team.human) return;
        Team rival = team == m.home ? m.away : m.home;
        String next =
            team.goals < rival.goals && m.minute >= 65
                ? "ATTACKING"
                : team.goals > rival.goals && m.minute >= 78
                  ? "DEFENSIVE"
                  : team.mentality;
        if (!next.equals(team.mentality)) {
            team.mentality = next;
            event(m, "TACTICS", team, null, null, team.name + " mudou a postura tática.");
        }
        if (team.substitutions >= 5) return;
        var outgoing = team.players
            .stream()
            .filter(
                p ->
                    p.entered &&
                    !p.withdrawn &&
                    !p.sentOff &&
                    (p.injured || (p.onPitch && m.minute >= 60 && p.fitness < 82))
            )
            .sorted(Comparator.comparingInt(p -> p.injured ? -1 : p.fitness))
            .findFirst()
            .orElse(null);
        if (outgoing == null || (!outgoing.injured && r.nextDouble() > .15)) return;
        var incoming = team.players
            .stream()
            .filter(p -> !p.entered && !p.injured && p.position.equals(outgoing.position))
            .max(Comparator.comparingInt(p -> p.ability))
            .orElse(null);
        if (incoming != null) substitute(m, team, outgoing.id, incoming.id);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
