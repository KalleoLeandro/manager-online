import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { LeagueCalendar, LeagueCalendarService } from '../../services/league-calendar.service';
import { Room } from '../../services/save.service';
import { ManagementPage } from '../../utils/management-navigation';
import { dateLabel, weekDates } from '../../utils/game-calendar';

@Component({selector:'app-management-calendar',templateUrl:'./management-calendar.html',styleUrl:'./management-calendar.css'})
export class ManagementCalendar {
  readonly leagueId=input.required<string>();readonly room=input.required<Room>();readonly navigate=output<ManagementPage>();
  private readonly api=inject(LeagueCalendarService);
  readonly data=signal<LeagueCalendar|null>(null);readonly selected=signal('');readonly error=signal('');
  readonly loading=signal(true);private readonly retry=signal(0);
  private readonly round=computed(()=>this.room().roundNumber);
  readonly days=computed(()=>this.data()?weekDates(this.data()!.weekStart):[]);
  readonly events=computed(()=>{const room=this.room(),ready=room.members.filter(m=>m.ready).length;return (this.data()?.events||[]).map(event=>event.type==='PREPARATION'?{...event,title:'Preparação da rodada '+room.roundNumber,description:`${ready} de ${room.capacity} participantes prontos.`,status:room.status==='READY'?'READY':'WAITING'}:event);});
  readonly selectedEvents=computed(()=>this.events().filter(event=>event.date===this.selected()));
  readonly label=dateLabel;
  constructor(){effect(onCleanup=>{const id=this.leagueId();this.round();this.retry();this.loading.set(true);this.error.set('');const request=this.api.week(id).subscribe({next:data=>{this.data.set(data);this.selected.set(data.currentDate);this.loading.set(false);},error:()=>{this.error.set('Não foi possível carregar o calendário.');this.loading.set(false);}});onCleanup(()=>request.unsubscribe());});}
  count(date:string):number{return this.events().filter(event=>event.date===date).length;}
  reload():void{this.retry.update(value=>value+1);}
  eventLabel(type:string):string{return ({PREPARATION:'Rodada',RECOVERY:'Retorno previsto',MATCH:'Partida',TRAINING:'Treino',TRANSFER:'Mercado',CLUB:'Clube'} as Record<string,string>)[type]||'Evento';}
}
