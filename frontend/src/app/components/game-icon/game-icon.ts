import { Component, computed, input } from '@angular/core';

const paths:Record<string,string[]>={
 chart:['M4 3v18h17','M8 16v-4M13 16V7M18 16V4'],
 layout:['M3 4h18v16H3z','M8 4v16M16 4v16'],
 volume:['M11 4 6 8H2v8h4l5 4z','M15 8a6 6 0 0 1 0 8M18 5a10 10 0 0 1 0 14'],
 muted:['M11 4 6 8H2v8h4l5 4z','m16 9 6 6m0-6-6 6'],
 whistle:['M10 6H7a6 6 0 1 0 6 6v-1l8-3V4l-8 2v4h-3V6Z','M9 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z','M5 18l-1 3M18 1v1M23 2l-1 1'],
 messages:['M3 5h18v14H3z','m3 6 9 7 9-7'],
 overview:['M8 3 3 5l-2 6 4 2 1-3v11h12V10l1 3 4-2-2-6-5-2','M8 3a4 4 0 0 0 8 0'],
 transfers:['M3 7h17l-4-4M21 17H4l4 4','M20 7l-4 4M4 17l4-4'],
 details:['m12 2 8 3v7c0 5-8 10-8 10S4 17 4 12V5z','m8 11 3 3 5-6'],
 stadium:['M3 10 12 5l9 5v10H3z','M3 10l9 5 9-5M7 20v-7M17 20v-7M12 15v5M5 5V2M19 5V2'],
 facilities:['M3 10 12 5l9 5v10H3z','M3 10l9 5 9-5M7 20v-7M17 20v-7'],
 career:['M8 7V4h8v3M3 7h18v14H3z','M3 12c6 3 12 3 18 0M10 13v3h4v-3'],
 options:['M4 6h16M4 12h16M4 18h16','M8 3v6M16 9v6M9 15v6'],
 arrow:['M4 12h16m-6-6 6 6-6 6'],
 calendar:['M4 5h16v16H4z','M8 3v4M16 3v4M4 10h16M8 14h2M14 14h2M8 17h2'],
 trophy:['M7 3h10v6a5 5 0 0 1-10 0z','M7 5H3v3c0 3 2 4 5 4M17 5h4v3c0 3-2 4-5 4M12 14v7M8 21h8'],
};
@Component({selector:'app-game-icon',template:`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">@for(path of selected();track $index){<path [attr.d]="path"/>}</svg>`,styles:`:host{display:inline-flex;width:1.25em;height:1.25em;flex-shrink:0;align-items:center;justify-content:center}svg{width:100%;height:100%;overflow:visible}`})
export class GameIcon {readonly name=input('arrow');readonly selected=computed(()=>paths[this.name()]||paths['details']);}
