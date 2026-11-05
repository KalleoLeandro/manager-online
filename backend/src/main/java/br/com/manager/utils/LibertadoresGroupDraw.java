package br.com.manager.utils;

import br.com.manager.models.dto.TournamentDtos.Entrant;
import java.util.*;

/** Four pots of eight; association exceptions apply only to qualifiers from the preliminary phases. */
public final class LibertadoresGroupDraw {
    private LibertadoresGroupDraw() {}
    public static Map<String,List<Entrant>> draw(List<Entrant> entrants,long seed) {
        if(entrants.size()!=32||entrants.stream().map(Entrant::id).distinct().count()!=32)throw new IllegalArgumentException("A fase de grupos exige 32 clubes distintos.");
        var sorted=new ArrayList<>(entrants);sorted.sort(Comparator.comparingInt((Entrant e)->e.stage().startsWith("PRE")?100000+e.rank():e.rank()));
        var groups=new ArrayList<List<Entrant>>();for(int i=0;i<8;i++)groups.add(new ArrayList<>());
        groups.getFirst().add(sorted.getFirst());var rest=new ArrayList<>(sorted.subList(1,8));Collections.shuffle(rest,new Random(seed));for(int i=1;i<8;i++)groups.get(i).add(rest.get(i-1));
        if(!assign(1,sorted,groups,new Random(seed^0x47524F5550L)))throw new IllegalStateException("Não foi possível respeitar os potes e as restrições da CONMEBOL.");
        var result=new LinkedHashMap<String,List<Entrant>>();for(int i=0;i<8;i++)result.put("Grupo "+(char)('A'+i),List.copyOf(groups.get(i)));return result;
    }
    private static boolean assign(int pot,List<Entrant> clubs,List<List<Entrant>> groups,Random random){
        if(pot==4)return true;var pool=new ArrayList<>(clubs.subList(pot*8,pot*8+8));Collections.shuffle(pool,random);return place(pot,0,pool,clubs,groups,random);
    }
    private static boolean place(int pot,int index,List<Entrant> pool,List<Entrant> clubs,List<List<Entrant>> groups,Random random){
        if(index==8)return assign(pot+1,clubs,groups,random);
        var club=pool.get(index);var order=new ArrayList<>(java.util.stream.IntStream.range(0,8).boxed().toList());Collections.shuffle(order,random);
        for(int g:order){var group=groups.get(g);if(group.size()!=pot)continue;var same=group.stream().filter(t->Objects.equals(t.association(),club.association())).toList();
            if(same.size()>1||same.size()==1&&!club.stage().startsWith("PRE")&&!same.getFirst().stage().startsWith("PRE"))continue;
            group.add(club);if(place(pot,index+1,pool,clubs,groups,random))return true;group.removeLast();
        }return false;
    }
}
