import { Component, computed, effect, input, signal } from '@angular/core';
@Component({selector:'app-stadium-background',templateUrl:'./stadium-background.html',styleUrl:'./stadium-background.css'})
export class StadiumBackground {
 readonly teamId=input<number|null>(null);readonly dark=input(false);readonly loaded=signal(false);readonly failed=signal(false);
 readonly url=computed(()=>{const id=this.teamId();return id!=null&&Number.isSafeInteger(id)&&id>0?'/api/teams/'+id+'/stadium':null;});
 constructor(){effect(()=>{this.teamId();this.loaded.set(false);this.failed.set(false);});}
}
