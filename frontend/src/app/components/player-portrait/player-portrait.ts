import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { PlayerPortraitsService } from '../../services/player-portraits.service';
import { PortraitFrame, portraitFrame } from '../../utils/portrait-frame';

@Component({selector:'app-player-portrait',template:`<img [src]="failed() ? fallback : source()" [style.width.%]="frame()?.width" [style.left.%]="frame()?.left" [style.top.%]="frame()?.top" [style.height]="frame() ? 'auto' : null" alt="" loading="lazy" (load)="frameImage($event)" (error)="failed.set(true); frame.set(null)">`,styles:`:host{position:relative;display:inline-flex;flex-shrink:0;width:31px;height:36px;border-radius:4px;overflow:hidden;background:var(--page,#26342a)}img{position:absolute;left:0;top:0;display:block;width:100%;height:100%;object-fit:contain}`})
export class PlayerPortrait {
  private readonly portraits=inject(PlayerPortraitsService);
  readonly playerId=input.required<number>();
  readonly failed=signal(false);
  readonly frame=signal<PortraitFrame|null>(null);
  readonly fallback=this.portraits.fallback;
  readonly source=computed(()=>this.portraits.source(this.playerId()));
  constructor(){effect(()=>{this.source();this.failed.set(false);this.frame.set(null);});}
  frameImage(event: Event): void {
    this.frame.set(null);
    if(this.failed()) return;
    const image=event.target as HTMLImageElement;
    try {
      const canvas=document.createElement('canvas');
      const scale=Math.min(1,256/Math.max(image.naturalWidth,image.naturalHeight));
      canvas.width=Math.round(image.naturalWidth*scale);canvas.height=Math.round(image.naturalHeight*scale);
      const context=canvas.getContext('2d',{willReadFrequently:true});
      if(!context) return;
      context.drawImage(image,0,0,canvas.width,canvas.height);
      this.frame.set(portraitFrame(context.getImageData(0,0,canvas.width,canvas.height)));
    } catch {
      // Keep the original framing if pixel access is unavailable.
    }
  }
}
