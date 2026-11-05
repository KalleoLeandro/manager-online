package br.com.manager.models.responses;

import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.dto.NationalSeasonDto;
import java.time.LocalDate;
import java.util.List;

public record FinanceStateResponse(
    LocalDate date,
    long teamId,
    String currency,
    long annualRevenue,
    NationalSeasonDto season,
    Plan plan,
    long cash,
    long payrollMonthly,
    long staffSalaryMonthly,
    long committedWagesMonthly,
    long reservedTransfers,
    long transferAvailable,
    int boardTrust,
    int fanTrust,
    int expectedRank,
    List<Objective> objectives,
    List<History> history,
    List<Ledger> ledger,
    boolean approvalRequired
) {}
