import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { readLocal, writeLocal } from '../utils/local-storage';

export interface Session { id: string; name: string; accessToken: string; photoUrl?: string|null; firstName?:string; lastName?:string|null }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  session(): Session | null { return readLocal<Session>('manager-session'); }
  login(email: string, password: string) {
    return this.http.post<Session>('/api/auth/login', { email: email.trim(), password }).pipe(tap(session => {
      writeLocal('manager-session',session); writeLocal('manager-name',session.name);
    }));
  }
}
