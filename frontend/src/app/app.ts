import { Component, HostListener, signal } from '@angular/core';
import { Login } from './pages/login/login';
import { CreateSave } from './pages/create-save/create-save';
import { Lobby } from './pages/lobby/lobby';
import { currentPath } from './utils/navigation';
import { TeamManagementPage } from './pages/team-management/team-management';
import { LeaguesPage } from './pages/leagues/leagues';

@Component({ selector: 'app-root', imports: [Login, CreateSave, Lobby, TeamManagementPage,LeaguesPage], template: `
  @for (route of [path()]; track route) {
    @if (route === 'leagues') { <app-leagues /> }
    @else if (route.startsWith('team/')) { <app-team-management /> }
    @else if (route.startsWith('lobby/')) { <app-lobby /> }
    @else if (route === 'new-league' || route === 'new-save' || route.startsWith('join')) { <app-create-save /> }
    @else { <app-login /> }
  }` })
export class App {
  readonly path = signal(currentPath());
  @HostListener('window:hashchange') onNavigation(): void { this.path.set(currentPath()); window.scrollTo(0,0); }
}
