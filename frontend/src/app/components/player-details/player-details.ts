import { PlayerTechnicalDetails } from '../player-technical/player-technical';
import { AttributeScore } from '../attribute-score/attribute-score';
import { PlayerCondition } from '../../utils/player-attributes';
import { AfterViewInit, Component, ElementRef, computed, input, output, viewChild } from '@angular/core';
import { Player } from '../../services/catalog.service';
import { PlayerPortrait } from '../player-portrait/player-portrait';

export type PlayerProfile = Omit<Player, 'nationality'> & { nationality?: string | null; fitness?: number; condition?: PlayerCondition };

@Component({selector:'app-player-details',imports:[PlayerPortrait,PlayerTechnicalDetails,AttributeScore],templateUrl:'./player-details.html',styleUrl:'./player-details.css'})
export class PlayerDetails implements AfterViewInit {
  readonly player=input.required<PlayerProfile>();
  readonly squad=input.required<PlayerProfile[]>();
  readonly teamName=input('');
  readonly starting=input<boolean|null>(null);
  readonly showComparison=input(true);
  readonly closed=output<void>();
  private readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly positionName=computed(()=>({GOL:'Goleiro',DEF:'Defensor',MEI:'Meio-campista',ATA:'Atacante'}[this.player().position]||this.player().position));
  readonly average=computed(()=>this.squad().length?Math.round(this.squad().reduce((sum,p)=>sum+p.ability,0)/this.squad().length):this.player().ability);
  readonly difference=computed(()=>this.player().ability-this.average());
  readonly peers=computed(()=>this.squad().filter(p=>p.position===this.player().position));
  readonly rank=computed(()=>1+this.peers().filter(p=>p.ability>this.player().ability).length);
  ngAfterViewInit():void {this.dialog().nativeElement.showModal();}
  close():void {this.dialog().nativeElement.close();}
  backdrop(event:MouseEvent):void {
    const dialog=this.dialog().nativeElement,rect=dialog.getBoundingClientRect();
    if(event.target===dialog&&(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom))this.close();
  }
}
