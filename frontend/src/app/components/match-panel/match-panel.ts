import { Component, input, output } from '@angular/core';
import { GameIcon } from '../game-icon/game-icon';

@Component({selector:'app-match-panel',imports:[GameIcon],templateUrl:'./match-panel.html',styleUrl:'./match-panel.css'})
export class MatchPanel {
 readonly panelId=input.required<string>();readonly title=input.required<string>();readonly icon=input('overview');readonly subtitle=input('');readonly expanded=input(true);readonly required=input(false);
 readonly expandedChange=output<boolean>();readonly hiddenPanel=output<void>();
}
