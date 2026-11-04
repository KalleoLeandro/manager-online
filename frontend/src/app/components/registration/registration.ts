import { ManagerAvatar } from '../manager-avatar/manager-avatar';
import { prepareManagerPhoto } from '../../utils/manager-photo';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, switchMap } from 'rxjs';
import { PasswordField } from '../password-field/password-field';
import { RegistrationService } from '../../services/registration.service';
import { matchingPasswords } from '../../utils/form-validators';
import { AuthService } from '../../services/auth.service';
import { AccessFlowService } from '../../services/access-flow.service';

@Component({ selector: 'app-registration', imports: [ReactiveFormsModule, PasswordField,ManagerAvatar], templateUrl: './registration.html',styleUrl:'./registration.css' })
export class Registration {
  private readonly api = inject(RegistrationService);
  private readonly builder = inject(FormBuilder).nonNullable;
  private readonly auth=inject(AuthService);
  private readonly flow=inject(AccessFlowService);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly form = this.builder.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(40), Validators.pattern(/\S(?:[\s\S]*\S)?/)]],
    lastName: ['', [Validators.required, Validators.maxLength(40), Validators.pattern(/\S(?:[\s\S]*\S)?/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    passwordConfirmation: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]]
  }, { validators: matchingPasswords });

  readonly photoData=signal<string|null>(null);
  readonly preparingPhoto=signal(false);
  async selectPhoto(event:Event):Promise<void>{
    const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;
    this.error.set('');this.preparingPhoto.set(true);
    try{this.photoData.set(await prepareManagerPhoto(file));}catch(error){this.photoData.set(null);this.error.set(error instanceof Error?error.message:'Não foi possível preparar a foto.');(event.target as HTMLInputElement).value='';}finally{this.preparingPhoto.set(false);}
  }
  submit(): void {
    if (this.loading()||this.preparingPhoto()) return;
    this.error.set('');
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.error.set(this.form.hasError('mismatch') ? 'As senhas precisam ser iguais.' : 'Confira os campos. Informe nome e sobrenome, e-mail e uma senha com pelo menos 8 caracteres.');
      return;
    }
    const values = this.form.getRawValue();
    if((values.name.trim()+' '+values.lastName.trim()).length>40){this.error.set('O nome completo deve ter até 40 caracteres.');return;}
    this.loading.set(true);
    this.api.register({ ...values,photoData:this.photoData(), name: values.name.trim(),lastName:values.lastName.trim(), email: values.email.trim() })
      .pipe(switchMap(() => this.auth.login(values.email,values.password)),switchMap(() => this.flow.afterLogin()),finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => this.form.reset(),
        error: (response: HttpErrorResponse) => this.error.set(
          response.status === 0 ? 'Não foi possível conectar ao servidor. Tente novamente.' :
          response.error?.message || 'Não foi possível criar a conta. Tente novamente.')
      });
  }
}
