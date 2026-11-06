import {Component,inject} from '@angular/core';
import {LeagueSessionService} from '../../services/league-session.service';
@Component({selector:'app-league-exit',template:`<button type="button" (click)="logout()">Sair da liga</button>`,styles:`button{padding:11px 16px;border:1px solid #a7bdb0;border-radius:7px;background:#f8fbf8;color:#234c38;font:600 12px Arial,sans-serif;cursor:pointer}button:focus-visible{outline:2px solid #53806a;outline-offset:3px}`})
export class LeagueExit {
 private readonly session=inject(LeagueSessionService);
 logout(){if(window.confirm('Sair desta liga e voltar à lista? Seu acesso ficará salvo. Partidas em andamento continuam no servidor.'))this.session.logout();}
}
