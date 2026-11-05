import { Component, ElementRef, input, output, viewChild } from '@angular/core';
import { MatchPanelId, MatchPanelLayout } from '../../utils/match-panel-layout';
import { GameIcon } from '../game-icon/game-icon';
import { MatchAudioControls } from '../match-audio-controls/match-audio-controls';
import { MatchSpeedControls } from '../match-speed-controls/match-speed-controls';

@Component({selector:'app-match-options',imports:[GameIcon,MatchAudioControls,MatchSpeedControls],templateUrl:'./match-options.html',styleUrl:'./match-options.css'})
export class MatchOptions {
 readonly layout=input.required<MatchPanelLayout>();
 readonly percent=input(100);readonly allowed=input(false);readonly local=input(false);readonly busy=input(false);
 readonly panelToggled=output<MatchPanelId>();readonly speedChanged=output<number>();
 private readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
 open():void{this.dialog().nativeElement.showModal();}
 close():void{this.dialog().nativeElement.close();}
 backdrop(event:MouseEvent):void{if(event.target===this.dialog().nativeElement)this.close();}
}
