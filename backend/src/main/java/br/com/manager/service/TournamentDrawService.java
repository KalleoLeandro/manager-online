package br.com.manager.service;

import br.com.manager.models.dto.TournamentDrawDto;
import br.com.manager.models.dto.TournamentDrawDto.*;
import br.com.manager.models.dto.TournamentDtos.Edition;
import br.com.manager.repository.*;
import br.com.manager.utils.TournamentDrawSchedule;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TournamentDrawService {

    private final TournamentDrawRepository repository;
    private final TournamentRepository tournaments;
    private final org.springframework.context.ApplicationEventPublisher events;

    public TournamentDrawService(
        TournamentDrawRepository repository,
        TournamentRepository tournaments,
        org.springframework.context.ApplicationEventPublisher events
    ) {
        this.repository = repository;
        this.tournaments = tournaments;
        this.events = events;
    }

    public void record(String id, Edition edition, int stage) {
        if (!TournamentDrawSchedule.ceremony(edition, stage)) return;
        var teams = new HashMap<Long, String>();
        tournaments.teams(id, edition.id()).forEach(t -> teams.put(t.id(), t.name()));
        var games = tournaments
            .games(id, edition.id())
            .stream()
            .filter(g -> g.stage() == stage)
            .toList();
        var rows = new ArrayList<Row>();
        if (edition.rules().stages().get(stage).format().equals("KNOCKOUT")) {
            for (var tie : tournaments.ties(edition.id(), stage))
                rows.add(
                    new Row(
                        new Club(tie.home(), teams.get(tie.home()), "C"),
                        List.of(new Club(tie.away(), teams.get(tie.away()), "V"))
                    )
                );
        } else {
            var participants = new LinkedHashSet<Long>();
            games.forEach(g -> {
                participants.add(g.home());
                participants.add(g.away());
            });
            for (long club : participants) {
                var opponents = new ArrayList<Club>();
                for (var g : games)
                    if (g.home() == club || g.away() == club) {
                        long other = g.home() == club ? g.away() : g.home();
                        opponents.add(new Club(other, teams.get(other), g.home() == club ? "C" : "V"));
                    }
                rows.add(new Row(new Club(club, teams.get(club), ""), opponents));
            }
        }
        repository.record(
            id,
            edition.id(),
            stage,
            new TournamentDrawDto(
                0,
                edition.name(),
                edition.rules().stages().get(stage).label(),
                TournamentDrawSchedule.date(edition, stage),
                Instant.now().toEpochMilli() + 3000,
                List.copyOf(rows)
            )
        );
        events.publishEvent(new br.com.manager.models.events.TournamentDrawCreatedEvent(id, edition.id(), stage));
    }

    public List<TournamentDrawDto> pending(String id, String member) {
        return repository.pending(id, member);
    }

    public void acknowledge(String id, String member, long draw) {
        if (!repository.acknowledge(id, member, draw)) throw new br.com.manager.exception.DefaultErrorException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Sorteio não encontrado nesta liga."
        );
    }
}
