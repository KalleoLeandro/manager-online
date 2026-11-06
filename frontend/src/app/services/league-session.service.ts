import {Injectable,signal} from '@angular/core';
@Injectable({providedIn:'root'})
export class LeagueSessionService {
 readonly notice=signal('');private active='';private last='';
 constructor(){try{const saved=JSON.parse(sessionStorage.getItem('active-league')||'null');if(saved?.id&&saved?.path){this.active=saved.id;this.last=saved.path;}}catch{}}
 resolve(path:string):string {
  const match=/^(?:team|lobby|match|match-tactics)\/([^/]+)(?:\/.*)?$/.exec(path);
  if(this.active&&match?.[1]!==this.active){this.notice.set('Para voltar às ligas, use Sair da liga.');return this.last;}
  if(match){this.active=match[1];this.last=path;try{sessionStorage.setItem('active-league',JSON.stringify({id:this.active,path}));}catch{}}
  return path;
 }
 clear(){this.active='';this.last='';this.notice.set('');try{sessionStorage.removeItem('active-league');}catch{}}
 logout(){this.clear();location.hash='leagues';}
}
