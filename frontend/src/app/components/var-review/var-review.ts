import { AfterViewInit, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { VarReview as Review } from '../../services/matchday.service';

@Component({selector:'app-var-review',templateUrl:'./var-review.html',styleUrl:'./var-review.css'})
export class VarReview {
 readonly review=input.required<Review>();readonly refresh=output<void>();
 private readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
 constructor(){effect(onCleanup=>{const r=this.review();const timer=setTimeout(()=>this.refresh.emit(),Math.max(150,r.remainingMillis+100));onCleanup(()=>clearTimeout(timer));});}
 ngAfterViewInit():void{this.dialog().nativeElement.showModal();}
}
