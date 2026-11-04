import { Component } from '@angular/core';

@Component({selector:'app-league-retention-notice',template:`<aside role="note"><strong>Prazo de armazenamento</strong><p>Ligas sem atividade por mais de <b>60 dias</b> são apagadas automaticamente do servidor.</p><small>Entrar na liga, participar ou salvar alterações renova o prazo. Apenas listar ligas ou consultar convites não conta como atividade.</small></aside>`,styles:`:host{display:block;margin:20px 0}aside{padding:17px 20px;border:1px solid #53603c;border-radius:10px;background:#26341d;color:#e3ebdc}strong{font-size:12px}p{font-size:12px;margin:8px 0;line-height:1.6}small{display:block;font-size:11px;color:#bccbb0;line-height:1.7}`})
export class LeagueRetentionNotice {}
