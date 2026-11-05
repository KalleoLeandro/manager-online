package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import br.com.manager.utils.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TournamentRulesTests {
    @Test void publishedDomesticCupStructuresHaveTheCorrectEntryPoolsAndMatchCounts() {
        for(var rules:List.of(TournamentRulebooks.copaBrasil(2026),TournamentRulebooks.copaRey(2026),TournamentRulebooks.coppaItalia(2026),TournamentRulebooks.dfbPokal(2026)))assertThatCode(()->TournamentRulebooks.validate(rules)).doesNotThrowAnyException();
        var brazil=TournamentRulebooks.copaBrasil(2026);
        assertThat(brazil.stages().stream().mapToInt(s->s.entrants()/2*s.legs()).sum()).isEqualTo(155);
        assertThat(brazil.stages()).allMatch(s->!s.extraTime());
        assertThat(brazil.stages().get(4).newEntrants()).isEqualTo(20);
        assertThat(TournamentRulebooks.coppaItalia(2026).stages()).filteredOn(s->s.legs()==2).hasSize(1);
        assertThat(TournamentRulebooks.copaRey(2026).stages()).filteredOn(s->s.legs()==2).hasSize(1);
    }
    @Test void shootoutsUseAlternatingKicksAndCannotEndTied() {
        for(int seed=0;seed<1000;seed++){
            var score=PenaltyShootoutRules.play(.76,.76,new Random(seed));
            assertThat(score.home()).isNotEqualTo(score.away());
            assertThat(Math.abs(score.homeKicks()-score.awayKicks())).isLessThanOrEqualTo(1);
            assertThat(score.home()).isLessThanOrEqualTo(score.homeKicks());assertThat(score.away()).isLessThanOrEqualTo(score.awayKicks());
        }
    }
    @Test void championsLeagueDrawRespectsPotsAssociationsVenuesAndTheEightMatchdays() {
        String[] countries=("FRA GER ESP ENG ITA ENG ENG ESP ESP GER ITA POR ENG POR ENG BEL ESP NED NED FRA NOR ITA GER ESP TUR UKR TUR CZE SVK GER GRE AUT ITA FRA NOR AZE").split(" ");
        var clubs=IntStream.range(0,36).mapToObj(i->new ChampionsLeagueDraw.Club(i+1,countries[i],i/9+1)).toList();
        var dates=IntStream.range(0,8).mapToObj(i->LocalDate.of(2026,9,8).plusDays(i*14)).toList();
        var draw=ChampionsLeagueDraw.draw(clubs,dates,37);
        assertThat(draw).hasSize(144);
        for(var club:clubs){
            var games=draw.stream().filter(f->f.home()==club.id()||f.away()==club.id()).toList();
            assertThat(games).hasSize(8);assertThat(games.stream().map(ChampionsLeagueDraw.Fixture::round).distinct().count()).isEqualTo(8);
            assertThat(games.stream().filter(g->g.home()==club.id()).count()).isEqualTo(4);
            var opponents=games.stream().map(g->clubs.get((int)(g.home()==club.id()?g.away():g.home())-1)).toList();
            assertThat(opponents).noneMatch(o->o.association().equals(club.association()));
            assertThat(opponents.stream().map(ChampionsLeagueDraw.Club::id).distinct().count()).isEqualTo(8);
            for(int p=1;p<=4;p++){int pot=p;assertThat(opponents.stream().filter(o->o.pot()==pot).count()).isEqualTo(2);}
            for(var association:opponents.stream().map(ChampionsLeagueDraw.Club::association).distinct().toList())assertThat(opponents.stream().filter(o->o.association().equals(association)).count()).isLessThanOrEqualTo(2);
        }
    }
}
