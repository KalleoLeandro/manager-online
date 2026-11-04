import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, map, of, tap, throwError } from 'rxjs';
import { readLocal, writeLocal } from '../utils/local-storage';
import { AuthService } from './auth.service';

export interface Member { id: string; name: string; teamId: number; teamName: string; crestUrl: string | null; owner: boolean; ready: boolean }
export interface Room { id: string; name: string; inviteCode: string; capacity: number; status: string; catalogVersion: string; roundNumber: number; members: Member[]; lastActivityAt: string; inactivityExpiresAt: string }
export interface Access { room: Room; memberId: string; accessToken: string }
export interface CreateSave { name: string; capacity: number; managerName: string; teamId: number }

@Injectable({ providedIn: 'root' })
export class SaveService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private key(name: string) { return `${this.auth.session()?.id || 'guest'}:${name}`; }
  remember(access: Access): void { writeLocal(this.key(`manager-save-${access.room.id}`), access); writeLocal(this.key('manager-last-save'), access.room.id); }
  access(id: string): Access | null { return readLocal<Access>(this.key(`manager-save-${id}`)) || (!this.auth.session() ? readLocal<Access>(`manager-save-${id}`) : null); }
  lastSave(): string | null { return readLocal<string>(this.key('manager-last-save')) || (!this.auth.session() ? readLocal<string>('manager-last-save') : null); }
  private accountHeaders(): Record<string,string> { const token=this.auth.session()?.accessToken; return token ? { Authorization: `Bearer ${token}` } : {}; }
  mine() { return this.http.get<Access[]>('/api/saves/mine', { headers: this.accountHeaders() }); }
  available() {
    if(this.auth.session())return this.mine().pipe(tap(items=>{
      const valid=new Set(items.map(item=>item.room.id)),prefix=this.key('manager-save-');
      try{for(const key of Object.keys(localStorage))if(key.startsWith(prefix)&&!valid.has(key.slice(prefix.length)))this.forget(key.slice(prefix.length));}catch{}
      items.forEach(item=>this.storeAccess(item));
    }));
    let keys:string[]=[];try{keys=Object.keys(localStorage);}catch{}
    const ids=[...new Set(keys.filter(key=>key.startsWith('guest:manager-save-')||key.startsWith('manager-save-')).map(key=>key.replace(/^(guest:)?manager-save-/,'')))];
    if(!ids.length)return of<Access[]>([]);
    return forkJoin(ids.map(id=>{
      const access=this.access(id);if(!access?.room||!access.accessToken)return of(null);
      return this.http.get<Room>(`/api/saves/${id}/summary`,{headers:this.headers(id)}).pipe(map(room=>({...access,room})),tap(item=>this.storeAccess(item)),catchError(error=>{
        if([401,403,404,410].includes(error.status)){this.forget(id);return of(null);}return throwError(()=>error);
      }));
    })).pipe(map(items=>items.filter((item):item is Access=>item!==null)));
  }
  private storeAccess(access:Access) { writeLocal(this.key(`manager-save-${access.room.id}`),access); }
  forget(id:string):void {
    const keys=[this.key(`manager-save-${id}`)];
    if(this.lastSave()===id)keys.push(this.key('manager-last-save'));
    if(!this.auth.session()) {keys.push(`manager-save-${id}`);if(readLocal<string>('manager-last-save')===id)keys.push('manager-last-save');}
    try{for(const key of keys)localStorage.removeItem(key);}catch{}
  }
  delete(id:string) {return this.http.delete<void>(`/api/saves/${id}`,{headers:this.headers(id)}).pipe(tap(()=>this.forget(id)));}
  private headers(id: string) { return { Authorization: `Bearer ${this.access(id)?.accessToken || ''}` }; }
  create(input: CreateSave) { return this.http.post<Access>('/api/saves', input, { headers: this.accountHeaders() }).pipe(tap(access => this.remember(access))); }
  invite(code: string) { return this.http.get<Room>(`/api/saves/invites/${encodeURIComponent(code.trim().toUpperCase())}`); }
  join(code: string, managerName: string, teamId: number) {
    return this.http.post<Access>(`/api/saves/invites/${encodeURIComponent(code)}/join`, { managerName, teamId }, { headers: this.accountHeaders() }).pipe(tap(access => this.remember(access)));
  }
  room(id: string) { return this.http.get<Room>(`/api/saves/${id}`, { headers: this.headers(id) }); }
  ready(id: string, ready: boolean) { return this.http.put<Room>(`/api/saves/${id}/ready`, { ready }, { headers: this.headers(id) }); }
}
