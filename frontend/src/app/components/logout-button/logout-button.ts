import {Component,inject,signal} from '@angular/core';
import {finalize} from 'rxjs';
import {AuthService} from '../../services/auth.service';
@Component({selector:'app-logout-button',template:`@if(auth.session()){<button type="button" [disabled]="busy()" (click)="logout()">{{busy()?'Encerrando…':'Logout'}}</button>}@else{<a href="#login">Entrar</a>}@if(error()){<span role="alert">{{error()}}</span>}`,styles:`:host{display:inline-flex;position:relative;align-items:center}button,a{display:inline-flex;align-items:center;justify-content:center;min-height:40px;padding:10px 16px;border:1px solid #6f574f;border-radius:8px;color:#efb6a6;background:#251d19;font:600 12px Arial,sans-serif;text-decoration:none;cursor:pointer}button:disabled{opacity:.6;cursor:wait}span{position:absolute;right:0;top:100%;width:260px;max-width:80vw;padding:12px;border:1px solid #b57b65;border-radius:7px;background:#30231d;color:#f4c3b3;z-index:60;font-size:11px;line-height:1.5}`})
export class LogoutButton {
 readonly auth=inject(AuthService);readonly busy=signal(false);readonly error=signal('');
 logout(){if(this.busy())return;this.busy.set(true);this.error.set('');this.auth.logout().pipe(finalize(()=>this.busy.set(false))).subscribe({error:()=>this.error.set('Não foi possível encerrar a sessão no servidor. Tente novamente.')});}
}
