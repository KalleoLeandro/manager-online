import {Component,ElementRef,HostListener,inject,input,output,signal} from '@angular/core';
export interface ExchangePlayer {id:number;name:string;position:string;fitness:number;rating?:number|null;injured?:boolean;sentOff?:boolean}
@Component({selector:'app-player-exchange',templateUrl:'./player-exchange.html',styleUrl:'./player-exchange.css'})
export class PlayerExchange {
 readonly field=input.required<ExchangePlayer[]>();readonly bench=input.required<ExchangePlayer[]>();readonly locked=input(false);readonly benchOnly=input(false);
 readonly exchanged=output<{first:number;second:number}>();readonly selected=signal(0);readonly message=signal('');
 readonly drag=signal<{id:number;name:string;x:number;y:number;startX:number;startY:number;pointer:number;moving:boolean}|null>(null);
 private readonly host=inject(ElementRef<HTMLElement>);private suppress=false;
 start(event:PointerEvent,p:ExchangePlayer){if(this.locked())return;event.preventDefault();this.drag.set({id:p.id,name:p.name,x:event.clientX,y:event.clientY,startX:event.clientX,startY:event.clientY,pointer:event.pointerId,moving:false});}
 @HostListener('document:pointermove',['$event']) move(event:PointerEvent){const d=this.drag();if(!d||d.pointer!==event.pointerId)return;this.drag.set({...d,x:event.clientX,y:event.clientY,moving:d.moving||Math.hypot(event.clientX-d.startX,event.clientY-d.startY)>8});}
 @HostListener('document:pointerup',['$event']) end(event:PointerEvent){const d=this.drag();if(!d||d.pointer!==event.pointerId)return;this.drag.set(null);if(!d.moving)return;this.suppress=true;setTimeout(()=>this.suppress=false,0);const target=document.elementsFromPoint(event.clientX,event.clientY).map(e=>e.closest<HTMLElement>('[data-exchange-player],[data-player]')).find(e=>e&&(this.host.nativeElement.closest('app-match-coach-controls,app-tactical-board')||this.host.nativeElement).contains(e)&&Number(e.dataset['exchangePlayer']??e.dataset['player'])!==d.id);if(target)this.exchange(d.id,Number(target.dataset['exchangePlayer']??target.dataset['player']));}
 @HostListener('document:pointercancel') @HostListener('window:blur') cancel(){this.drag.set(null);}
 click(id:number){if(this.locked())return;if(this.suppress){this.suppress=false;return;}if(!this.selected()){this.selected.set(id);return;}if(this.selected()===id){this.selected.set(0);return;}this.exchange(this.selected(),id);}
 private exchange(first:number,second:number){this.selected.set(0);if(this.locked()||first===second)return;if(![...this.field(),...this.bench()].some(p=>p.id===first)||![...this.field(),...this.bench()].some(p=>p.id===second))return;if(!this.field().some(p=>p.id===first||p.id===second)){this.message.set('Escolha pelo menos um jogador em campo.');return;}this.message.set('');this.exchanged.emit({first,second});}
}
