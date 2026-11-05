package br.com.manager.utils;
import br.com.manager.models.dto.TournamentDtos.Edition;
import java.time.LocalDate;

public final class TournamentDrawSchedule {
    private TournamentDrawSchedule() {}
    public static LocalDate date(Edition edition,int stage) {
        var rules=edition.rules().stages().get(stage);
        if(edition.key().equals("BRA_CUP")&&stage==0)return LocalDate.of(edition.year(),1,28);
        if(edition.key().equals("CHAMPIONS")&&rules.key().equals("LEAGUE"))return LocalDate.of(edition.year(),8,27);
        return rules.firstDate().minusDays(14);
    }
    public static boolean ceremony(Edition edition,int stage) {
        return !edition.rules().stages().get(stage).drawRule().equals("PREVIOUS_BRACKET");
    }
}
