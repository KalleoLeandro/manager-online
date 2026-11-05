package br.com.manager.service;

import br.com.manager.repository.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceDailyService {

    private final FinanceRepository repository;
    private final FinanceService finances;
    private final TransferService transfers;
    private final DayProgressRepository calendar;

    public FinanceDailyService(
        FinanceRepository repository,
        FinanceService finances,
        TransferService transfers,
        DayProgressRepository calendar
    ) {
        this.repository = repository;
        this.finances = finances;
        this.transfers = transfers;
        this.calendar = calendar;
    }

    @Transactional
    public boolean processThrough(String id, long team, LocalDate date) {
        if (repository.live(id)) return false;
        finances.initialize(id, team);
        finances.prepareComputerBudget(id, team);
        var previous = repository.account(id, team).lastProcessedDate();
        boolean important = false;
        for (var day = previous.plusDays(1); !day.isAfter(date); day = day.plusDays(1)) {
            for (var c : repository.contracts(id, team)) {
                if (!c.endDate().isAfter(day)) {
                    if (!c.humanClub()) {
                        repository.renewComputerContract(id, c, day);
                        continue;
                    }
                    repository.release(id, c);
                    repository.recalculateRating(id, team);
                    calendar.event(
                        id,
                        team,
                        day,
                        "TRANSFER",
                        "Contrato encerrado: " + c.name(),
                        "O atleta está livre no mercado. Revise o elenco e a escalação."
                    );
                    important = true;
                }
            }
            if (day.getDayOfMonth() == day.lengthOfMonth()) {
                long payroll = repository.payroll(id, team);
                if (
                    payroll > 0 &&
                    repository.ledger(
                        id,
                        team,
                        day,
                        "PAYROLL",
                        "Salários de jogadores e staff — " + YearMonth.from(day),
                        -payroll,
                        "PAYROLL:" + YearMonth.from(day)
                    )
                ) {
                    calendar.event(
                        id,
                        team,
                        day,
                        "CLUB",
                        "Folha salarial paga",
                        "Salários mensais debitados do caixa: R$ " + payroll + "."
                    );
                    if (repository.cash(id, team) < 0) calendar.event(
                        id,
                        team,
                        day,
                        "CLUB",
                        "Atenção ao caixa",
                        "O caixa ficou negativo após os salários. Novas contratações dependem da recuperação financeira."
                    );
                }
            }
            repository.processed(id, team, day);
        }
        if (!repository.live(id)) important |= transfers.process(id, team, date);
        if (important) finances.changed(id, team);
        return important;
    }
}
