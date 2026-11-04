import { Component, inject, signal } from '@angular/core';
import { Registration } from '../../components/registration/registration';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { AccessFlowService } from '../../services/access-flow.service';
import { finalize, switchMap } from 'rxjs';

@Component({ selector: 'app-login', imports: [Registration, ReactiveFormsModule], templateUrl: './login.html' })
export class Login {
  private readonly auth=inject(AuthService);
  private readonly flow=inject(AccessFlowService);
  private readonly builder=inject(FormBuilder).nonNullable;
  readonly registering=signal(false);
  readonly loading=signal(false);
  readonly error=signal('');
  readonly form=this.builder.group({ email:['',[Validators.required,Validators.email]], password:['',Validators.required] });
  submit(): void {
    if(this.loading()) return;
    this.error.set(''); this.form.markAllAsTouched();
    if(this.form.invalid) { this.error.set('Informe seu e-mail e senha.'); return; }
    this.loading.set(true);
    const {email,password}=this.form.getRawValue();
    this.auth.login(email,password).pipe(switchMap(()=>this.flow.afterLogin()),finalize(()=>this.loading.set(false))).subscribe({
      next:()=>this.form.reset(), error:e=>this.error.set(e.error?.message || 'Não foi possível entrar. Verifique a conexão e tente novamente.')
    });
  }
}
