package br.com.manager.service;

import br.com.manager.models.dto.FinanceDtos.Contract;
import br.com.manager.models.requests.SeasonBudgetRequest;
import br.com.manager.models.responses.FinanceStateResponse;
import br.com.manager.repository.FinanceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceFacadeService {

    private final SaveService saves;
    private final FinanceService finances;
    private final FinanceDailyService daily;
    private final FinanceRepository repository;

    public FinanceFacadeService(
        SaveService saves,
        FinanceService finances,
        FinanceDailyService daily,
        FinanceRepository repository
    ) {
        this.saves = saves;
        this.finances = finances;
        this.daily = daily;
        this.repository = repository;
    }

    private long team(String id, String token) {
        saves.authorizedRoom(id, token);
        return saves.realtimeMember(id, token.substring(7)).teamId();
    }

    @Transactional
    public FinanceStateResponse state(String id, String token) {
        long team = team(id, token);
        finances.lock(id);
        daily.processThrough(id, team, finances.date(id, team));
        return finances.state(id, team);
    }

    @Transactional
    public FinanceStateResponse approve(String id, String token, SeasonBudgetRequest input) {
        return finances.approve(id, team(id, token), input);
    }

    @Transactional
    public List<Contract> contracts(String id, String token) {
        long team = team(id, token);
        finances.lock(id);
        daily.processThrough(id, team, finances.date(id, team));
        return repository.contracts(id, team);
    }
}
