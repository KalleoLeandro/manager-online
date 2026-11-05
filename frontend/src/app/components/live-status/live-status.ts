import { Component, input } from '@angular/core';

@Component({ selector: 'app-live-status', template: `
  <span class="live" [class.online]="status() === 'connected'" role="status"><i></i>{{
    status() === 'connected' ? 'Conectado' : status() === 'reconnecting' ? 'Reconectando…' : status() === 'error' ? 'Conexão indisponível' : 'Conectando…'
  }}</span>`, styles: `
    .live{display:inline-flex;align-items:center;gap:7px;font-size:10px;color:#d6ba7e;white-space:nowrap}.live i{width:6px;height:6px;border-radius:50%;background:currentColor}.online{color:var(--live-color,var(--green))}
  ` })
export class LiveStatus { readonly status = input.required<string>(); }
