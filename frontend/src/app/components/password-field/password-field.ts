import { Component, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'app-password-field',
  imports: [ReactiveFormsModule],
  template: `
    <div class="field">
      <label [for]="fieldId()">{{ label() }}</label>
      <div class="password-wrap">
        <input [id]="fieldId()" [name]="fieldId()" [type]="visible() ? 'text' : 'password'"
          [formControl]="control()" autocomplete="new-password" minlength="8" maxlength="128"
          [placeholder]="placeholder()" [attr.aria-describedby]="hint() ? fieldId() + '-hint' : null" required>
        <button class="reveal" type="button" (click)="visible.set(!visible())"
          [attr.aria-pressed]="visible()" [attr.aria-label]="(visible() ? 'Ocultar ' : 'Mostrar ') + label()">
          {{ visible() ? 'Ocultar' : 'Mostrar' }}
        </button>
      </div>
      @if (hint()) { <small [id]="fieldId() + '-hint'">{{ hint() }}</small> }
    </div>`
})
export class PasswordField {
  readonly fieldId = input.required<string>();
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly hint = input('');
  readonly control = input.required<FormControl<string>>();
  readonly visible = signal(false);
}
