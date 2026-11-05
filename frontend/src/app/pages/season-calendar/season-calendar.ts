import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LeagueCalendarService, SeasonCalendar, CalendarEvent } from '../../services/league-calendar.service';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { GameIcon } from '../../components/game-icon/game-icon';
import { dateLabel } from '../../utils/game-calendar';

@Component({selector:'app-season-calendar',imports:[FormsModule,ClubCrest,GameIcon],templateUrl:'./season-calendar.html',styleUrl:'./season-calendar.css'})
export class SeasonCalendarPage {
  readonly revision=input(0);readonly leagueId=input.required<string>();readonly gameRound=input.required<number>();readonly championship=output<void>();
  private readonly api=inject(LeagueCalendarService);private readonly retry=signal(0);
  readonly data=signal<SeasonCalendar|null>(null);readonly loading=signal(true);readonly error=signal('');
  readonly month=signal('');readonly selected=signal('');readonly type=signal('');readonly label=dateLabel;
  readonly months=computed(()=>{const d=this.data();if(!d)return [];const result:string[]=[];let month=d.seasonStart.slice(0,7);while(month<=d.seasonEnd.slice(0,7)){result.push(month);month=this.shift(month,1);}return result;});
  readonly types=computed(()=>[...new Set(this.data()?.events.map(e=>e.type)||[])]);
  readonly filtered=computed(()=>(this.data()?.events||[]).filter(e=>!this.type()||e.type===this.type()));
  readonly days=computed(()=>{if(!this.month())return [];const first=new Date(this.month()+'-01T12:00:00Z'),offset=(first.getUTCDay()+6)%7,last=new Date(first);last.setUTCMonth(last.getUTCMonth()+1);last.setUTCDate(0);return Array.from({length:Math.ceil((offset+last.getUTCDate())/7)*7},(_,i)=>{const date=new Date(first);date.setUTCDate(1-offset+i);return date.toISOString().slice(0,10);});});
  readonly agenda=computed(()=>this.filtered().filter(e=>this.selected()?e.date===this.selected():e.date.startsWith(this.month())));
  constructor(){effect(onCleanup=>{const id=this.leagueId();this.gameRound();this.revision();this.retry();this.loading.set(true);this.error.set('');const request=this.api.season(id).subscribe({next:data=>{this.data.set(data);this.month.set(data.currentDate.slice(0,7));this.selected.set('');this.loading.set(false);},error:()=>{this.error.set('Não foi possível carregar o calendário da temporada.');this.loading.set(false);}});onCleanup(()=>request.unsubscribe());});}
  events(date:string):CalendarEvent[]{return this.filtered().filter(e=>e.date===date);}
  matches(date:string):CalendarEvent[]{return this.events(date).filter(e=>e.type==='MATCH'&&e.opponentTeamId!=null);}
  dayLabel(date:string):string{return this.label(date,{weekday:'long',day:'numeric',month:'long',year:'numeric'})+', '+this.events(date).length+' evento(s)';}
  changeMonth(month:string):void{this.month.set(month);this.selected.set('');}
  move(amount:number):void{const target=this.shift(this.month(),amount);if(this.months().includes(target))this.changeMonth(target);}
  today():void{if(this.data()){this.month.set(this.data()!.currentDate.slice(0,7));this.selected.set(this.data()!.currentDate);}}
  reload():void{this.retry.update(v=>v+1);}
  eventLabel(type:string):string{return ({MATCH:'Partida',PREPARATION:'Preparação',RECOVERY:'Retorno previsto',TRAINING:'Treino',TRANSFER:'Mercado',CLUB:'Clube'} as Record<string,string>)[type]||'Evento';}
  statusLabel(status:string):string{return ({COMPLETED:'Concluído',PLAYED:'Concluído',CANCELLED:'Cancelado',READY:'Pronto',WAITING:'Aguardando',LIVE:'Em andamento'} as Record<string,string>)[status]||'Agendado';}
  private shift(month:string,amount:number):string{const date=new Date(month+'-01T12:00:00Z');date.setUTCMonth(date.getUTCMonth()+amount);return date.toISOString().slice(0,7);}
}
