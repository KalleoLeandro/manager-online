import { Component, effect, input, signal } from '@angular/core';

@Component({selector:'app-manager-avatar',template:`@if(photoUrl()&&!failed()){<img [src]="photoUrl()" alt="Foto do treinador" (error)="failed.set(true)">}@else{<svg viewBox="0 0 48 48" role="img" aria-label="Foto padrão do treinador"><circle cx="24" cy="17" r="9"/><path d="M7 44v-5a17 17 0 0 1 34 0v5Z"/></svg>}`,styles:`:host{display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;width:var(--avatar-size,36px);height:var(--avatar-size,36px);border-radius:50%;overflow:hidden;background:var(--accent-wash,#e4e7eb);color:var(--accent,#64748b)}img{width:100%;height:100%;object-fit:cover}svg{width:80%;height:80%;fill:currentColor}`})
export class ManagerAvatar {
  readonly photoUrl=input<string|null|undefined>(null);
  readonly failed=signal(false);
  constructor(){effect(()=>{this.photoUrl();this.failed.set(false);});}
}
