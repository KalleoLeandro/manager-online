import { Component, input } from '@angular/core';
import { Trophy } from '../../utils/trophies';

@Component({
 selector:'app-trophy-image',
 template:'<img [src]="\'/api/trophies/\'+trophy().id+\'/image\'" [alt]="trophy().name" loading="lazy" width="80" height="100">',
 styles:':host{display:inline-flex;align-items:center;justify-content:center;flex:none;width:80px;height:100px}img{max-width:100%;max-height:100%;object-fit:contain;filter:drop-shadow(0 4px 3px #243c3020)}'
})
export class TrophyImage {readonly trophy=input.required<Trophy>();}
