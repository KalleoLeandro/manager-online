package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.responses.NationalChampionshipResponse;
import br.com.manager.repository.NationalSeasonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NationalChampionshipService {

    private final SaveService saves;
    private final NationalSeasonService seasons;
    private final NationalSeasonRepository repository;

    public NationalChampionshipService(
        SaveService saves,
        NationalSeasonService seasons,
        NationalSeasonRepository repository
    ) {
        this.saves = saves;
        this.seasons = seasons;
        this.repository = repository;
    }

    @Transactional
    public NationalChampionshipResponse championship(String id, String token, Integer year) {
        var room = saves.authorizedRoom(id, token);
        var member = saves.realtimeMember(id, token.substring(7));
        var date = repository.start(id).plusDays(Math.max(0, repository.teamDay(id,member.teamId()) - 1L));
        seasons.ensureForDate(id, date);
        var history = repository.seasons(id, member.teamId());
        var selected = history
            .stream()
            .filter(s -> year != null ? s.year() == year : !date.isBefore(s.firstDate()) && !date.isAfter(s.lastDate()))
            .findFirst()
            .orElse(null);
        if (selected == null && year == null) selected = history
            .stream()
            .filter(s -> !s.firstDate().isBefore(date))
            .min(java.util.Comparator.comparing(s -> s.firstDate()))
            .orElse(history.isEmpty() ? null : history.getFirst());
        if (selected == null) throw new DefaultErrorException(
            HttpStatus.NOT_FOUND,
            "Temporada não encontrada para seu clube."
        );
        return new NationalChampionshipResponse(member.teamId(), selected, history, repository.fixtures(selected.id()));
    }
}
