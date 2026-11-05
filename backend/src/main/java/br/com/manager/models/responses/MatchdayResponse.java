package br.com.manager.models.responses;

import br.com.manager.models.dto.*;
import java.util.List;

public record MatchdayResponse(
    String date,
    long ownTeamId,
    String leagueStatus,
    List<MatchSummaryDto> matches,
    MatchView selected,
    List<Participant> participants,
    int speedPercent,
    boolean canChangeSpeed,
    boolean localMultiplayer
) {
    public record Participant(String name, long teamId, String teamName, String status, Long fixtureId) {}

    public record MatchView(
        long fixtureId,
        long revision,
        int minute,
        int period,
        int addedFirst,
        int addedSecond,
        String status,
        String pauseReason,
        long pausedUntil,
        boolean canPause,
        Long penaltyTeam,
        MatchStateDto.Team home,
        MatchStateDto.Team away,
        List<MatchStateDto.Event> events,
        long goalCelebrationRemainingMillis,
        int celebratingGoalSequence,
        boolean awaitingCoachDecision,
        boolean canResume,
        List<Long> pendingCoachTeams,
        VarReviewResponse varReview,
        String competitionName,
        Integer homePenalties,
        Integer awayPenalties,
        boolean cupMatch
    ) {}
}
