import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TacticalPlan } from '../../utils/tactical-plan';
type Slider='passingLength'|'passingRisk'|'tempo'|'width'|'defensiveLine'|'pressing';
@Component({selector:'app-team-strategy',imports:[FormsModule],templateUrl:'./team-strategy.html',styleUrl:'./team-strategy.css'})
export class TeamStrategy {
 readonly plan=input.required<TacticalPlan>();readonly locked=input(false);readonly planChange=output<TacticalPlan>();
 readonly sliders:{key:Slider;label:string;low:string;high:string;hint:string}[]=[{key:'passingLength',label:'Comprimento do passe',low:'Curto',high:'Longo',hint:'Passes longos aceleram a progressão e aumentam a chance de erro.'},{key:'passingRisk',label:'Risco dos passes',low:'Seguro',high:'Arriscado',hint:'Maior risco favorece a criação e facilita perdas de posse.'},{key:'tempo',label:'Ritmo de jogo',low:'Cadenciado',high:'Intenso',hint:'Ritmo alto favorece a criação e aumenta o desgaste.'},{key:'width',label:'Amplitude',low:'Pelo centro',high:'Pelas pontas',hint:'A amplitude muda o apoio ao ataque e o controle no meio.'},{key:'defensiveLine',label:'Linha defensiva',low:'Recuada',high:'Avançada',hint:'Avançar a linha aproxima a pressão e abre espaço nas costas.'},{key:'pressing',label:'Pressão',low:'Esperar',high:'Pressionar',hint:'Pressão alta melhora a recuperação e consome mais energia.'}];
 change(key:Slider,value:number):void{if(!this.locked())this.planChange.emit({...this.plan(),[key]:+value});}
 toggle(key:'counterAttack'|'offsideTrap',value:boolean):void{if(!this.locked())this.planChange.emit({...this.plan(),[key]:value});}
}
