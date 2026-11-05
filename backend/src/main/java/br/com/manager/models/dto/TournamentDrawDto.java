package br.com.manager.models.dto;
import java.time.LocalDate;
import java.util.List;
public record TournamentDrawDto(long id,String competition,String stage,LocalDate date,long startedAt,List<Row> rows) {
    public record Club(long id,String name,String venue) {}
    public record Row(Club club,List<Club> opponents) {}
}
