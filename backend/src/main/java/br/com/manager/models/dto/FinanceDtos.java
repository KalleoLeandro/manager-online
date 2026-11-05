package br.com.manager.models.dto;

import java.time.LocalDate;

public final class FinanceDtos {

    private FinanceDtos() {}

    public record Contract(
        long playerId,
        String name,
        Long teamId,
        String teamName,
        String position,
        int ability,
        Integer age,
        long marketValue,
        long salaryMonthly,
        long expectedSalary,
        LocalDate startDate,
        LocalDate endDate,
        long signingBonus,
        long appearanceBonus,
        long goalBonus,
        long titleBonus,
        boolean estimated,
        boolean humanClub
    ) {}

    public record Objective(
        int rank,
        String label,
        long maximumFunding,
        int boardTrustDelta,
        int fanTrustDelta,
        String reaction
    ) {}

    public record Plan(
        long id,
        long seasonId,
        boolean approved,
        int promisedRank,
        long baseFunding,
        long transferBudget,
        long salaryBudget,
        long infrastructureBudget,
        long miscellaneousBudget,
        long reserveBudget,
        long transferSpent,
        Integer finalRank,
        Boolean promiseMet,
        boolean settled,
        Integer expectedRank
    ) {}

    public record Ledger(long id, LocalDate date, String category, String description, long amount) {}

    public record History(String seasonLabel, int promisedRank, int finalRank, boolean promiseMet) {}

    public record Offer(
        long id,
        long playerId,
        String playerName,
        long buyerId,
        String buyerName,
        Long sellerId,
        String sellerName,
        long seasonId,
        String status,
        int clubMeter,
        int playerMeter,
        long fee,
        long salaryMonthly,
        int months,
        long signingBonus,
        long appearanceBonus,
        long goalBonus,
        long titleBonus,
        LocalDate offeredDate,
        LocalDate decisionDate,
        LocalDate expiresDate,
        boolean sellerApproved,
        String feedback,
        int revision
    ) {}

    public record Account(LocalDate lastProcessedDate, long staffSalaryMonthly, int boardTrust, int fanTrust) {}
}
