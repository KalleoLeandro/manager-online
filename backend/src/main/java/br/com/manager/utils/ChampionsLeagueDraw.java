package br.com.manager.utils;

import java.time.LocalDate;
import java.util.*;

/** Draw opponents first, then schedule their regular graph into eight perfect matchings. */
public final class ChampionsLeagueDraw {
    private ChampionsLeagueDraw() {}
    public record Club(long id,String association,int pot) {}
    public record Fixture(int round,LocalDate date,long home,long away) {}
    public static List<Fixture> draw(List<Club> clubs,List<LocalDate> dates,long seed) {
        if(clubs.size()!=36||dates.size()!=8||clubs.stream().map(Club::id).distinct().count()!=36)throw new IllegalArgumentException("A fase de liga exige 36 clubes e oito datas.");
        for(int pot=1;pot<=4;pot++){final int p=pot;if(clubs.stream().filter(c->c.pot()==p).count()!=9)throw new IllegalArgumentException("Cada pote deve conter nove clubes.");}
        for(int retry=0;retry<40;retry++){
            var opponents=new Opponents(clubs,new Random(seed+retry*1009L));
            if(!opponents.search(0))continue;
            var scheduler=new Scheduler(clubs,dates,opponents.host,new Random(seed+retry*2017L));
            if(scheduler.search(0,new boolean[36],0))return List.copyOf(scheduler.result);
        }
        throw new IllegalStateException("Não foi possível concluir um sorteio que respeite as restrições da UEFA.");
    }
    private static final class Opponents {
        final List<Club> clubs;final Random random;final boolean[][] home=new boolean[36][4],away=new boolean[36][4],played=new boolean[36][36],host=new boolean[36][36];
        final int[][] associations=new int[36][36];final int[] associationId=new int[36];int nodes;
        Opponents(List<Club> clubs,Random random){this.clubs=clubs;this.random=random;var ids=new HashMap<String,Integer>();for(int i=0;i<36;i++)associationId[i]=ids.computeIfAbsent(clubs.get(i).association(),k->ids.size());}
        List<Integer> choices(int a,int pot){var options=new ArrayList<Integer>();int ap=clubs.get(a).pot()-1;for(int b=0;b<36;b++)if(a!=b&&clubs.get(b).pot()-1==pot&&!played[a][b]&&!away[b][ap]&&associationId[a]!=associationId[b]&&associations[a][associationId[b]]<2&&associations[b][associationId[a]]<2)options.add(b);return options;}
        boolean search(int games){
            if(games==144)return true;if(++nodes>180000)return false;
            int a=-1,pot=-1;List<Integer> best=null;
            for(int i=0;i<36;i++)for(int p=0;p<4;p++)if(!home[i][p]){var options=choices(i,p);if(options.isEmpty())return false;if(best==null||options.size()<best.size()){a=i;pot=p;best=options;}}
            Collections.shuffle(best,random);int ap=clubs.get(a).pot()-1;
            for(int b:best){home[a][pot]=away[b][ap]=true;played[a][b]=played[b][a]=true;host[a][b]=true;associations[a][associationId[b]]++;associations[b][associationId[a]]++;
                if(search(games+1))return true;
                home[a][pot]=away[b][ap]=false;played[a][b]=played[b][a]=false;host[a][b]=false;associations[a][associationId[b]]--;associations[b][associationId[a]]--;
            }return false;
        }
    }
    private static final class Scheduler {
        final List<Club> clubs;final List<LocalDate> dates;final boolean[][] host,pending=new boolean[36][36];final int[][] venues=new int[36][8];final Random random;
        final List<Fixture> result=new ArrayList<>();int nodes;
        Scheduler(List<Club> clubs,List<LocalDate> dates,boolean[][] host,Random random){this.clubs=clubs;this.dates=dates;this.host=host;this.random=random;for(int a=0;a<36;a++)for(int b=0;b<36;b++)pending[a][b]=host[a][b]||host[b][a];}
        boolean venue(int team,int round,boolean home){int value=home?1:-1;if(round>0&&(round==1||round==7)&&venues[team][round-1]==value)return false;return round<2||venues[team][round-1]!=value||venues[team][round-2]!=value;}
        List<Integer> choices(int a,int round,boolean[] used){var options=new ArrayList<Integer>();for(int b=0;b<36;b++)if(!used[b]&&pending[a][b]&&venue(a,round,host[a][b])&&venue(b,round,host[b][a]))options.add(b);return options;}
        boolean search(int round,boolean[] used,int games){
            if(round==8)return true;if(++nodes>220000)return false;
            if(games==18)return search(round+1,new boolean[36],0);
            int a=-1;List<Integer> best=null;for(int i=0;i<36;i++)if(!used[i]){var options=choices(i,round,used);if(options.isEmpty())return false;if(best==null||options.size()<best.size()){a=i;best=options;}}
            Collections.shuffle(best,random);
            for(int b:best){used[a]=used[b]=true;pending[a][b]=pending[b][a]=false;venues[a][round]=host[a][b]?1:-1;venues[b][round]=host[b][a]?1:-1;
                result.add(new Fixture(round+1,dates.get(round),clubs.get(host[a][b]?a:b).id(),clubs.get(host[a][b]?b:a).id()));
                if(search(round,used,games+1))return true;
                result.removeLast();used[a]=used[b]=false;pending[a][b]=pending[b][a]=true;venues[a][round]=venues[b][round]=0;
            }return false;
        }
    }
}
