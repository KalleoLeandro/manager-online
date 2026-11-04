import { AfterViewInit, Component, ElementRef, input, output, viewChild } from '@angular/core';

@Component({selector:'app-league-delete-confirmation',template:`<dialog #dialog aria-labelledby="delete-title" (cancel)="onCancel($event)"><h2 id="delete-title">Excluir esta liga?</h2><strong>{{name()}}</strong><p>Todos os participantes perderão o acesso. O elenco, as escalações e os demais dados desta liga serão apagados permanentemente.</p><p>Essa ação não pode ser desfeita.</p><div><button type="button" [disabled]="busy()" (click)="cancel.emit()">Cancelar</button><button type="button" class="confirm-delete" [disabled]="busy()" (click)="confirm.emit()">{{busy()?'Excluindo…':'Excluir permanentemente'}}</button></div></dialog>`,styles:`dialog{max-width:460px;width:calc(100% - 64px);padding:28px;border:1px solid #536052;border-radius:12px;background:#18221b;color:#eaf1e9}dialog::backdrop{background:#000a}h2{margin-top:0;font-size:24px}strong{overflow-wrap:anywhere}p{font-size:13px;line-height:1.7;color:#b6c6b7}div{display:flex;justify-content:flex-end;gap:12px;flex-wrap:wrap;margin-top:24px}button{border:1px solid #526050;background:transparent;color:#eaf1e9;border-radius:6px;padding:12px;font:inherit;font-size:12px;cursor:pointer}.confirm-delete{background:#bd3838;border-color:#bd3838}button:disabled{opacity:.5;cursor:wait}`})
export class LeagueDeleteConfirmation implements AfterViewInit {
  readonly name=input.required<string>();readonly busy=input(false);
  readonly cancel=output<void>();readonly confirm=output<void>();
  readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  ngAfterViewInit(){this.dialog().nativeElement.showModal();}
  onCancel(event:Event){event.preventDefault();if(!this.busy())this.cancel.emit();}
}
