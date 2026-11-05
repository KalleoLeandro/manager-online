import { Directive, ElementRef, HostListener, forwardRef, inject, input } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';

/** Monetary amounts in this game are stored as whole reais, including salaries and bonuses. */
@Directive({
 selector:'input[appMoneyInput]',
 host:{type:'text',inputmode:'numeric',autocomplete:'off'},
 providers:[
  {provide:NG_VALUE_ACCESSOR,useExisting:forwardRef(()=>MoneyInput),multi:true},
  {provide:NG_VALIDATORS,useExisting:forwardRef(()=>MoneyInput),multi:true}
 ]
})
export class MoneyInput implements ControlValueAccessor, Validator {
 readonly moneyMin=input(0);readonly moneyMax=input(1_000_000_000_000);
 private readonly element=inject<ElementRef<HTMLInputElement>>(ElementRef);
 private readonly formatter=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL',maximumFractionDigits:0});
 private change:(value:number|null)=>void=()=>{};
 private touched:()=>void=()=>{};
 writeValue(value:number|null):void {this.element.nativeElement.value=value==null?'':this.formatter.format(value);}
 registerOnChange(fn:(value:number|null)=>void):void {this.change=fn;}
 registerOnTouched(fn:()=>void):void {this.touched=fn;}
 setDisabledState(disabled:boolean):void {this.element.nativeElement.disabled=disabled;}
 validate(control:AbstractControl):ValidationErrors|null {
  const value=control.value;if(value==null)return null;
  if(!Number.isSafeInteger(value))return {money:true};
  if(value<this.moneyMin())return {min:{min:this.moneyMin(),actual:value}};
  return value>this.moneyMax()?{max:{max:this.moneyMax(),actual:value}}:null;
 }
 @HostListener('input')
 edit():void {
  const element=this.element.nativeElement;
  const cursor=element.selectionStart??element.value.length;
  const digitsBefore=element.value.slice(0,cursor).replace(/\D/g,'').length;
  // Accept a pasted Brazilian currency value, ignoring its cents (the game uses whole reais).
  const digits=element.value.split(',')[0].replace(/\D/g,'');
  const value=digits?Number(digits):null;
  this.writeValue(value);
  const text=element.value;let seen=0,position=text.length;
  for(let index=0;index<text.length;index++)if(/\d/.test(text[index])&&++seen>=digitsBefore){position=index+1;break;}
  element.setSelectionRange(position,position);
  this.change(value);
 }
 @HostListener('blur') blur():void {this.touched();}
}
