package br.com.manager.utils;

import br.com.manager.models.dto.TournamentDtos.*;
import java.util.*;

public final class TournamentStandings {
    private TournamentStandings() {}
    public record Row(Entrant team,int played,int points,int wins,int draws,int losses,int gf,int ga,int awayGoals,int awayWins,int opponentsPoints,int opponentsDifference,int opponentsGoals) {}
    public static List<Row> rank(List<Entrant> entrants,List<Game> games,boolean uefa) {
        var values=new HashMap<Long,int[]>();var opponents=new HashMap<Long,List<Long>>();entrants.forEach(t->{values.put(t.id(),new int[9]);opponents.put(t.id(),new ArrayList<>());});
        for(var g:games)if(g.status().equals("PLAYED")&&values.containsKey(g.home())&&values.containsKey(g.away())){
            var h=values.get(g.home());var a=values.get(g.away());h[0]++;a[0]++;h[5]+=g.homeGoals();h[6]+=g.awayGoals();a[5]+=g.awayGoals();a[6]+=g.homeGoals();a[7]+=g.awayGoals();
            if(g.homeGoals()>g.awayGoals()){h[1]+=3;h[2]++;a[4]++;}else if(g.homeGoals()<g.awayGoals()){a[1]+=3;a[2]++;a[8]++;h[4]++;}else{h[1]++;a[1]++;h[3]++;a[3]++;}
            opponents.get(g.home()).add(g.away());opponents.get(g.away()).add(g.home());
        }
        var rows=new ArrayList<Row>();for(var team:entrants){var v=values.get(team.id());int op=0,od=0,og=0;for(var opponent:opponents.get(team.id())){var x=values.get(opponent);op+=x[1];od+=x[5]-x[6];og+=x[5];}rows.add(new Row(team,v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],op,od,og));}
        Comparator<Row> comparator=Comparator.comparingInt(Row::points).reversed().thenComparing(Comparator.comparingInt((Row r)->r.gf()-r.ga()).reversed()).thenComparing(Comparator.comparingInt(Row::gf).reversed()).thenComparing(Comparator.comparingInt(Row::awayGoals).reversed());
        if(uefa)comparator=comparator.thenComparing(Comparator.comparingInt(Row::wins).reversed()).thenComparing(Comparator.comparingInt(Row::awayWins).reversed()).thenComparing(Comparator.comparingInt(Row::opponentsPoints).reversed()).thenComparing(Comparator.comparingInt(Row::opponentsDifference).reversed()).thenComparing(Comparator.comparingInt(Row::opponentsGoals).reversed());
        rows.sort(comparator.thenComparingInt(r->r.team().rank()).thenComparingLong(r->r.team().id()));return rows;
    }
}
