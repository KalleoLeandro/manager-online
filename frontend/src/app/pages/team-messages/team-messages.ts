import { Component, computed, input, output, signal } from '@angular/core';
import { TeamManagement } from '../../services/management.service';
import { Room } from '../../services/save.service';
import { available } from '../../utils/player-attributes';
import { ManagementPage } from '../../utils/management-navigation';

interface ClubMessage { id: string; sender: string; title: string; body: string; action: string; page: ManagementPage }

@Component({selector:'app-team-messages',templateUrl:'./team-messages.html',styleUrl:'./team-messages.css'})
export class TeamMessagesPage {
  readonly club = input.required<TeamManagement>();
  readonly room = input.required<Room>();
  readonly dirty = input(false);
  readonly coachFirstName=input<string|null|undefined>(null);
  readonly navigate = output<ManagementPage>();
  readonly selectedId = signal('welcome');
  readonly messages = computed<ClubMessage[]>(() => {
    const club=this.club(),room=this.room();
    const ready=room.members.filter(m=>m.ready).length;
    const unavailable=club.players.filter(p=>!available(p));
    return [
      {id:'welcome',sender:'Central do clube',title:`Seu trabalho no ${club.teamName}`,body:`${this.coachFirstName()?'Bem-vindo, '+this.coachFirstName()+'. ':''}Você está no comando do ${club.teamName} na liga ${room.name}. Consulte as fichas dos atletas, escolha a formação e salve sua escalação antes de confirmar a preparação.`,action:'Conhecer o elenco',page:'roster'},
      {id:'preparation',sender:'Comissão técnica',title:this.dirty()?'Sua escalação tem alterações pendentes':club.version===0?'Prepare o time para a rodada':'Escalação salva',body:this.dirty()?'Você alterou a equipe e ainda precisa salvar. As escolhas são mantidas enquanto navega pelas seções desta central.':club.version===0?`A formação ${club.formation} foi sugerida para começar. Confira os titulares e ajuste a estratégia da equipe.`:`A formação ${club.formation} está salva nesta liga. Você pode revisar o time antes de ficar pronto para a rodada.`,action:'Revisar escalação',page:'tactics'},
      {id:'condition',sender:'Condição do elenco',title:unavailable.length?`${unavailable.length} atleta(s) indisponível(is)`:'Elenco disponível para jogar',body:unavailable.length?`${unavailable.map(p=>p.name).join(', ')}. Confira as fichas e substitua os titulares sem condição de jogo.`:'Nenhum atleta está bloqueado por lesão ativa ou condição física baixa. Consulte as fichas para comparar capacidade base e rendimento atual.',action:'Ver condição dos atletas',page:'roster'},
      {id:'league',sender:'Central da liga',title:`Preparação da rodada ${room.roundNumber}`,body:`${room.members.length} de ${room.capacity} participantes na liga. ${ready} de ${room.capacity} estão prontos.${room.capacity===1?' Você pode jogar sozinho e decidir quando confirmar a preparação.':' Os participantes podem preparar seus times ao mesmo tempo.'}`,action:'Ver visão geral',page:'overview'},
    ];
  });
  readonly selected = computed(()=>this.messages().find(m=>m.id===this.selectedId())||this.messages()[0]);
}
