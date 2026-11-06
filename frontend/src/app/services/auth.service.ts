import {LeagueSessionService} from './league-session.service';
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { readLocal, writeLocal } from '../utils/local-storage';

export interface Session { id: string; name: string; accessToken: string; photoUrl?: string|null; firstName?:string; lastName?:string|null }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly leagueSession=inject(LeagueSessionService);
  session(): Session | null { return readLocal<Session>('manager-session'); }
  logout(){const current=this.session();return this.http.post<void>('/api/auth/logout',{}, {headers:current?{Authorization:'Bearer '+current.accessToken}:{}}).pipe(tap(()=>{
    try{if(current)for(const key of Object.keys(localStorage))if(key.startsWith(current.id+':manager-'))localStorage.removeItem(key);localStorage.removeItem('manager-session');localStorage.removeItem('manager-name');}catch{}
    this.leagueSession.clear();location.replace(location.pathname+location.search+'#login');
  }));}
  login(email: string, password: string) {
    return this.http.post<Session>('/api/auth/login', { email: email.trim(), password }).pipe(tap(session => {
      writeLocal('manager-session',session); writeLocal('manager-name',session.name);
    }));
  }
}
