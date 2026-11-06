package br.com.manager.models.dto;

import java.util.*;

/** Persisted versioned simulation state; never returned directly to clients (contains the seed). */
public class MatchStateDto {

    // Old persisted matches may omit the version; they must retain the legacy rules.
    public int engineVersion = 1;
    public long seed, fixtureId, seasonId, revision;
    public String date,
        status = "LIVE",
        pauseReason;
    public int minute,
        period = 1,
        addedFirst = 2,
        addedSecond = 3;
    public long pausedUntil;
    public long goalCelebrationUntil;
    public int celebratingGoalSequence;
    public boolean finishAfterCelebration;
    public boolean cupMatch, decisiveTie, extraTimeAllowed, neutralVenue, periodEndPending;
    public long disciplineSeasonId;
    public int aggregateHomeBefore,
        aggregateAwayBefore,
        suspensionThreshold = 3;
    public Integer homePenalties, awayPenalties;
    public String competitionName;
    public boolean awaitingCoachDecision, halfTimeBreak;
    public List<Long> pendingCoachTeams = new ArrayList<>();
    public Long pausedBy, penaltyTeam;
    public Team home = new Team(),
        away = new Team();
    public List<Event> events = new ArrayList<>();
    public VarReview varReview;

    public static class VarReview {

        public int goalSequence;
        public long teamId, playerId, phaseUntil;
        public Long assistId;
        public boolean penalty, confirmed;
        public double expectedGoals;
        public String phase = "REVIEW",
            reason;
    }

    public static class Team {

        public long id;
        public String name,
            formation = "4-3-3",
            mentality = "BALANCED";
        public boolean human;
        public int goals,
            substitutions,
            substitutionWindows,
            lastWindowMinute = -1,
            pauseCount;
        public Long penaltyTaker;
        public TacticalPlanDto tacticalPlan = new TacticalPlanDto();
        public Touchline touchline = new Touchline();
        public Stats stats = new Stats();
        public List<Player> players = new ArrayList<>();
    }

    /** Match-local discipline; unrelated to career, board or supporter confidence. */
    public static class Touchline {

        // Missing in old match JSON: normalize those previous eight-minute effects once.
        public int commandDuration = 8;

        public int refereeConfidence = 100,
            warnings,
            complaints,
            cooldownUntil,
            effectUntil,
            lastComplaint = -100;
        public boolean dismissed;
        public String command = "NONE",
            refereeMessage = "Sem problemas com a área técnica.";
    }

    public static class Player {

        public long id;
        public String name, position, number;
        public String naturalPosition, assignedPosition;
        public List<String> preferredPositions = new ArrayList<>();
        public int slot = -1;
        public int ability,
            fitness = 100,
            morale = 100,
            sharpness = 100;
        public int yellow, minutes, goals, assists, passesCompleted, passesMissed, shots, saves, injuryDays;
        public int tackles, interceptions, blockedShots, fouls, shotsOnTarget, goalsAgainst;
        public Double rating;
        public int lastTalkPeriod, talkMoraleDelta, talkSharpnessDelta;
        public String halfTimeTalk, talkReaction;
        public boolean onPitch, entered, withdrawn, sentOff, injured;
        public Map<String, Integer> attributes = new LinkedHashMap<>();
    }

    public static class Stats {

        public int possessionTicks, passesCompleted, passesMissed, shots, shotsOnTarget, saves, corners, throwIns, fouls, yellows, reds, offsides, injuries, penalties, penaltiesScored, tackles, interceptions, blockedShots;
        public double expectedGoals;
    }

    public record Event(
        int sequence,
        int minute,
        int period,
        String type,
        long teamId,
        Long playerId,
        Long relatedPlayerId,
        String text
    ) {}
}
