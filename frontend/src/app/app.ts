import {LeagueSessionService} from './services/league-session.service';
import { MatchTacticsPage } from './pages/match-tactics/match-tactics';
import { Component, HostListener, inject, signal } from '@angular/core';
import { Login } from './pages/login/login';
import { CreateSave } from './pages/create-save/create-save';
import { Lobby } from './pages/lobby/lobby';
import { currentPath } from './utils/navigation';
import { TeamManagementPage } from './pages/team-management/team-management';
import { LeaguesPage } from './pages/leagues/leagues';
import { MatchdayPage } from './pages/matchday/matchday';

@Component({ selector: 'app-root', imports: [Login, CreateSave, Lobby, TeamManagementPage,LeaguesPage,MatchdayPage,MatchTacticsPage], template: `
  @if(session.notice()){<p role="status" style="position:fixed;z-index:9999;top:10px;left:50%;transform:translateX(-50%);padding:12px 20px;border-radius:8px;background:#fff3cf;color:#604e28;box-shadow:0 3px 12px #0003">{{session.notice()}} <button type="button" (click)="session.logout()">Sair da liga</button> <button type="button" (click)="session.notice.set('')">Fechar</button></p>}
  @for (route of [path()]; track route) {
    @if (route === 'leagues') { <app-leagues /> }
    @else if (route.startsWith('match-tactics/')) { <app-match-tactics /> }
    @else if (route.startsWith('match/')) { <app-matchday /> }
    @else if (route.startsWith('team/')) { <app-team-management /> }
    @else if (route.startsWith('lobby/')) { <app-lobby /> }
    @else if (route === 'new-league' || route === 'new-save' || route.startsWith('join')) { <app-create-save /> }
    @else { <app-login /> }
  }` })
export class App {
  readonly session=inject(LeagueSessionService);
  readonly path=signal(this.session.resolve(currentPath()));
  constructor(){if(currentPath()!==this.path())history.replaceState(null,'','#'+this.path());}
  @HostListener('window:storage',['$event']) onSessionChange(event:StorageEvent):void{if(event.key==='manager-session'&&event.oldValue&&!event.newValue){this.session.clear();location.replace(location.pathname+location.search+'#login');}}
  @HostListener('window:hashchange') onNavigation(): void { const route=this.session.resolve(currentPath());if(route!==currentPath())history.replaceState(null,'','#'+route);this.path.set(route); window.scrollTo(0,0); }
}
